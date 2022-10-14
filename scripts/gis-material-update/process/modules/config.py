import yaml

from pathlib import Path
from os.path import exists

class Config:
    """Class to handle configuration."""

    def __init__(self):
        self._cfg = self._cfg_file()

    def _cfg_file(self) -> dict:
        """Try to find configuration file.
        
        Handle cases
        * dockerized implementation
        * local development
        """
        parsed = {}
        candidate_paths = [ "/haitaton-gis",  str(Path(__file__).parent.parent.parent) ]
        for c_p in candidate_paths:
            try:
                with open("{}/config.yaml".format(c_p), "r") as stream:
                    try:
                        parsed = yaml.safe_load(stream)
                    except yaml.YAMLError as exc:
                        raise Exception(exc)
            except OSError as error:
                print(f"Not found: {error.filename}")
        return parsed

    def file_directory(self, storage : str = "download") -> str:
        """Obtain directory for input/output files. Detect running environment automatically.
        
        Supported storage parameter values:
            download - directory for download files
            gis-output - directory for gis output files."""

        if storage == "download":
            local_directory_name = "haitaton-downloads"
            docker_directory_name = self._cfg.get("common").get("download_path")
        elif storage == "gis-output":
            local_directory_name = "haitaton-local-gis-output"
            docker_directory_name = self._cfg.get("common").get("gis_output_path")
        else:
            raise ValueError("Storage type not detected!")
        
        candidate_paths = [
            # local dev
            Path(__file__).parent.parent.parent / local_directory_name,
            # local via docker-compose
            Path(docker_directory_name)
            ]
        for c_p in candidate_paths:
            if c_p.is_dir() and c_p.exists():
                return str(c_p)
        return None

    def local_file(self, item : str) -> str:
        """Return local file name from configuration."""
        file_path = self.file_directory()
        return "/".join([file_path, self._cfg.get(item, {}).get('local_file')])

    def target_file(self, item : str) -> str:
        """Return target file name from configuration."""
        file_path = self.file_directory("gis-output")
        return "/".join([file_path, self._cfg.get(item, {}).get('target_file')])

    def target_buffer_file(self, item : str) -> str:
        """Return target buffer file name from configuration."""
        file_path = self.file_directory("gis-output")
        return "/".join([file_path, self._cfg.get(item, {}).get('target_buffer_file')])

    def crs(self) -> str:
        """Return CRS information from config file."""
        return self._cfg.get("common").get("crs")

    def layer(self, item : str) -> str:
        """Return layer name of source data from configuration."""
        return self._cfg.get(item, {}).get('layer')

    def buffer(self, item : str) -> list[int]:
        """Return buffer value list from configuration."""
        return self._cfg.get(item, {}).get("buffer")

    def downloaded_file_name(self, data_descriptor : str, file_desc : str = "local_file") -> str:
        """Return complete file path for further file access."""
        candidate_file = self._cfg.get(data_descriptor).get(file_desc)
        candidate_paths = [
            Path(__file__).parent.parent.parent / "haitaton-downloads",
            self._cfg.get("common").get("download_path")
            ]
        for c_p in candidate_paths:
            cand_path = Path(c_p) / candidate_file
            if Path(c_p).is_dir():
                return str(cand_path)
        return None

    def pg_conn_string(self, deployment : str) -> str:
        """Return connection string for database.
        
        deployment -parameter refers to deployment configuration."""
        return "PG:'host={} dbname={} user={} password={} port={}'".format(
            self._cfg.get("database").get(deployment).get("host"),
            self._cfg.get("database").get(deployment).get("database"),
            self._cfg.get("database").get(deployment).get("username"),
            self._cfg.get("database").get(deployment).get("password"),
            self._cfg.get("database").get(deployment).get("port")
        )

    def pg_conn_uri(self, deployment : str) -> str:
        """Return PostgreSQL connect URI
        
        deployment -parameter refers to deployment in configuration"""
        return "postgresql://{user}:{password}@{host}:{port}/{dbname}".format(
            host=self._cfg.get("database").get(deployment).get("host"),
            dbname=self._cfg.get("database").get(deployment).get("database"),
            user=self._cfg.get("database").get(deployment).get("username"),
            password=self._cfg.get("database").get(deployment).get("password"),
            port=self._cfg.get("database").get(deployment).get("port")
        )

