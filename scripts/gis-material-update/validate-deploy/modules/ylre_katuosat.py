import logging

from modules.config import Config
from modules.common import *
from modules.gis_validate_deploy import GisProcessor

class YlreKatuosat(GisProcessor):
    """Process YLRE parts"""

    def __init__(self, cfg: Config):
        self._module = "ylre_katuosat"
        self._filename = cfg.target_buffer_file(self._module)
        GisProcessor.__init__(self, cfg)

    def get_temp_data(self, cfg: Config):
        GisProcessor.get_temp_data(self, cfg)

    def validate_deploy(self):
        GisProcessor.validate_deploy(self)