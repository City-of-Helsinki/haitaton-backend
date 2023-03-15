import unittest

from test.compare_utils import TormaysCheckerMixin
from modules.config import Config
from modules.tram_lines import TramLines


class TestTramLines(TormaysCheckerMixin, unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cfg = Config()
        tram_lines = TramLines(cls.cfg)
        tram_lines.process()

        cls._target_buffer_dataframe = tram_lines._process_result_polygons
        cls._target_lines_dataframe = tram_lines._process_result_lines

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
            self._target_buffer_dataframe, ["lines", "geometry"]
        )

    def test_tormays_min_area(self):
        self.check_geom_data_min_area(self._target_buffer_dataframe)


if __name__ == "__main__":
    unittest.main()
