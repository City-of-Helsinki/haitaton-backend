import unittest


from modules.config import Config
from modules.ylre_katualueet import YlreKatualueet


class TestYlreKatualueet(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cfg = Config()
        cls._cfg = cfg
        ylre_classes = YlreKatualueet(cfg)
        ylre_classes.process()
        cls._source_features = ylre_classes._df
        cls._processed_features = ylre_classes._process_result

    def test_ylre_classes_has_configured_crs(self):
        self.assertEqual(self._source_features.crs, self._cfg.crs())

    def test_original_geometry_is_polygon_only(self):
        geom_names = self._source_features.geometry.geom_type.unique().tolist()

        self.assertEqual(len(geom_names), 1)

        self.assertEqual(geom_names[0].lower(), "polygon")

    def test_processed_ylre_classes_has_configured_crs(self):
        self.assertEqual(self._processed_features.crs, self._cfg.crs())

    def test_processed_geometry_is_polygon_only(self):
        geom_names = self._processed_features.geometry.geom_type.unique().tolist()

        self.assertEqual(len(geom_names), 1)

        self.assertEqual(geom_names[0].lower(), "polygon")

    def test_tormays_attributes(self):
        attributes = set(["ylre_class", "geometry"])
        self.assertEqual(set(self._processed_features.columns.tolist()), attributes)

    def test_tormays_min_area(self):
        self.assertGreater(min(self._processed_features.area), 0.0)


if __name__ == "__main__":
    unittest.main()
