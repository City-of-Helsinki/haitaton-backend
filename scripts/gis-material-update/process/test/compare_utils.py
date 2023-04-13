"""Common compare utils for tests."""
import geopandas as gpd


class TormaysCheckerMixin(object):
    def check_unique_geometry_type(
        self, data: gpd.GeoDataFrame = None, geom_type: str = "linestring"
    ):
        geom_names = data.geometry.geom_type.unique().tolist()

        # Only one geometry type
        self.assertEqual(len(geom_names), 1)
        # Geometry is geom_type
        self.assertEqual(geom_names[0].lower(), geom_type)

    def check_geometry_has_configured_crs(
        self, data: gpd.GeoDataFrame = None, crs: str = "EPSG:3879"
    ):
        self.assertEqual(data.geometry.crs, crs)

    def check_geom_data_attributes(
        self, data: gpd.GeoDataFrame = None, ref_attributes: list[str] = None
    ):
        attributes = set(ref_attributes)
        self.assertEqual(set(data.columns.tolist()), attributes)

    def check_geom_data_min_area(self, data: gpd.GeoDataFrame = None):
        self.assertGreater(min(data.area), 0.0)

    def check_geometry_type_is_in_list(
        self, data: gpd.GeoDataFrame = None, geometries: list[str] = None
    ):
        geom_names = data.geometry.geom_type.unique().tolist()
        for gn in geom_names:
            self.assertIn(gn.lower(), geometries)
