"""Test for configuration YAML contents."""
import unittest


from modules.config import Config


class TestConfiguration(unittest.TestCase):
    """Test configuration"""

    @classmethod
    def setUpClass(cls) -> None:
        cls._cfg = Config()

    def test_crs_is_epsg_3879(self):
        crs = self._cfg.crs()

        self.assertEqual("EPSG:3879", crs)


if __name__ == "__main__":
    unittest.main()
