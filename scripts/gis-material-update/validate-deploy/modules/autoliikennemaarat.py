import logging

from modules.config import Config
from modules.common import *
from modules.gis_validate_deploy import GisProcessor
import geopandas as gpd

class MakaAutoliikennemaarat(GisProcessor):
    """Process traffic (car) volumes."""

    def __init__(self, cfg: Config):
        self._module = "maka_autoliikennemaarat"
        self._buffers = cfg.buffer(self._module)
        self._filename = cfg.target_buffer_file(self._module)
        self._tormays_files_temp = {}
        GisProcessor.__init__(self, cfg)

    def get_temp_data(self):
        for buffer in self._buffers:
            buffer_data = gpd.read_file(self._filename.format(buffer))
            buffer_data.rename_geometry('geom', inplace=True)
            self._tormays_files_temp[buffer] = buffer_data

    def validate_deploy(self):

        # validate data amount: is it between given limits
        validate_result = False

        for buffer in self._buffers:
            validate_result = validate_data_count_limits(
                self._module,
                self._pg_conn_uri,
                self._tormays_table_org.format(buffer),
                self._tormays_files_temp[buffer],
                self._validate_limit_min,
                self._validate_limit_max,
                self._filename.format(buffer),
                self._logger
                )
            if validate_result is True:
                self._deploy_result = deploy(
                    self._pg_conn_uri,
                    self._tormays_table_org.format(buffer),
                    self._tormays_files_temp[buffer],
                    self._logger
                    )
            else:
                logging.error("Validation failed(" + self._module + "_" + str(buffer) + "). No deploy")