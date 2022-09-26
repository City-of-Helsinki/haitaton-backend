import sys

from modules.config import Config
from modules.gis_processing import GisProcessor
from modules.hsl import HslBuses

def process_item(item : str):
    print(f"Processing item: {item}")
    gis_processor = instantiate_processor(item)
    gis_processor.process()
    gis_processor.persist_to_database()
    gis_processor.save_to_file()
    pass

def instantiate_processor(item : str) -> GisProcessor:
    """Instantiate correct class for processing data."""
    if item == "hsl":
        return HslBuses()
    else:
        raise Exception(f"Configuration not recognized: {item}")

if __name__ == "__main__":
    cfg = Config()

    print("Processing data:")

    for item in sys.argv[1:]:
        process_item(item)