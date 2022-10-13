from time import strftime
import pandas as pd
import requests
import geopandas as gpd
import re

from modules.config import Config
from modules.gis_processing import GisProcessor
from datetime import date, timedelta, datetime, time

import gtfs_kit as gk
import pyjq
from parse import parse

def current_week_dates(target_date : date = None) -> list[date]:
    """Obtain weeks worth of dates from monday to sunday.
    
    If target_date is not given, set target_date to today"""
    if target_date is None:
        target_date = date.today()
    monday = target_date - timedelta(days = target_date.weekday())
    retval = [ monday + timedelta(days = d) for d in range(0,7) ]

    return retval

def as_string(d : date) -> str:
    """Convert date to string representation"""
    return d.strftime("%Y%m%d")

def as_date(d : str) -> date:
    """Convert string representation to date object"""
    return datetime.strptime(d, "%Y%m%d").date()
class HslBuses(GisProcessor):
    def __init__(self, validate_gtfs : bool = True):
        cfg = Config()
        self._trunk_lines = self.get_trunk_lines()
        # TODO: how to obtain this string automatically?
        file_name = cfg.downloaded_file_name("hsl")
        self._feed = self._read_feed_data(file_name)
        if validate_gtfs:
            self._feed.validate()
        # Second week of schedule in transit information.
        self._transit_week = self.feed().get_week(2, as_date_obj=False)
        for t in self.transit_week():
            print(t)

    def _read_feed_data(self, file_name) -> gk.Feed:
        """Read feed data from zip file"""
        feed = gk.read_feed(file_name, dist_units='km')
        return feed

    def feed(self) -> gk.Feed:
        return self._feed

    def transit_week(self) -> list[date]:
        return self._transit_week

    def transit_day(self) -> date:
        """Return tuesday of transit week
        """
        return self.transit_week()[1]

    def pick_service_ids(self, d_start : str, d_end : str) -> pd.Index:
        """Pick service_id:s that fall within provided range."""
        result = self.feed().calendar
        ind = result.apply(lambda r: self.overlaps(d_start, d_end, r.start_date, r.end_date), axis=1)
        return ind
        #result[ind]['service_id'].tolist()

    def trunk_lines(self):
        return self._trunk_lines

    def overlaps(self, d1min : str, d1max : str, d2min : str, d2max : str) -> bool:
        """Check if date ranges overlap"""
        return min(as_date(d1max), as_date(d2max)) - max(as_date(d1min), as_date(d2min))  >= timedelta(0)

    def filter_trunk_lines(self) -> pd.DataFrame:
        """Filter trunk lines. Preserve only important trunk lines.
        """

        pass

    def get_trunk_lines(self) -> list[str]:
        """Obtain trunk lines"""
        addr = 'https://api.digitransit.fi/routing/v1/routers/hsl/index/graphql'
        data = {
            "query": "{ routes(transportModes:BUS) { gtfsId type }}"
            }

        try:
            resp = requests.post(addr, json=data)
            resp.raise_for_status()
        except requests.exceptions.HTTPError as err:
            raise SystemExit(err)

        r = resp.json()
        res = pyjq.all('.data.routes[] | select(.type == 702) | .gtfsId', r)
        res = [ s.replace("HSL:", "") for s in res ]
        return res

    def process(self):
        pass

    def persist_to_database(self):
        pass

    def save_to_file(self):
        pass

    def pick_service_ids_for_one_day(self, datestring : str) -> list[str]:
        """Return list of service ids of a given day.
        Currently does not respect calendar_dates -information."""
        result_ind = self.pick_service_ids(datestring, datestring)
        candidate = self.feed().calendar.copy()
        weekday = as_date(datestring).strftime("%A").lower()
        weekday_ind = (candidate[weekday] == 1)
        # one day must belong to time range AND have specific weekday mentioned in calendar
        serv_id_cand = candidate[result_ind & weekday_ind]
        return serv_id_cand["service_id"].values.tolist()

    def trips_for_service_ids(self, service_ids : list[str]) -> pd.DataFrame:
        """Pick all trip_id:s belonging to a list of provided service_ids"""
        srv_ind = self.feed().trips['service_id'].isin(service_ids).values
        return self.feed().trips[srv_ind]

    # def shapes_for_(self, trip_ids : list[str]) -> pd.DataFrame:
    #     #res = self.feed().shapes.apply(lambda r: r.shape_id in )
    #     pass

    def stoptimes_for_trip_ids(self, trip_ids : list[str]) -> pd.DataFrame:
        stp_ind = self.feed().stop_times['trip_id'].isin(trip_ids).values
        return self.feed().stop_times[stp_ind]

    def summarize_rush_hours(self, stoptimes):
        rush_hours = self._rush_hours()
        res = stoptimes.copy()
        for c_string, t_s, t_e in rush_hours:
            res[c_string] = stoptimes.apply(lambda r: datetime.strptime(r.arrival_time, "%H:%M:%S").time() >= t_s and datetime.strptime(r.arrival_time, "%H:%M:%S").time() < t_e, axis=1)
        return res

    def _rush_hours(self) -> list[tuple[str, time, time]]:
        """Compute rush hour times.
        
        Return list of (<descriptor>, start time, end time)"""
        hours = []
        for s in [6, 15]:
            start_times = []
            for h in range(4):
                for m in [0, 15, 30, 45]:
                    start_times.append((s+h, m, s+h+1, m))
            start_times = start_times[0:13]
            hours += start_times

        hour_range = []
        for h_s, m_s, h_e, m_e in hours:
            hour_range.append(("n_{:02d}{:02d}".format(h_s, m_s), time(h_s, m_s), time(h_e, m_e)))
        return hour_range

    def _parse_time(self, t_candidate : str) -> dict[time, bool]:
        """Parse time. Support hour values larger than 23.
        If hour part is larger than 23, "next_day" boolean value is True"""
        format_string = "{:d}:{:d}:{:d}"
        h,m,s = parse(format_string, t_candidate)
        next_day = False
        if h > 23:
            h -= 24
            next_day = True
        time_str = "{:02d}:{:02d}:{:02d}".format(h, m, s)
        t = datetime.strptime(time_str, "%H:%M:%S").time()
        return {
            "time": t,
            "next_day": next_day
            }

    def agg_rush_hour(self, t_start : time, t_end : time, t_candidate : str):
        """Check if time is between an interval. Return 1 if it is, 0 otherwise.
        
        Handle both pd.Series and single value cases."""
        if isinstance(t_candidate, pd.Series):
            t_c = t_candidate.apply(lambda r: self._parse_time(r).get("time"))
            retval = 1*t_c.apply(lambda r: r >= t_start and r < t_end)
            return retval
        else:
            t_c = self._parse_time(t_candidate).get("time")
        if t_c >= t_start and t_c < t_end:
            return 1
        else:
            return 0

    def _route_is_trunk(self, route_id) -> str:
        """Check if route is trunk route."""
        is_trunk_route = {
            r"^1500.*": "yes",
            r"^2200.*": "yes",
            r"^2510.*": "yes",
            r"^2550.*": "yes",
            r"^4560.*": "yes",
            r"^1018.*": "almost",
            r"^1039.*": "almost",
            r"^1040.*": "almost"
        }
        for pattern, result in is_trunk_route.items():
            if re.match(pattern, route_id):
                return result
        return "no"

    def add_trunk_descriptor(self, shapes : gpd.GeoDataFrame) -> gpd.GeoDataFrame:
        retval = shapes.copy()
        retval["trunk"] = retval.apply(lambda r: self._route_is_trunk(r.route_id), axis=1)
        return retval
