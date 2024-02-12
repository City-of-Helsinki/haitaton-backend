import logging

from modules.config import Config
from modules.common import *
from modules.gis_validate_deploy import GisProcessor

class MakaAutoliikennemaarat(GisProcessor):
    """Process traffic (car) volumes."""

    def __init__(self, cfg: Config):
        self._cfg = cfg
        self._module = "maka_autoliikennemaarat"
        self._tormays_table_org = self._cfg.tormays_table_org(self._module)
        self._tormays_table_temp = self._cfg.tormays_table_temp(self._module)
        self._validate_limit_min = self._cfg.validate_limit_min(self._module)
        self._validate_limit_max = self._cfg.validate_limit_max(self._module)
        self._pg_conn_uri = self._cfg.pg_conn_uri()
        logging.basicConfig(
            filename=self._cfg.logging_filename().format(self._module), 
            filemode=self._cfg.logging_filemode(), 
            format=self._cfg.logging_format()
            )
        self._logger = logging.getLogger()
        self._logger.setLevel(self._cfg.logging_level())
                            
    def validate_deploy(self):

        # validate data amount: is it between given limits
        buffers = self._cfg.buffer(self._module)
        self._validate_result = {}
        for buffer in buffers:
            self._validate_result[buffer] = validate_data_count_limits(
                self._module,
                self._pg_conn_uri, 
                self._tormays_table_org.format(buffer), 
                self._tormays_table_temp.format(buffer), 
                self._validate_limit_min, 
                self._validate_limit_max, 
                self._logger
                )
        
        # Valid --> deploy
        for buffer_size, result in self._validate_result.items():
            if result == "Valid":
                self._deploy_result = deploy(
                    self._pg_conn_uri, 
                    self._tormays_table_org.format(buffer_size), 
                    self._tormays_table_temp.format(buffer_size), 
                    self._logger
                    )
            else:
                logging.error("Validation failed(" + self._module + "_" + str(buffer_size) + "). No deploy")