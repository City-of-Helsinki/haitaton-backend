import geopandas as gpd
from sqlalchemy import create_engine


from modules.config import Config


class YlreKatualueet:
    """Process YLRE street areas"""

    def __init__(self, cfg: Config):
        self._cfg = cfg
        self._module = "ylre_katualueet"

        filename = cfg.local_file(self._module)
        layer = cfg.layer(self._module)
        df = gpd.read_file(filename, layer=layer)

        def purpose_to_class(purpose: str) -> str:
            retval = None
            if purpose in ["Moottoriväylä", "Pääkatu"]:
                retval = "Pääkatu tai moottoriväylä"
            elif purpose == "Kokoojakatu alueellinen":
                retval = "Alueellinen kokoojakatu"
            elif purpose == "Kokoojakatu tai -tie":
                retval = "Paikallinen kokoojakatu"
            elif purpose in ["Asuntokatu", "Hidaskatu", "Pihakatu", "Tontti"]:
                retval = "Tonttikatu tai ajoyhteys"

            return retval

        df["ylre_class"] = df.apply(
            lambda r: purpose_to_class(r.kayttotarkoitus), axis=1
        )
        df = df[~df["ylre_class"].isna()]
        df = df[~(df["geometry"].isna() | df["geometry"].is_empty)].loc[
            :, ["ylre_class", "geometry"]
        ]
        df["fid"] = df.reset_index().index
        self._df = df.set_index("fid")

    def process(self):
        # no buffering or further processing needed
        self._process_result = self._df.copy()

    def persist_to_database(self):
        connection = create_engine(self._cfg.pg_conn_uri())

        self._df.to_postgis(
            "ylre_classes_orig_polys",
            connection,
            "public",
            if_exists="replace",
            index=True,
            index_label="fid",
        )

        self._process_result.to_postgis(
            "ylre_classes_polys",
            connection,
            "public",
            if_exists="replace",
            index=True,
            index_label="fid",
        )

        # persist results to temp table
        self._process_result.to_postgis(
            self._cfg.tormays_table_temp(self._module),
            connection,
            "public",
            if_exists="replace",
            index=True,
            index_label="fid",
        )

    def save_to_file(self):
        file_name = self._cfg.target_file(self._module)
        self._df.to_file(file_name, driver="GPKG")

        file_name = self._cfg.target_buffer_file(self._module)
        self._process_result.to_file(file_name, driver="GPKG")
