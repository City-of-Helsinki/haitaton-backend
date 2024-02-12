import geopandas as gpd
import pandas as pd
from sqlalchemy import create_engine, text

from modules.config import Config
from modules.gis_processing import GisProcessor


class CycleInfra(GisProcessor):
    """Process cycle route infra."""

    def __init__(self, cfg: Config):
        self._cfg = cfg
        self._process_result_lines = None
        self._process_result_polygons = None
        self._debug_result_lines = None
        self._module = "cycle_infra"

        file_name = cfg.local_file(self._module)

        self._lines = gpd.read_file(file_name)

    def process(self):
        self._process_result_lines = self._lines

        # Buffering configuration
        buffers = self._cfg.buffer(self._module)
        if len(buffers) != 1:
            raise ValueError("Unknown number of buffer values")

        # buffer lines
        target_infra_polys = self._process_result_lines.copy()
        target_infra_polys["geometry"] = target_infra_polys.buffer(buffers[0])

        # save to instance
        self._process_result_polygons = target_infra_polys

    def persist_to_database(self):
        engine = create_engine(self._cfg.pg_conn_uri(), future=True)

        # persist route lines to database
        self._process_result_lines.to_postgis(
            "cycle_infra",
            engine,
            "public",
            if_exists="replace",
            index=True,
            index_label="fid",
        )

        # persist polygons to database
        self._process_result_polygons.to_postgis(
            "cycle_infra_polys",
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
        # cycle line infra as debug material
        target_infra_file_name = self._cfg.target_file(self._module)

        cycle_lines = self._process_result_lines.reset_index(drop=True)

        cycle_lines.to_file(target_infra_file_name, driver="GPKG")

        # tormays GIS material
        target_buffer_file_name = self._cfg.target_buffer_file(self._module)

        # instruct Geopandas for correct data type in file write
        # fid is originally as index, obtain fid as column...
        tormays_polygons = self._process_result_polygons.reset_index(drop=True)

        tormays_polygons.to_file(target_buffer_file_name, driver="GPKG")
