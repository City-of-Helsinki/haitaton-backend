import unittest
import geopandas as gpd

from modules.config import Config
from modules.tram_infra import TramInfra


class TestTramInfra(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cfg = Config()
        tram_infra = TramInfra(cls.cfg)
        tram_infra.process()

        cls._target_buffer_dataframe = tram_infra._process_result_polygons
        cls._target_lines_dataframe = tram_infra._process_result_lines

    def test_line_geometry_is_linestring(self):
        lines = self._target_lines_dataframe
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

    def test_tormays_geometry_has_configured_crs(self):
        polygons = self._target_buffer_dataframe

        self.assertEqual(polygons.geometry.crs, self.cfg.crs())

    def test_tormays_attributes(self):
        tormays = self._target_buffer_dataframe
        attributes = set(["infra", "geometry"])

        self.assertEqual(set(tormays.columns.tolist()), attributes)

    def test_tormays_min_area(self):
        tormays = self._target_buffer_dataframe

        self.assertGreater(min(tormays.area), 0.0)


if __name__ == "__main__":
    unittest.main()
