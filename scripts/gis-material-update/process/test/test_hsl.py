import geopandas as gpd
import unittest


from modules.config import Config
from modules.hsl import HslBuses


class TestHslLines(unittest.TestCase):
    def __init__(self, *args, **kwargs):
        super(TestHslLines, self).__init__(*args, **kwargs)
        self.cfg = Config()

    def test_line_geometry_is_linestring(self):
        filename = self.cfg.target_file("hsl")
        lines = gpd.read_file(filename)
        geom_names = lines.geometry.geom_type.unique().tolist()

        # Only one geometry type
        self.assertEqual(len(geom_names), 1)
        # Geometry is LineString
        self.assertEqual(geom_names[0].lower(), "linestring")

    def test_tormays_geometry_is_polygon(self):
        filename = self.cfg.target_buffer_file("hsl")
        polygons = gpd.read_file(filename)
        geom_names = polygons.geometry.geom_type.unique().tolist()

        # Only one geometry type
        self.assertEqual(len(geom_names), 1)
        # Geometry is Polygon
        self.assertEqual(geom_names[0].lower(), "polygon")


class TestHslInternals(unittest.TestCase):
    def test_gtfs_is_valid(self):
        cfg = Config()
        hsl = HslBuses(cfg, validate_gtfs=False)
        report = hsl.feed().validate()

        self.assertEqual(
            sum(report.type == "error"), 0, "There should be no errors in GTFS file"
        )


if __name__ == "__main__":
    unittest.main()
