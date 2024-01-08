import geopandas as gpd
import pandas as pd
from sqlalchemy import create_engine
import gtfs_kit as gk
from shapely.errors import ShapelyDeprecationWarning
from shapely.geometry import LineString, Point
import warnings


from modules.config import Config
from modules.gis_processing import GisProcessor

# Select only following route_type values:
# 0 = Tram, Streetcar, Light rail. Any light rail or street level system within a metropolitan area
# 900 = Tram Service

TRAM_ROUTE_TYPE = [0, 900]

class TramLines(GisProcessor):
    """Process tram lines, i.e. schedule information."""

    def __init__(self, cfg: Config):
        self._cfg = cfg
        self._process_result_lines = None
        self._process_result_polygons = None
        self._debug_result_lines = None

        file_name = cfg.local_file("tram_lines")
        self._feed = gk.read_feed(file_name, dist_units="km")

    def _tram_trips(self) -> pd.DataFrame:
        """Pick tram trips from schedule data."""
        feed = self._feed
        tram_routes = (
            feed.routes[feed.routes.route_type.isin(TRAM_ROUTE_TYPE)]
            .route_id.unique()
            .tolist()
        )
        trip_shapes = (
            feed.trips.groupby(["shape_id", "direction_id", "route_id"], as_index=False)
            .first()
            .sort_values("route_id")
        )
        tram_trips = trip_shapes[trip_shapes.route_id.isin(tram_routes)]
        return tram_trips

    def _line_shapes(self) -> gpd.GeoDataFrame:
        """Form line geometries from schedule data."""
        shp_sorted = self._feed.shapes.sort_values(["shape_id", "shape_pt_sequence"])
        with warnings.catch_warnings():
            warnings.filterwarnings("ignore", category=ShapelyDeprecationWarning)

            shp_sorted["geometry"] = shp_sorted.apply(
                lambda p: Point(p.shape_pt_lon, p.shape_pt_lat), axis=1
            )
            shp_lines = gpd.GeoDataFrame(
                shp_sorted.groupby(["shape_id"])["geometry"].apply(
                    lambda x: LineString(x.tolist())
                ),
                geometry="geometry",
            )
        shp_lines = shp_lines.set_crs(epsg=4326)
        shp_lines = shp_lines.to_crs(self._cfg.crs())
        return shp_lines

    def process(self):
        tram_trips = self._tram_trips()
        line_shapes = self._line_shapes()
        shapes_and_trips = tram_trips.join(line_shapes, on="shape_id", how="left")
        shapes_and_trips = gpd.GeoDataFrame(shapes_and_trips, geometry="geometry")

        shapes_and_trips["lines"] = 1
        shapes_and_trips = shapes_and_trips.loc[:, ["lines", "geometry"]]
        shapes_and_trips = shapes_and_trips.astype({"lines": "int32"})

        self._process_result_lines = shapes_and_trips

        buffers = self._cfg.buffer("tram_lines")
        if len(buffers) != 1:
            raise ValueError("Unknown number of buffer values")

        # buffer lines
        target_lines_polys = self._process_result_lines.copy()
        target_lines_polys["geometry"] = target_lines_polys.buffer(buffers[0])

        # Only intersecting routes to Helsinki area are important
        # read Helsinki geographical region and reproject
        try:
            helsinki_region_polygon = gpd.read_file(
                filename=self._cfg.local_file("hki")
            ).to_crs(self._cfg.crs())
        except Exception as e:
            print("Area polygon file not found!")
            raise e
        
        target_lines_polys = gpd.clip(target_lines_polys, helsinki_region_polygon)

        # save to instance
        self._process_result_polygons = target_lines_polys

    def persist_to_database(self):
        engine = create_engine(self._cfg.pg_conn_uri(), future=True)

        # persist route lines to database
        self._process_result_lines.to_postgis(
            "tram_lines",
            engine,
            "public",
            if_exists="replace",
            index=True,
            index_label="fid",
        )

        # persist polygons to database
        self._process_result_polygons.to_postgis(
            "tram_lines_polys",
            engine,
            "public",
            if_exists="replace",
            index=True,
            index_label="fid",
        )

    def save_to_file(self):
        """Save processing results to file."""
        # tram line infra as debug material
        target_infra_file_name = self._cfg.target_file("tram_lines")

        tram_lines = self._process_result_lines.reset_index(drop=True)

        schema = gpd.io.file.infer_schema(tram_lines)
        schema["properties"]["lines"] = "int32"

        tram_lines.to_file(target_infra_file_name, schema=schema, driver="GPKG")

        # tormays GIS material
        target_buffer_file_name = self._cfg.target_buffer_file("tram_lines")

        # instruct Geopandas for correct data type in file write
        # fid is originally as index, obtain fid as column...
        tormays_polygons = self._process_result_polygons.reset_index(drop=True)

        schema = gpd.io.file.infer_schema(tormays_polygons)
        schema["properties"]["lines"] = "int32"

        tormays_polygons.to_file(target_buffer_file_name, schema=schema, driver="GPKG")
