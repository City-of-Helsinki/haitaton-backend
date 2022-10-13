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

    def local_file(self, item : str) -> str:
        """Return local file name from configuration."""
        return self._cfg.get(item, {}).get('local_file')

    def layer(self, item : str) -> str:
        """Return layer name of source data from configuration."""
        return self._cfg.get(item, {}).get('layer')

    def downloaded_file_name(self, data_descriptor : str) -> str:
        """Return complete file path for further file access."""
        candidate_file = self._cfg.get(data_descriptor).get("local_file")
        candidate_paths = [
            Path(__file__).parent.parent.parent / "haitaton-downloads",
            self._cfg.get("common").get("download_path")
            ]
        for c_p in candidate_paths:
            cand_path = Path(c_p) / candidate_file
            if cand_path.is_file():
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

