import logging

from modules.config import Config
from modules.common import *
from modules.gis_validate_deploy import GisProcessor

class YlreKatualueet(GisProcessor):
    """Process YLRE street areas"""

    def __init__(self, cfg: Config):
        self._module = "ylre_katualueet"
        self._filename = cfg.target_buffer_file(self._module)
        GisProcessor.__init__(self, cfg)

    def get_temp_data(self, cfg: Config):
        GisProcessor.get_temp_data(self, cfg)

    def validate_deploy(self):
        GisProcessor.validate_deploy(self)