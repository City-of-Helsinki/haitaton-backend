from datetime import datetime, time
import geopandas as gpd
import pandas as pd
import unittest

from test.compare_utils import TormaysCheckerMixin
from modules.config import Config
from modules.hsl import HslBuses


class TestHslLines(TormaysCheckerMixin, unittest.TestCase):
    """Test that final output contains correct geometry and attributes."""

    @classmethod
    def setUpClass(cls):
        cls.cfg = Config()
        hsl = HslBuses(cls.cfg, validate_gtfs=False)
        hsl.process()

        cls._target_buffer_dataframe = hsl._process_result_polygons
        cls._target_lines_dataframe = hsl._process_result_lines

    def test_line_geometry_is_linestring(self):
        self.check_unique_geometry_type(self._target_lines_dataframe, "linestring")

    def test_tormays_geometry_is_polygon(self):
        self.check_unique_geometry_type(self._target_buffer_dataframe, "polygon")

    def test_tormays_geometry_has_configured_crs(self):
        self.check_geometry_has_configured_crs(
            self._target_buffer_dataframe, self.cfg.crs()
        )

    def test_tormays_attributes(self):
        self.check_geom_data_attributes(
            self._target_buffer_dataframe,
            ["direction_id", "route_id", "rush_hour", "trunk", "geometry"],
        )

    def test_tormays_min_area(self):
        self.check_geom_data_min_area(self._target_buffer_dataframe)


class TestHslGtfs(unittest.TestCase):
    """Test HSL GTFS for validity"""

    @unittest.skip("HSL GTFS is broken for now and we know it.")
    def test_gtfs_is_valid(self):
        cfg = Config()
        hsl = HslBuses(cfg, validate_gtfs=False)
        report = hsl.feed().validate()

        self.assertEqual(
            sum(report.type == "error"), 0, "There should be no errors in GTFS file"
        )


class TestHslInternals(unittest.TestCase):
    """Test processing class methods and feed contents."""

    @classmethod
    def setUpClass(cls):
        cfg = Config()
        cls.hsl = HslBuses(cfg, validate_gtfs=False)
        cls.feed = cls.hsl.feed()

    def test_overlaps(self):
        a = "20221001"
        b = "20221002"

        c = "20221002"
        d = "20221003"
        e = "20221004"

        self.assertTrue(self.hsl._overlaps(a, b, c, d))

        self.assertFalse(self.hsl._overlaps(a, b, d, e))

        self.assertTrue(self.hsl._overlaps(c, d, a, b))

        self.assertFalse(self.hsl._overlaps(d, e, a, b))

        self.assertTrue(self.hsl._overlaps(a, e, b, c))

        self.assertTrue(self.hsl._overlaps(b, c, a, e))

        self.assertTrue(self.hsl._overlaps(a, a, a, a))

    def test_feed_contains_agency(self):
        self.assertTrue(isinstance(self.feed.agency, pd.DataFrame))

    def test_feed_contains_stops(self):
        self.assertTrue(isinstance(self.feed.stops, pd.DataFrame))

    def test_feed_contains_routes(self):
        self.assertTrue(isinstance(self.feed.routes, pd.DataFrame))

    def test_feed_contains_trips(self):
        self.assertTrue(isinstance(self.feed.trips, pd.DataFrame))

    def test_feed_contains_stop_times(self):
        self.assertTrue(isinstance(self.feed.stop_times, pd.DataFrame))

    def test_feed_contains_calendar(self):
        self.assertTrue(isinstance(self.feed.calendar, pd.DataFrame))

    def test_feed_contains_shapes(self):
        self.assertTrue(isinstance(self.feed.shapes, pd.DataFrame))

    def test_transit_week(self):
        self.assertEqual(len(self.hsl.transit_week()), 7)

    def test_rush_hours(self):
        self.assertEqual(len(self.hsl._rush_hours()), 2 * 13)

    def test_rush_hour_start(self):
        morning_start_times = [
            time(6, 0),
            time(6, 15),
            time(6, 30),
            time(6, 45),
            time(7, 0),
            time(7, 15),
            time(7, 30),
            time(7, 45),
            time(8, 0),
            time(8, 15),
            time(8, 30),
            time(8, 45),
            time(9, 0),
        ]
        evening_start_times = [
            time(15, 0),
            time(15, 15),
            time(15, 30),
            time(15, 45),
            time(16, 0),
            time(16, 15),
            time(16, 30),
            time(16, 45),
            time(17, 0),
            time(17, 15),
            time(17, 30),
            time(17, 45),
            time(18, 0),
        ]
        start_times = {times[1] for times in self.hsl._rush_hours()}
        self.assertEqual(start_times, set(morning_start_times + evening_start_times))

    def test_rush_hour_end(self):
        morning_end_times = [
            time(7, 0),
            time(7, 15),
            time(7, 30),
            time(7, 45),
            time(8, 0),
            time(8, 15),
            time(8, 30),
            time(8, 45),
            time(9, 0),
            time(9, 15),
            time(9, 30),
            time(9, 45),
            time(10, 0),
        ]
        evening_end_times = [
            time(16, 0),
            time(16, 15),
            time(16, 30),
            time(16, 45),
            time(17, 0),
            time(17, 15),
            time(17, 30),
            time(17, 45),
            time(18, 0),
            time(18, 15),
            time(18, 30),
            time(18, 45),
            time(19, 0),
        ]
        end_times = {times[2] for times in self.hsl._rush_hours()}
        self.assertEqual(end_times, set(morning_end_times + evening_end_times))

    def test_hour_parse_same_day(self):
        parsed_time = self.hsl._parse_time("23:59:59")

        self.assertEqual(
            parsed_time.get("time"), datetime.strptime("23:59:59", "%H:%M:%S").time()
        )
        self.assertEqual(parsed_time.get("next_day"), False)

    def test_hour_parse_next_day(self):
        parsed_time = self.hsl._parse_time("26:00:01")

        self.assertEqual(
            parsed_time.get("time"), datetime.strptime("02:00:01", "%H:%M:%S").time()
        )
        self.assertEqual(parsed_time.get("next_day"), True)

    def test_hour_parse_midnight(self):
        parsed_time = self.hsl._parse_time("24:00:00")

        self.assertEqual(
            parsed_time.get("time"), datetime.strptime("00:00:00", "%H:%M:%S").time()
        )
        self.assertEqual(parsed_time.get("next_day"), True)

    def test_trunk_route_assign(self):
        df = gpd.GeoDataFrame([700, 701, 702, 703], columns=["route_type"])
        df_trunk = self.hsl._add_trunk_descriptor(df)
        is_trunk_line = df_trunk.loc[:, "trunk"].tolist()
        self.assertEqual(is_trunk_line, ["no", "no", "yes", "no"])


if __name__ == "__main__":
    unittest.main()
