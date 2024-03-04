"""Main entrypoint script for material processing.
"""
import os
import sys

from modules.config import Config
from modules.gis_validate_deploy import GisProcessor

from modules.autoliikennemaarat import MakaAutoliikennemaarat
from modules.hsl import HslBuses
from modules.ylre_katualueet import YlreKatualueet
from modules.ylre_katuosat import YlreKatuosat
from modules.tram_infra import TramInfra
from modules.tram_lines import TramLines
from modules.cycling_infra import CycleInfra
from modules.liikennevaylat import Liikennevaylat
from modules.central_business_area import CentralBusinessAreas

DEFAULT_DEPLOYMENT_PROFILE = "local_development"

def validate_deploy_item(item: str, cfg: Config):
    print(f"Validating deployment item: {item}")
    gis_processor = instantiate_processor(item, cfg)
    gis_processor.get_temp_data()
    gis_processor.validate_deploy()
    pass

def instantiate_processor(item: str, cfg: Config) -> GisProcessor:
    """Instantiate correct class for processing data."""
    if item == "hsl":
        return HslBuses(cfg)
    elif item == "maka_autoliikennemaarat":
        return MakaAutoliikennemaarat(cfg)
    elif item == "ylre_katuosat":
        return YlreKatuosat(cfg)
    elif item == "ylre_katualueet":
        return YlreKatualueet(cfg)
    elif item == "tram_infra":
        return TramInfra(cfg)
    elif item == "tram_lines":
        return TramLines(cfg)
    elif item == "cycle_infra":
        return CycleInfra(cfg)
    elif item == "liikennevaylat":
        return Liikennevaylat(cfg)
    elif item == "central_business_area":
        return CentralBusinessAreas(cfg)
    else:
        print("{}".format(RuntimeError("Configuration not recognized: {}".format(item))))

if __name__ == "__main__":

    deployment_profile = os.environ.get("TORMAYS_DEPLOYMENT_PROFILE")
    use_deployment_profile = DEFAULT_DEPLOYMENT_PROFILE
    if deployment_profile in ["local_docker_development", "local_development"]:
        use_deployment_profile = deployment_profile
    else:
        print(
            "Deployment profile environment variable is not set, defaulting to '{}'".format(
                DEFAULT_DEPLOYMENT_PROFILE
            )
        )

    print("Using deployment profile: '{}'".format(use_deployment_profile))

    cfg = Config().with_deployment_profile(use_deployment_profile)

    print("Validating data.")

    for item in sys.argv[1:]:
        validate_deploy_item(item, cfg)
