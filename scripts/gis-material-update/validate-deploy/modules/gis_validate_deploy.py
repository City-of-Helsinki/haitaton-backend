import logging

from abc import ABC, abstractmethod
from modules.common import validate_data_count_limits, deploy
import geopandas as gpd


class GisProcessor(ABC):
    """ This class helps keeping interface consistent."""

    def __init__(self, cfg):
        self._tormays_table_org = cfg.tormays_table_org(self._module)
        self._validate_limit_min = cfg.validate_limit_min(self._module)
        self._validate_limit_max = cfg.validate_limit_max(self._module)
        self._pg_conn_uri = cfg.pg_conn_uri()
        logging.basicConfig(
            filename=cfg.logging_filename().format(self._module),
            filemode=cfg.logging_filemode(),
            format=cfg.logging_format()
            )
        self._logger = logging.getLogger()
        self._logger.setLevel(cfg.logging_level())

    def get_temp_data(self):
        self._tormays_file_temp = gpd.read_file(self._filename)
        self._tormays_file_temp.rename_geometry('geom', inplace=True)

    def validate_deploy(self):
        # validate data amount: is it between given limits
        validate_result = validate_data_count_limits(
            self._module,
            self._pg_conn_uri,
            self._tormays_table_org,
            self._tormays_file_temp,
            self._validate_limit_min,
            self._validate_limit_max,
            self._filename,
            self._logger
            )

        # Valid --> deploy
        if validate_result is True:
            deploy(
                self._pg_conn_uri,
                self._tormays_table_org,
                self._tormays_file_temp,
                self._logger
                )
        else:
            logging.error("Validation failed(" + self._module + "). No deploy")