"""Main entrypoint script for material processing."""

import os
import sys

from modules.autoliikennemaarat import MakaAutoliikennemaarat
from modules.central_business_area import CentralBusinessAreas
from modules.config import Config
from modules.cycling_infra import CycleInfra
from modules.gis_validate_deploy import GisProcessor
from modules.hsl import HslBuses
from modules.liikennevaylat import Liikennevaylat
from modules.tram_infra import TramInfra
from modules.tram_lines import TramLines
from modules.ylre_katualueet import YlreKatualueet
from modules.ylre_katuosat import YlreKatuosat

DEFAULT_DEPLOYMENT_PROFILE = "local_development"


def validate_deploy_item(item: str, cfg: Config):
    print(f"Validating deployment item: {item}")
    gis_processor = instantiate_processor(item, cfg)
    gis_processor.get_temp_data()
    gis_processor.validate_deploy()


def instantiate_processor(item: str, cfg: Config) -> GisProcessor:
    """Instantiate correct class for processing data."""
    configs = {
        "hsl": lambda: HslBuses(cfg),
        "maka_autoliikennemaarat": lambda: MakaAutoliikennemaarat(cfg),
        "ylre_katuosat": lambda: YlreKatuosat(cfg),
        "ylre_katualueet": lambda: YlreKatualueet(cfg),
        "tram_infra": lambda: TramInfra(cfg),
        "tram_lines": lambda: TramLines(cfg),
        "cycle_infra": lambda: CycleInfra(cfg),
        "liikennevaylat": lambda: Liikennevaylat(cfg),
        "central_business_area": lambda: CentralBusinessAreas(cfg),
    }
    if configs[item]:
        return configs[item]()
    print(f"Configuration not recognized: {item}")
    return None


if __name__ == "__main__":
    deployment_profile = os.environ.get("TORMAYS_DEPLOYMENT_PROFILE")
    use_deployment_profile = DEFAULT_DEPLOYMENT_PROFILE
    if deployment_profile in ["local_docker_development", "local_development"]:
        use_deployment_profile = deployment_profile
    else:
        print(f"Deployment profile environment variable is not set, defaulting to '{DEFAULT_DEPLOYMENT_PROFILE}'")

    print(f"Using deployment profile: '{use_deployment_profile}'")

    cfg = Config().with_deployment_profile(use_deployment_profile)

    print("Validating data.")

    for item in sys.argv[1:]:
        validate_deploy_item(item, cfg)
