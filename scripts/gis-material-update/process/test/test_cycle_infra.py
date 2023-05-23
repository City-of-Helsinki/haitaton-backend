import unittest

from test.compare_utils import TormaysCheckerMixin
from modules.config import Config
from modules.cycling_infra import CycleInfra


class TestCycleInfra(TormaysCheckerMixin, unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cfg = Config()
        cycle_infra = CycleInfra(cls.cfg)
        cycle_infra.process()

        cls._target_buffer_dataframe = cycle_infra._process_result_polygons
        cls._target_lines_dataframe = cycle_infra._process_result_lines

    def test_line_geometry_is_multilinestring(self):
        self.check_unique_geometry_type(self._target_lines_dataframe, "multilinestring")

    def test_tormays_geometry_is_polygon_or_multipolygon(self):
        accepted_geometries = ["multipolygon", "polygon"]
        self.check_geometry_type_is_in_list(
            self._target_buffer_dataframe, accepted_geometries
        )

    def test_tormays_geometry_has_configured_crs(self):
        self.check_geometry_has_configured_crs(
            self._target_buffer_dataframe, self.cfg.crs()
        )

    def test_tormays_attributes(self):
        self.check_geom_data_attributes(
            self._target_buffer_dataframe, ["type", "geometry"]
        )

    def test_tormays_min_area(self):
        self.check_geom_data_min_area(self._target_buffer_dataframe)


if __name__ == "__main__":
    unittest.main()
