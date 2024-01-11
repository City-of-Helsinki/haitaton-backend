import unittest

from test.compare_utils import TormaysCheckerMixin
from modules.config import Config
from modules.liikennevaylat import Liikennevaylat


class TestLiikennevaylat(TormaysCheckerMixin, unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cfg = Config()
        tormays_data = Liikennevaylat(cls.cfg)
        tormays_data.process()

        cls._target_dataframe = tormays_data._process_result_polygons
        cls._target_lines_dataframe = tormays_data._process_result_lines

    def test_line_geometry_is_linestring(self):
        self.check_unique_geometry_type(self._target_lines_dataframe, "linestring")
        
    def test_tormays_geometry_is_polygon_or_multipolygon(self):
        accepted_geometries = ["multipolygon", "polygon"]
        self.check_geometry_type_is_in_list(self._target_dataframe, accepted_geometries)

    def test_tormays_geometry_has_configured_crs(self):
        self.check_geometry_has_configured_crs(self._target_dataframe, self.cfg.crs())

    def test_tormays_attributes(self):
        self.check_geom_data_attributes(
            self._target_dataframe,
            [
                "street_class",
                "silta_alikulku",
                "geometry",
            ],
        )

    def test_tormays_min_area(self):
        self.check_geom_data_min_area(self._target_dataframe)


if __name__ == "__main__":
    unittest.main()
