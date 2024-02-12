import geopandas as gpd
import pandas as pd
from sqlalchemy import create_engine, text

from modules.config import Config
from modules.gis_processing import GisProcessor

# Select only following tag values:
# tram = Urban tram
# light_rail = Light rail

TRAM_TYPES = ["tram", "light_rail"]

def dict_values_to_list(d: dict) -> dict:
    """Transform dict values to list."""
    return {k: [v] for k, v in d.items()}


def other_tag_to_dict(tags: str) -> dict:
    """Split OSM tags to dict."""
    tags = tags[1:-1]
    return {
        k: v for k, v in [s.split('"=>"') for s in tags.split('","')] if v is not None
    }


class TramInfra(GisProcessor):
    """Process tram infra."""

    def __init__(self, cfg: Config):
        self._cfg = cfg
        self._process_result_lines = None
        self._process_result_polygons = None
        self._debug_result_lines = None
        self._module = "tram_infra"

        file_name = cfg.local_file(self._module)

        self._lines = gpd.read_file(file_name)

    def process(self):
        lines = self._lines
        lines_with_tags = lines[~lines.other_tags.isna()].copy()

        lines_with_tags["tag_dict"] = lines_with_tags.apply(
            lambda r: other_tag_to_dict(r.other_tags), axis=1
        )
        lines_with_tags_tram_index = lines_with_tags.apply(
            lambda r: any(tag_value in r.tag_dict.get("railway", []) for tag_value in TRAM_TYPES), axis=1
        )
        tram_lines = lines_with_tags[lines_with_tags_tram_index]

        df_list = []
        for i, r in tram_lines.iterrows():
            df_list.append(pd.DataFrame(dict_values_to_list(r.tag_dict)))
        df_new = pd.concat(df_list)
        df_new.index = tram_lines.index
        trams = tram_lines.join(df_new, how="inner").drop(["tag_dict"], axis=1)
        trams["infra"] = 1
        trams = trams.astype({"infra": "int32"})
        self._process_result_lines = trams.loc[:, ["infra", "geometry"]]
        self._debug_result_lines = trams

        # Buffering configuration
        buffers = self._cfg.buffer(self._module)
        if len(buffers) != 1:
            raise ValueError("Unkown number of buffer values")

        # buffer lines
        target_infra_polys = self._process_result_lines.copy()
        target_infra_polys["geometry"] = target_infra_polys.buffer(buffers[0])

        # Only intersecting objects to Helsinki area are important
        # read Helsinki geographical region and reproject
        try:
            helsinki_region_polygon = gpd.read_file(
                filename=self._cfg.local_file("hki")
            ).to_crs(self._cfg.crs())
        except Exception as e:
            print("Area polygon file not found!")
            raise e
        
        target_infra_polys = gpd.clip(target_infra_polys, helsinki_region_polygon)

        # save to instance
        self._process_result_polygons = target_infra_polys

    def persist_to_database(self):
        engine = create_engine(self._cfg.pg_conn_uri(), future=True)

        # persist original results for debugging and development
        debug_schema = "debug"

        with engine.connect() as conn:
            conn.execute(text("CREATE SCHEMA IF NOT EXISTS {}".format(debug_schema)))
            conn.commit()

        self._debug_result_lines.to_postgis(
            "tram_infra",
            engine,
            debug_schema,
            if_exists="replace",
            index=True,
            index_label="fid",
        )

        # persist route lines to database
        self._process_result_lines.to_postgis(
            "tram_infra",
            engine,
            "public",
            if_exists="replace",
            index=True,
            index_label="fid",
        )

        # persist polygons to database
        self._process_result_polygons.to_postgis(
            "tram_infra_polys",
            engine,
            "public",
            if_exists="replace",
            index=True,
            index_label="fid",
        )

        # persist results to temp table
        self._process_result_polygons.to_postgis(
            self._cfg.tormays_table_temp(self._module),
            engine,
            "public",
            if_exists="replace",
            index=True,
            index_label="fid",
        )

    def save_to_file(self):
        """Save processing results to file."""
        # tram line infra as debug material
        target_infra_file_name = self._cfg.target_file(self._module)

        tram_lines = self._process_result_lines.reset_index(drop=True)

        schema = gpd.io.file.infer_schema(tram_lines)
        schema["properties"]["infra"] = "int32"

        tram_lines.to_file(target_infra_file_name, schema=schema, driver="GPKG")

        # tormays GIS material
        target_buffer_file_name = self._cfg.target_buffer_file(self._module)

        # instruct Geopandas for correct data type in file write
        # fid is originally as index, obtain fid as column...
        tormays_polygons = self._process_result_polygons.reset_index(drop=True)

        schema = gpd.io.file.infer_schema(tormays_polygons)
        schema["properties"]["infra"] = "int32"

        tormays_polygons.to_file(target_buffer_file_name, schema=schema, driver="GPKG")
