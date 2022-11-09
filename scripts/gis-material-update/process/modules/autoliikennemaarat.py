import geopandas as gpd
from sqlalchemy import create_engine


from modules.config import Config


class MakaAutoliikennemaarat:
    """ "Process traffic (car) volumes."""

    def __init__(self, cfg: Config):
        self._cfg = cfg
        self._module = "maka_autoliikennemaarat"

        filename = cfg.local_file(self._module)
        layer = cfg.layer(self._module)
        df = gpd.read_file(filename, layer=layer)
        self._df = (
            df.loc[:, ["autot", "geometry"]]
            .rename(columns={"autot": "volume"})
            .explode(index_parts=False)
        )
        self._df["fid"] = self._df.reset_index().index
        self._df = self._df.set_index("fid")

    def process(self) -> None:
        buffers = self._cfg.buffer(self._module)

        result = {}
        for buffer in buffers:
            target_volume_polys = self._df.copy()
            target_volume_polys["geometry"] = target_volume_polys.buffer(buffer)
            result[buffer] = target_volume_polys
        self._process_result = result

    def persist_to_database(self) -> None:
        connection = create_engine(self._cfg.pg_conn_uri())

        # persist route lines to database
        self._df.to_postgis(
            "volume_lines",
            connection,
            "public",
            if_exists="replace",
            index=True,
            index_label="fid",
        )

        # persist polygons to database
        for buffer_size, polygon_data in self._process_result.items():
            polygon_data.to_postgis(
                "volume_{}".format(buffer_size),
                connection,
                "public",
                if_exists="replace",
                index=True,
                index_label="fid",
            )

    def save_to_file(self):
        target_lines_file_name = self._cfg.target_file(self._module)
        self._df.to_file(target_lines_file_name, driver="GPKG")

        target_buffer_file_template = self._cfg.target_buffer_file(self._module)

        for buffer_size, polygon_data in self._process_result.items():
            file_name = target_buffer_file_template.format(buffer_size)
            polygon_data.to_file(file_name, driver="GPKG")
