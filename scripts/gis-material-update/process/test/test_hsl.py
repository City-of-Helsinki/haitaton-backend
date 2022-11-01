from datetime import datetime
import geopandas as gpd
import pandas as pd
import unittest


from modules.config import Config
from modules.hsl import HslBuses


class TestHslLines(unittest.TestCase):
    """Test that final output contains correct geometry and attributes."""

    @classmethod
    def setUpClass(self):
        self.cfg = Config()
        filename = self.cfg.target_buffer_file("hsl")
        self._target_buffer_dataframe = gpd.read_file(filename)

    def test_line_geometry_is_linestring(self):
        filename = self.cfg.target_file("hsl")
        lines = gpd.read_file(filename)
        geom_names = lines.geometry.geom_type.unique().tolist()

        # Only one geometry type
        self.assertEqual(len(geom_names), 1)
        # Geometry is LineString
        self.assertEqual(geom_names[0].lower(), "linestring")

    def test_tormays_geometry_is_polygon(self):
        polygons = self._target_buffer_dataframe
        geom_names = polygons.geometry.geom_type.unique().tolist()

        # Only one geometry type
        self.assertEqual(len(geom_names), 1)
        # Geometry is Polygon
        self.assertEqual(geom_names[0].lower(), "polygon")

    def test_tormays_attributes(self):
        tormays = self._target_buffer_dataframe
        attributes = set(["direction_id", "route_id", "rush_hour", "trunk", "geometry"])

        self.assertEqual(set(tormays.columns.tolist()), attributes)

    def test_tormays_min_area(self):
        tormays = self._target_buffer_dataframe

        self.assertGreater(min(tormays.area), 0.0)


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
    def setUpClass(self):
        cfg = Config()
        self.hsl = HslBuses(cfg, validate_gtfs=False)
        self.feed = self.hsl.feed()

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

    def test_trunk_route(self):
        candidates_true = [
            ("1500", "yes"),
            ("15001", "yes"),
            (" 1500", "no"),
            ("_2511", "no"),
            ("1041", "no"),
            ("1040", "yes"),
        ]
        for r_id, result in candidates_true:
            self.assertEqual(self.hsl._route_is_trunk(r_id), result)


if __name__ == "__main__":
    unittest.main()
