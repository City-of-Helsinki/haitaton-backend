import geopandas as gpd
import pandas as pd
import unittest


from modules.config import Config
from modules.autoliikennemaarat import MakaAutoliikennemaarat


class TestMakaAutoliikennemaarat(unittest.TestCase):
    """Test traffic volume processing"""

    @classmethod
    def setUpClass(cls) -> None:
        cfg = Config()

        volumes = MakaAutoliikennemaarat(cfg)
        volumes.process()

        cls._line_results = volumes._df
        cls._polygon_results = volumes._process_result

    def test_line_geometry_is_linestring(self):
        geom_names = self._line_results.geometry.geom_type.unique().tolist()

        self.assertEqual(len(geom_names), 1)

        self.assertEqual(geom_names[0].lower(), "linestring")

    def test_line_geometry_has_crs_epsg_3879(self):
        self.assertEqual(self._line_results.crs, "EPSG:3879")

    def test_buffered_geometry_is_polygon(self):
        for _, geom_data in self._polygon_results.items():
            geom_names = geom_data.geometry.geom_type.unique().tolist()

            self.assertEqual(len(geom_names), 1)

            self.assertEqual(geom_names[0].lower(), "polygon")

    def test_buffered_geometry_has_crs_epsg_3879(self):
        for _, geom_data in self._polygon_results.items():
            self.assertEqual(geom_data.crs, "EPSG:3879")

    def test_tormays_attributes(self):
        attributes = set(["volume", "geometry"])
        for _, geom_data in self._polygon_results.items():
            self.assertEqual(set(geom_data.columns.tolist()), attributes)

    def test_tormays_min_area(self):
        for _, geom_data in self._polygon_results.items():
            self.assertGreater(min(geom_data.area), 0.0)


if __name__ == "__main__":
    unittest.main()
