from datetime import date, timedelta, datetime, time
from functools import partial
import geopandas as gpd
import gtfs_kit as gk
from os import path
import pandas as pd
from parse import parse
import re
from shapely.errors import ShapelyDeprecationWarning
from shapely.geometry import Point, LineString
from sqlalchemy import create_engine
import warnings


from modules.config import Config
from modules.gis_processing import GisProcessor


def as_date(d: str) -> date:
    """Convert string representation to date object"""
    return datetime.strptime(d, "%Y%m%d").date()


class HslBuses(GisProcessor):
    """Process HSL bus lines."""

    def __init__(
        self, cfg: Config, validate_gtfs: bool = True, week_of_transit: int = 2
    ):
        self._cfg = cfg

        # check that Helsinki city area file is available
        if not path.exists(self._cfg.local_file("hki")):
            raise FileNotFoundError("Helsinki city area polygon not found")

        # TODO: how to obtain this string automatically?
        file_name = cfg.local_file("hsl")
        self._feed = self._read_feed_data(file_name)

        if validate_gtfs:
            self._feed.validate()

        self._transit_week = self.feed().get_week(week_of_transit, as_date_obj=False)

        self._process_result_lines = None
        self._process_result_polygons = None

    def _read_feed_data(self, file_name) -> gk.Feed:
        """Read feed data from zip file"""
        feed = gk.read_feed(file_name, dist_units="km")
        return feed

    def feed(self) -> gk.Feed:
        return self._feed

    def transit_week(self) -> list[str]:
        """Return date strings of transit week"""
        return self._transit_week

    def transit_day(self, day_sel: int = 1) -> str:
        """Return date of transit week.

        Default is date for tuesday.
        """
        if day_sel > 6:
            day_sel = day_sel % 7

        return self.transit_week()[day_sel]

    def _pick_service_ids(self, d_start: str, d_end: str) -> pd.Index:
        """Pick service_id:s that fall within provided range.

        Return index to calendar table."""
        result = self.feed().calendar
        ind = result.apply(
            lambda r: self._overlaps(d_start, d_end, r.start_date, r.end_date), axis=1
        )
        return ind

    def _overlaps(self, d1min: str, d1max: str, d2min: str, d2max: str) -> bool:
        """Check if date ranges overlap"""
        return min(as_date(d1max), as_date(d2max)) - max(
            as_date(d1min), as_date(d2min)
        ) >= timedelta(0)

    def _pick_service_ids_for_one_day(self, datestring: str) -> list[str]:
        """Return list of service ids of a given day.
        Does not respect calendar_dates -information."""
        result_ind = self._pick_service_ids(datestring, datestring)
        candidate = self.feed().calendar.copy()
        weekday = as_date(datestring).strftime("%A").lower()
        weekday_ind = candidate[weekday] == 1
        # candidate day must belong to time range AND have specific weekday mentioned in calendar
        serv_id_cand = candidate[result_ind & weekday_ind]
        return serv_id_cand["service_id"].values.tolist()

    def _trips_for_service_ids(self, service_ids: list[str]) -> pd.DataFrame:
        """Pick all trip_id:s belonging to a list of provided service_ids"""
        srv_ind = self.feed().trips["service_id"].isin(service_ids).values
        return self.feed().trips[srv_ind]

    def _rush_hours(self) -> list[tuple[str, time, time]]:
        """Construct rush hour times.

        Return list of (<descriptor string>, start time, end time)"""

        hours = []
        # rush hours are morning rush hours (13 ranges):
        # 6.00 - 7.00
        # 6.15 - 7.15
        # ...
        # 8.45 - 9.45
        # 9.00 - 10.00
        #
        # and evening rush hours (13 ranges):
        # 15.00 - 16.00
        # 15.15 - 16.15
        # ...
        # 17.45 - 18.45
        # 18.00 - 19.00
        for s in [6, 15]:
            start_times = []
            for h in range(4):
                for m in [0, 15, 30, 45]:
                    # construct list of tuples (hour_start, minute_start, hour_end, minute_end)
                    start_times.append((s + h, m, s + h + 1, m))
            # pick first 13 values, according to spec commented above
            start_times = start_times[0:13]
            hours += start_times

        hour_range = []
        for h_s, m_s, h_e, m_e in hours:
            hour_range.append(
                ("n_{:02d}{:02d}".format(h_s, m_s), time(h_s, m_s), time(h_e, m_e))
            )
        return hour_range

    def _compute_rush_hours(self, stops_and_trips: pd.DataFrame) -> pd.DataFrame:
        """Compute rush hours for given data frame."""
        rush_hours = self._rush_hours()

        # construct list of tuples(<descriptor>, <partial function>)
        # to help computing multiple columns to data frame
        pl = [(rh[0], partial(self._agg_rush_hour, rh[1], rh[2])) for rh in rush_hours]

        kwargs = {nm: lambda x: pf(x.departure_time) for nm, pf in pl}
        return stops_and_trips.assign(**kwargs)

    def _parse_time(self, t_candidate: str) -> dict:
        """Parse time. Support hour values larger than 23.
        If hour part is larger than 23, "next_day" boolean value is True"""
        format_string = "{:d}:{:d}:{:d}"
        h, m, s = parse(format_string, t_candidate)
        next_day = False
        if h > 23:
            h %= 24
            next_day = True
        time_str = "{:02d}:{:02d}:{:02d}".format(h, m, s)
        t = datetime.strptime(time_str, "%H:%M:%S").time()
        return {"time": t, "next_day": next_day}

    def _agg_rush_hour(self, t_start: time, t_end: time, t_candidate):
        """Check if time is between an interval. Return 1 if it is, 0 otherwise.

        Handle both pd.Series and single value input for t_candidate."""

        def is_between(candidate, low, high) -> bool:
            """Check if candidate is in range [low, high)"""
            return low <= candidate < high

        if isinstance(t_candidate, pd.Series):
            # Time in GTFS can be greater than 23.
            # Hours falling to next day are not treated here
            # due to it is extremely unlikely that day of operation
            # continues until morning rush hour.

            # TODO: handle previous day and current day for times past midnight.
            t_c = t_candidate.apply(lambda r: self._parse_time(r).get("time"))
            retval = 1 * t_c.apply(lambda r: is_between(r, t_start, t_end))
            return retval
        else:
            # TODO: handle previous day and current day for times past midnight.
            t_c = self._parse_time(t_candidate).get("time")
            if is_between(t_c, t_start, t_end):
                return 1
            else:
                return 0

    def _route_is_trunk(self, route_id: str) -> str:
        """Check if route is trunk route."""
        is_trunk_route = {
            r"^1500.*": "yes",
            r"^2200.*": "yes",
            r"^2510.*": "yes",
            r"^2550.*": "yes",
            r"^4560.*": "yes",
            r"^1018.*": "almost",
            r"^1039.*": "almost",
            r"^1040.*": "almost",
        }
        for pattern, result in is_trunk_route.items():
            if re.match(pattern, route_id):
                return result
        return "no"

    def _process_hsl_bus_lines(self) -> pd.DataFrame:
        """Process GTFS material."""
        service_ids_day = self._pick_service_ids_for_one_day(self.transit_day())
        trips_day = self._trips_for_service_ids(service_ids_day)
        stop_times_trip = self.feed().stop_times[
            self.feed().stop_times["trip_id"].isin(trips_day["trip_id"])
        ]
        stop_times_trip_uniq = stop_times_trip.drop_duplicates("trip_id")
        stops_trips = stop_times_trip_uniq.merge(trips_day, on="trip_id", how="left")

        # rush hour time columns added
        stops_trips = self._compute_rush_hours(stops_trips)

        shape_trips_max = (
            stops_trips.loc[
                :, ["shape_id"] + [x for x in stops_trips.columns if x.startswith("n_")]
            ]
            .groupby("shape_id")
            .sum()
            .max(axis=1)
        )
        stop_times_day = shape_trips_max.to_frame("rush_hour").reset_index()

        # pick weeks worth of service_ids
        w_start = self.transit_week()[0]
        w_end = self.transit_week()[-1]

        ind_service_ids_week = self._pick_service_ids(w_start, w_end)
        service_ids_week = (
            self.feed().calendar[ind_service_ids_week]["service_id"].values.tolist()
        )

        ind_trips_week = self.feed().trips["service_id"].isin(service_ids_week)
        trips_week = self.feed().trips[ind_trips_week]

        shps = self.feed().shapes.copy()

        with warnings.catch_warnings():
            warnings.filterwarnings("ignore", category=ShapelyDeprecationWarning)
            # form point objects from coordinate values
            shps["geometry"] = shps.apply(
                lambda r: Point(r.shape_pt_lon, r.shape_pt_lat), axis=1
            )

        # pick all line geometries (shapes) that are operated within a given week
        # sort by shape_id and shape point sequence, to ensure correct linestring formation
        shapes_week_ind = shps["shape_id"].isin(trips_week["shape_id"].tolist())
        shapes_week = shps[shapes_week_ind].sort_values(
            ["shape_id", "shape_pt_sequence"]
        )

        with warnings.catch_warnings():
            warnings.filterwarnings("ignore", category=ShapelyDeprecationWarning)
            # Create Linestring from points
            shapes_week_lines = gpd.GeoDataFrame(
                shapes_week.groupby(["shape_id"])["geometry"].apply(
                    lambda x: LineString(x.tolist())
                ),
                geometry="geometry",
            )

        shapes_week_lines = shapes_week_lines.merge(
            stop_times_day, on="shape_id", how="left"
        ).fillna(0)

        # merge shapes and routes
        trips_week = trips_week.drop_duplicates("shape_id")
        shapes_with_trips = shapes_week_lines.merge(
            trips_week, on="shape_id", how="left"
        )
        shapes_trips_routes = shapes_with_trips.merge(
            self.feed().routes, on="route_id", how="left"
        )
        shapes_trips_routes = shapes_trips_routes[~shapes_trips_routes.route_id.isna()]

        # Exclude route types
        # 0 - trams
        # 1 - subway
        # 4 - Suomenlinna ferries
        # 109 - trains

        # Description for route types:
        # https://developers.google.com/transit/gtfs/reference#routestxt
        # https://developers.google.com/transit/gtfs/reference/extended-route-types
        filtered_shapes_trips_routes = shapes_trips_routes[
            ~shapes_trips_routes.route_type.isin([0, 1, 4, 109])
        ]

        shapes_with_attributes = self._add_trunk_descriptor(
            filtered_shapes_trips_routes
        )

        # Pick columns and reproject to desired CRS
        shapes_with_attributes["fid"] = shapes_with_attributes.reset_index().index
        shapes_with_attributes = shapes_with_attributes.loc[
            :, ["fid", "route_id", "direction_id", "rush_hour", "trunk", "geometry"]
        ]
        shapes_with_attributes = shapes_with_attributes.set_crs("EPSG:4326").to_crs(
            self._cfg.crs()
        )

        # Only intersecting routes to Helsinki area are important
        # read Helsinki geographical region and reproject
        try:
            helsinki_region_polygon = gpd.read_file(
                filename=self._cfg.local_file("hki")
            ).to_crs(self._cfg.crs())
        except Exception as e:
            print("Area polygon file not found!")
            raise e

        target_routes = (
            shapes_with_attributes.sjoin(helsinki_region_polygon)
            .loc[:, shapes_with_attributes.columns.tolist()]
            .set_index("fid")
        )

        return target_routes

    def _add_trunk_descriptor(self, shapes: gpd.GeoDataFrame) -> gpd.GeoDataFrame:
        """Add trunk line descriptor column."""
        retval = shapes.copy()
        retval["trunk"] = retval.apply(
            lambda r: self._route_is_trunk(r.route_id), axis=1
        )
        return retval

    def process(self) -> None:
        # main part of processing is initiated here
        self._process_result_lines = self._process_hsl_bus_lines()

        # Buffering configuration
        buffers = self._cfg.buffer("hsl")
        if len(buffers) != 1:
            raise ValueError("Unkown number of buffer values")

        # buffer lines
        target_route_polys = self._process_result_lines.copy()
        target_route_polys["geometry"] = target_route_polys.buffer(buffers[0])

        # save to instance
        self._process_result_polygons = target_route_polys

    def persist_to_database(self) -> None:
        connection = create_engine(self._cfg.pg_conn_uri())

        # persist route lines to database
        self._process_result_lines.to_postgis(
            "bus_lines",
            connection,
            "public",
            if_exists="replace",
            index=True,
            index_label="fid",
        )

        # persist polygons to database
        self._process_result_polygons.to_postgis(
            "bus_line_polys",
            connection,
            "public",
            if_exists="replace",
            index=True,
            index_label="fid",
        )

    def save_to_file(self) -> None:
        """Save processing results to file(s).

        write computed bus lines and polygons to file."""
        # Bus line as debug material
        target_lines_file_name = self._cfg.target_file("hsl")
        self._process_result_lines.to_file(target_lines_file_name, driver="GPKG")
        # tormays GIS material
        target_buffer_file_name = self._cfg.target_buffer_file("hsl")
        self._process_result_polygons.to_file(target_buffer_file_name, driver="GPKG")
