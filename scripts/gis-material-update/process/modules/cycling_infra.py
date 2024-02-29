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
        self._store_original_data = cfg.store_orinal_data(self._module)

        self._lines = gpd.read_file(file_name)
        self._orig = self._lines

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
        connection = create_engine(self._cfg.pg_conn_uri(), future=True)

        if self._store_original_data is not False:
            self._orig.rename_geometry('geom', inplace=True)
            # persist original data
            self._orig.to_postgis(
                self._store_original_data,
                connection,
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
