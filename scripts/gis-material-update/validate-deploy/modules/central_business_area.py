from modules.config import Config
from modules.gis_validate_deploy import GisProcessor

class CentralBusinessAreas(GisProcessor):
    """Process Central Business Areas"""

    def __init__(self, cfg: Config):
        self._module = "central_business_area"
        self._filename = cfg.target_file(self._module)
        GisProcessor.__init__(self, cfg)