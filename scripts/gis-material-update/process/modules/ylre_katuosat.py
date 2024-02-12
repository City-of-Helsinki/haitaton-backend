import geopandas as gpd
from sqlalchemy import create_engine


from modules.config import Config


class YlreKatuosat:
    """Process YLRE parts"""

    def __init__(self, cfg: Config):
        self._cfg = cfg
        self._module = "ylre_katuosat"

        filename = cfg.local_file(self._module)
        layer = cfg.layer(self._module)
        df = gpd.read_file(filename, layer=layer)
        df["ylre_types_concat"] = df["paatyyppi"] + " - " + df["alatyyppi"]
        selected_types = [
            "Ajorata - Ajorata",
            "Ajorata - Ajorata, muu",
            "Ajorata - Koroke",
            "Ajorata - Tonttiliittymä",
            "Silta - Ajorata (Silta)",
            "Silta - Ajorata, muu (Silta)",
            "Silta - Koroke (Silta)",
        ]
        df = df[df["ylre_types_concat"].isin(selected_types)].loc[:, ["geometry"]]
        df["ylre_street_area"] = 1
        df["fid"] = df.reset_index().index
        self._df = df.set_index("fid")
        self._df = self._df.astype({"ylre_street_area": "int32"})

    def process(self):
        buffers = self._cfg.buffer(self._module)
        buffer_amount = buffers[0]
        process_result = self._df.copy()
        process_result["geometry"] = process_result.buffer(buffer_amount)
        process_result = process_result.explode(index_parts=False)
        process_result["fid"] = process_result.reset_index().index
        self._process_result = process_result.set_index("fid")

    def persist_to_database(self):
        connection = create_engine(self._cfg.pg_conn_uri())

        self._df.to_postgis(
            "ylre_parts_orig_polys",
            connection,
            "public",
            if_exists="replace",
            index=True,
            index_label="fid",
        )

        self._process_result.to_postgis(
            "ylre_parts_polys",
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

        polygons = self._process_result.reset_index()
        schema = gpd.io.file.infer_schema(polygons)
        schema["properties"]["ylre_street_area"] = "int32"

        polygons.to_file(file_name, schema=schema, driver="GPKG")
