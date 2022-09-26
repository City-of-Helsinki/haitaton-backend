from modules.config import Config
from modules.gis_processing import GisProcessor
from datetime import date, timedelta

import gtfs_kit as gk

def current_week_dates(target_date : date = None) -> list[date]:
    """Obtain weeks worth of dates from monday to sunday.
    
    If target_date is not given, set target_date to today"""
    if target_date is None:
        target_date = date.today()
    monday = target_date - timedelta(days = target_date.weekday())
    retval = [ monday + timedelta(days = d) for d in range(0,7) ]

    return retval


class HslBuses(GisProcessor):
    def __init__(self, validate_gtfs : bool = True):
        self._week = current_week_dates()
        cfg = Config()
    
        # TODO: how to obtain this string automatically?
        file_name = cfg.downloaded_file_name("hsl")
        self._feed = self._read_feed_data(file_name)
        if validate_gtfs:
            self._feed.validate()
        pass

    def _read_feed_data(self, file_name) -> gk.feed.Feed:
        """Read feed data from zip file"""
        feed = gk.read_feed(file_name, dist_units='km')
        return feed

    def process(self):
        pass

    def persist_to_database(self):
        pass

    def save_to_file(self):
        pass
