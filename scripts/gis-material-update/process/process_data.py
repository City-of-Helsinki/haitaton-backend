import sys
import os

from modules.config import Config
from modules.gis_processing import GisProcessor
from modules.hsl import HslBuses

def process_item(item : str, cfg : Config):
    print(f"Processing item: {item}")
    gis_processor = instantiate_processor(item, cfg)
    gis_processor.process()
    gis_processor.persist_to_database()
    gis_processor.save_to_file()
    pass

def instantiate_processor(item : str, cfg : Config) -> GisProcessor:
    """Instantiate correct class for processing data."""
    if item == "hsl":
        # TODO: Do not validate feed during development (save time)
        return HslBuses(cfg, validate_gtfs=False)
    else:
        try:
            raise RuntimeError("Configuration not recognized: {}".format(item))
        except Exception as e:
            print("{}".format(e))

if __name__ == "__main__":

    database_profile = os.environ.get("TORMAYS_DB_PROFILE")

    if database_profile in [ "local_docker_development", "local_development" ]:
        use_db_profile = database_profile
    else:
        raise ValueError("Database profile not detected")

    print("Using database configuration: '{}'".format(use_db_profile))

    cfg = Config().with_db_profile(use_db_profile)

    print("Processing data:")

    for item in sys.argv[1:]:
        process_item(item, cfg)