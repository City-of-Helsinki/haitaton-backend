import geopandas as gpd
import pandas as pd
import shapely
from datetime import datetime 
from sqlalchemy import create_engine, text
from os import path

from modules.config import Config
from modules.gis_processing import GisProcessor


class Liikennevaylat(GisProcessor):
    """Process street classes infra."""

    def __init__(self, cfg: Config):
        self._cfg = cfg
        self._process_result_lines = None
        self._process_result_polygons = None
        self._debug_result_lines = None
        self._module = "liikennevaylat"
        self._tormays_table_org = cfg.tormays_table_org(self._module)
        self._tormays_table_temp = cfg.tormays_table_temp(self._module)

        # check that ylre_katuosat file is available
        if not path.exists(self._cfg.target_buffer_file("ylre_katuosat")):
            raise FileNotFoundError("ylre katuosat polygon not found")
        
        # Loading ylre_katuosat dataset 
        ylre_katuosat_filename = cfg.target_buffer_file("ylre_katuosat")
        self._ylre_katuosat = gpd.read_file(ylre_katuosat_filename)
        self._ylre_katuosat_sindex = self._ylre_katuosat.sindex

        # check central business area file is available
        if not path.exists(self._cfg.target_file("central_business_area")):
            raise FileNotFoundError("central business area polygon not found")
        
        # Loading central business area dataset
        central_business_area_filename = cfg.target_file("central_business_area")
        self._central_business_area = gpd.read_file(central_business_area_filename)
        self._central_business_area_sindex = self._central_business_area.sindex

        # Buffering configuration
        self._buffers = self._cfg.buffer_class_values(self._module)

        if len(self._buffers) != 5:
            raise ValueError("Unknown number of buffer values")

        # Buffering classes
        self._buffer_class_street_class_values = {
            "paakatu_tai_moottorivayla": ["Pääkatu tai moottoriväylä",],
            "alueellinen_kokoojakatu": ["Alueellinen kokoojakatu",],
            "paikallinen_kokoojakatu": ["Paikallinen kokoojakatu",],
            "kantakaupungin_asuntokatu_huoltovayla_tai_muu_vahaliikenteinen_katu": ["Kantakaupungin asuntokatu, huoltoväylä tai muu vähäliikenteinen katu",],
            "asuntokatu_huoltovayla_tai_muu_vahaliikenteinen_katu": ["Asuntokatu, huoltoväylä tai muu vähäliikenteinen katu",],
        }

        # Following columns can be dropped from liikennevaylat data
        self._dropped_columns = [
            "hierarkia",
            "pituus",
            "lisatietoja",
            "yhtluontipvm",
            "yhtmuokkauspvm",
            "yhtdatanomistaja",
            "paivitetty_tietopalveluun",
            "gml_id", 
            "id", 
            "uuid",
            "paatyyppi",
            "alatyyppi",
            "yksisuuntaisuus",
            "IsInsideArea",
            "IntersectsArea",
            "index_right",
        ]

        # Following main and sub type combinations can be removed from data
        self._droppable_types = {
            "Kevyt liikenne": [
                "Suojatie",
                "Puistotie- tai väylä",
                "Jalkakäytävä",
                "Yhdistetty jalkakäytävä ja pyörätie",
                "Jalkakäytävä ja pyörätie samassa tasossa",
                "Ulkoilureitti",
                "Kulkuväylä aukiolla",
                "Pyöräkaista",
                "Pyöräliikenteen ylityspaikka",
                "Välikaistalla erotellut jalkakäytävä ja pyörätie",
                "Välikaistalla eroteltu pyörätie",
                "Jalkakäytävän tasossa oleva pyörätie",
                "Muu polku",
                "Tasoeroteltu pyörätie",
            ],
            "Muu väylä": [
                "Väylälinkki",
                "Porras/portaat",
                "Jalkakäytävä",
                "Kulkuväylä aukiolla",
            ],
            "Katu": ["Yhdistetty jalkakäytävä ja pyörätie"],
        }

        # Following main and sub type combinations should be checked if there inside kantakaupunki
        self._street_classes_check_inside_kantakaupunki = {
            "Katu": [
                "Asuntokatu",
                "Tonttikatu",
            ],
            "Muu väylä": [
                "Huoltotie",
            ],
            "Kevyt liikenne": ["Piha- ja/tai kävelykatu", "Pyöräkatu"],
        }

        # Set street_classes base on main and sub type combinations (main,sub,street_class)
        self._street_class_name_base_on_main_and_sub_type = (
            (
                "Katu",
                "Asuntokatu",
                "Asuntokatu, huoltoväylä tai muu vähäliikenteinen katu",
            ),
            ("Katu", "Paikallinen kokoojakatu", "Paikallinen kokoojakatu"),
            ("Katu", "Alueellinen kokoojakatu", "Alueellinen kokoojakatu"),
            ("Katu", "Päätie", "Pääkatu tai moottoriväylä"),
            (
                "Muu väylä",
                "Huoltotie",
                "Asuntokatu, huoltoväylä tai muu vähäliikenteinen katu",
            ),
            ("Katu", "Moottoriväylä", "Pääkatu tai moottoriväylä"),
            (
                "Katu",
                "Tonttikatu",
                "Asuntokatu, huoltoväylä tai muu vähäliikenteinen katu",
            ),
            (
                "Kevyt liikenne",
                "Piha- ja/tai kävelykatu",
                "Asuntokatu, huoltoväylä tai muu vähäliikenteinen katu",
            ),
            (
                "Kevyt liikenne",
                "Pyöräkatu",
                "Asuntokatu, huoltoväylä tai muu vähäliikenteinen katu",
            ),
        )

        file_name = cfg.local_file(self._module)

        self._lines = gpd.read_file(file_name)

    def _get_central_business_area_and_merge(self) -> gpd.GeoDataFrame:
        retval = self._central_business_area
        retval = retval.dissolve("central_business_area")
        retval["geometry"] = retval["geometry"].buffer(10)
        retval["geometry"] = retval["geometry"].buffer(-10)

        return retval

    def _set_value_based_on_IsInside_IntersectsArea_main_sub_type(self, row):
        if row["street_class"] == "Asuntokatu, huoltoväylä tai muu vähäliikenteinen katu" and (row["IsInsideArea"] or row["IntersectsArea"]):
            return "Kantakaupungin asuntokatu, huoltoväylä tai muu vähäliikenteinen katu"
        else:
            return row["street_class"]

    def _is_inside_area(self, geometry_object, area):
        # Check if geometry_object is not None before performing spatial operation
        if geometry_object is not None:
            return geometry_object.within(area)
        else:
            return False  # Return False for None values

    def _intersects_area(self, geometry_object, area):
        # Check if geometry_object is not None before performing spatial operation
        if geometry_object is not None:
            return geometry_object.intersects(area)
        else:
            return False  # Return False for None values

    def _check_and_change_central_business_area_objects(
        self, main_and_sub_types: list[str], checked_data: gpd.GeoDataFrame
    ) -> gpd.GeoDataFrame:
        retval = checked_data.copy()
        area_of_interest = self._get_central_business_area_and_merge()
        retval["IsInsideArea"] = retval.apply(lambda row: self._is_inside_area(row["geometry"], area_of_interest.geometry.iloc[0]), axis=1)
        retval["IntersectsArea"] = retval.apply(lambda row: self._intersects_area(row["geometry"], area_of_interest.geometry.iloc[0]), axis=1)
        retval["street_class"] = retval.apply(self._set_value_based_on_IsInside_IntersectsArea_main_sub_type, axis=1)

        return retval

    def _drop_unnecessary_columns(
        self, columns_to_drop: list[str], shapes: gpd.GeoDataFrame
    ) -> gpd.GeoDataFrame:
        retval = shapes.copy()
        retval.drop(columns_to_drop, axis=1, inplace=True)

        return retval

    def _drop_not_used_classes_base_on_main_and_sub_types(
        self, main_and_sub_types, shapes: gpd.GeoDataFrame
    ) -> gpd.GeoDataFrame:
        retval = shapes.copy()
        for main_type, sub_types in main_and_sub_types.items():
            for sub_type in sub_types:
                retval.drop(
                    retval[
                        (retval["paatyyppi"] == main_type)
                        & (retval["alatyyppi"] == sub_type)
                    ].index,
                    axis=0,
                    inplace=True,
                )

        return retval

    def _set_street_classes(
        self, set_class_names_list, shapes: gpd.GeoDataFrame
    ) -> gpd.GeoDataFrame:
        retval = shapes.copy()

        # Add new column street_class
        retval.insert(1, "street_class", None, True)

        # Set street_class values base on main and sub type
        for item in set_class_names_list:
            retval.loc[
                (retval["paatyyppi"] == item[0]) & (retval["alatyyppi"] == item[1]),
                "street_class",
            ] = item[2]

        return retval

    def _check_and_set_ylre_classes_id(
        self, lines: gpd.GeoDataFrame
    ) -> gpd.GeoDataFrame:
        ylre_katuosat_dissolved = self._ylre_katuosat.dissolve("ylre_street_area")
        joined_result = gpd.sjoin(lines, ylre_katuosat_dissolved, predicate='within')

        retval = lines.merge(joined_result[['uuid', 'index_right']], how='left', left_on='uuid', right_on='uuid')

        return retval

    def _buffering(
        self, lines: gpd.GeoDataFrame
    ) -> gpd.GeoDataFrame:
        # Buffer lines
        target_infra_polys = lines.copy()
        retval = target_infra_polys[0:0]
        for buffer_class, buffer_value in self._buffers.items():
            buffered_items = target_infra_polys.loc[target_infra_polys["street_class"].isin(self._buffer_class_street_class_values[buffer_class])].copy()
            buffered_items["geometry"] = buffered_items.buffer(buffer_value)
            retval = gpd.GeoDataFrame(pd.concat([retval, buffered_items], ignore_index=True))

        return retval

    def _clip_by_ylre_classes_areas(
        self, geometry: gpd.GeoDataFrame
    ) -> gpd.GeoDataFrame:
        
        ylre_katuosat_dissolved = self._ylre_katuosat.dissolve("ylre_street_area")

        ylre_areas = geometry[geometry["index_right"].notnull()]

        clipped_result=gpd.clip(ylre_areas, ylre_katuosat_dissolved)

        # Getting objects which were not clipped
        merged = ylre_areas.merge(clipped_result, how="outer", indicator=True, on="id", suffixes=("", "_right"))
        not_clipped = merged[merged["_merge"] == "left_only"].copy()
        not_clipped.drop("_merge", axis=1, inplace=True)
        common_columns = set(ylre_areas.columns).intersection(not_clipped.columns)
        common_columns.add(ylre_areas.geometry.name)
        common_columns_list = list(common_columns)
        not_clipped = not_clipped[common_columns_list].copy()

        # Adding clipped results to objects which were not interacting with YLRE areas at all
        retval = gpd.GeoDataFrame(pd.concat([geometry.loc[~geometry["index_right"].notnull()], clipped_result], ignore_index=True))

        # Adding not clipped objects 
        retval = gpd.GeoDataFrame(pd.concat([retval, not_clipped], ignore_index=True))

        return retval

    def process(self):
        self._process_result_lines = self._lines.dropna(subset=["geometry"])

        # Drop unnecessary data rows base on main and sub type
        self._process_result_lines = self._drop_not_used_classes_base_on_main_and_sub_types(
            self._droppable_types, self._process_result_lines
        )

        # Give street_class values base on main and sub type
        self._process_result_lines = self._set_street_classes(
            self._street_class_name_base_on_main_and_sub_type, self._process_result_lines
        )
        
        # Check central business area objects
        self._process_result_lines = self._check_and_change_central_business_area_objects(
            self._street_classes_check_inside_kantakaupunki, self._process_result_lines
        )

        # Mark objects which are within YLRE katuosa areas
        self._process_result_lines = self._check_and_set_ylre_classes_id(self._process_result_lines)

        # Buffer lines using buffer configuration
        target_infra_polys = self._process_result_lines.copy()
        target_infra_polys = self._buffering(target_infra_polys)

        # Clip by using YLRE katuosa areas
        target_infra_polys = self._clip_by_ylre_classes_areas(target_infra_polys)

        # Dissolve areas using attributes street_class and silta_alikulku as grouping factor
        dissolve_attrs = ["street_class", "silta_alikulku"]
        target_infra_polys = target_infra_polys.dissolve(by=dissolve_attrs, as_index=False)

        # Explode multipolygon to polygons
        target_infra_polys = target_infra_polys.explode(ignore_index=True)

        # Drop unnecessary columns
        target_infra_polys = self._drop_unnecessary_columns(
            self._dropped_columns, target_infra_polys
        )

        # Save to instance
        self._process_result_polygons = target_infra_polys

    def persist_to_database(self):
        engine = create_engine(self._cfg.pg_conn_uri(), future=True)

        # Drop z-values
        func = lambda geom: shapely.wkb.loads(shapely.wkb.dumps(geom, output_dimension=2))
        self._process_result_lines["geometry"] = self._process_result_lines["geometry"].apply(func)
        self._process_result_polygons["geometry"] = self._process_result_polygons["geometry"].apply(func)

        # persist route lines to database
        self._process_result_lines.to_postgis(
            "street_classes_lines",
            engine,
            "public",
            if_exists="replace",
            index=True,
            index_label="fid",
        )

        # persist polygons to database
        #self._process_result_polygons.to_postgis(
        #    self._tormays_table_temp,
        #    engine,
        #    "public",
        #    if_exists="replace",
        #    index=True,
        #    index_label="fid",
        #)

        # persist results to temp table
        self._process_result_polygons.to_postgis(
            self._cfg.tormays_table_temp(self._module),
            engine,
            "public",
            if_exists="replace",
            index=True,
            index_label="fid",
        )

    def save_to_file(self):
        """Save processing results to file."""
        # liikennevaylat as debug material
        target_infra_file_name = self._cfg.target_file(self._module)
        target_lines = self._process_result_lines.reset_index(drop=True)
        target_lines.to_file(target_infra_file_name, driver="GPKG")

        # tormays GIS material
        target_buffer_file_name = self._cfg.target_buffer_file(self._module)
        tormays_polygons = self._process_result_polygons.reset_index(drop=True)
        tormays_polygons.to_file(target_buffer_file_name, driver="GPKG")
