import geopandas as gpd
import pandas as pd
import unittest


from modules.config import Config
from modules.autoliikennemaarat import MakaAutoliikennemaarat


class TestMakaAutoliikennemaarat(unittest.TestCase):
    """Test MAKA Autoliikennemäärät -processing"""

    @classmethod
    def setUpClass(cls) -> None:
        cls.cfg = Config()
        cls._module_name = "maka_autoliikennemaarat"
        filename_template = cls.cfg.target_buffer_file(cls._module_name)
        buffers = cls.cfg.buffer(cls._module_name)

        results = {}
        for buffer in buffers:
            results[buffer] = gpd.read_file(filename_template.format(buffer))

        cls._polygon_results = results

        cls._line_results = gpd.read_file(cls.cfg.target_file(cls._module_name))

    def test_line_geometry_is_linestring(self):
        geom_names = self._line_results.geometry.geom_type.unique().tolist()

        self.assertEqual(len(geom_names), 1)

        self.assertEqual(geom_names[0].lower(), "linestring")

    def test_buffer_geometry_is_polygon(self):
        for _, geom_data in self._polygon_results.items():
            geom_names = geom_data.geometry.geom_type.unique().tolist()

            self.assertEqual(len(geom_names), 1)

            self.assertEqual(geom_names[0].lower(), "polygon")


if __name__ == "__main__":
    unittest.main()
