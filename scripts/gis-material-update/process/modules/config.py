from __future__ import annotations
from os.path import exists
from pathlib import Path
import yaml


class Config:
    """Class to handle configuration."""

    def __init__(self):
        self._cfg = self._cfg_file()
        self._deployment_profile = "local_development"

    def with_deployment_profile(self, deployment_profile: str) -> Config:
        """Set deployment profile.

        Supported configurations:
            - local_development
            - local_docker_development"""
        self._deployment_profile = deployment_profile
        return self

    def _cfg_file(self) -> dict:
        """Find configuration file.

        Return parsed configuration YAML.

        Handle cases
        * dockerized implementation
        * local development
        """
        parsed = {}
        candidate_paths = [Path("/haitaton-gis"), Path(__file__).parent.parent.parent]

        for config_file in [cand / "config.yaml" for cand in candidate_paths]:
            if config_file.is_file():
                with open(str(config_file), "r") as stream:
                    try:
                        parsed = yaml.safe_load(stream)
                    except yaml.YAMLError as exc:
                        raise Exception(exc)
                    else:
                        print("Using configuration file: {}".format(config_file))
                return parsed
        raise OSError("Configuration file was not found.")

    def _file_directory(self, storage: str = "download_dir") -> str:
        """Obtain directory for input/output files.

        Supported storage parameter values:
            download_dir - directory for download files
            output_dir - directory for gis output files."""
        deployment_profile = self.deployment_profile()
        directory = self._cfg.get(deployment_profile, {}).get("storage").get(storage)

        if deployment_profile == "local_development":
            directory_name = Path(__file__).parent.parent.parent / directory
        elif deployment_profile == "local_docker_development":
            directory_name = Path(directory)
        else:
            raise ValueError("Storage type not detected!")

        if directory_name.exists():
            return str(directory_name)
        else:
            return None

    def deployment_profile(self) -> str:
        return self._deployment_profile

    def local_file(self, item: str) -> str:
        """Return local file name from configuration."""
        file_path = self._file_directory()
        return "/".join([file_path, self._cfg.get(item, {}).get("local_file")])

    def target_file(self, item: str) -> str:
        """Return target file name from configuration."""
        file_path = self._file_directory("output_dir")
        return "/".join([file_path, self._cfg.get(item, {}).get("target_file")])

    def target_buffer_file(self, item: str) -> str:
        """Return target buffer file name from configuration."""
        file_path = self._file_directory("output_dir")
        return "/".join([file_path, self._cfg.get(item, {}).get("target_buffer_file")])

    def crs(self) -> str:
        """Return CRS information from config file."""
        return self._cfg.get("common").get("crs")

    def layer(self, item: str) -> str:
        """Return layer name of source data from configuration."""
        return self._cfg.get(item, {}).get("layer")

    def buffer(self, item: str) -> list[int]:
        """Return buffer value list from configuration."""
        return self._cfg.get(item, {}).get("buffer")

    def pg_conn_uri(self, deployment: str = None) -> str:
        """Return PostgreSQL connection URI

        deployment -parameter refers to deployment in configuration"""
        if deployment is None:
            deployment = self.deployment_profile()

        return "postgresql://{user}:{password}@{host}:{port}/{dbname}".format(
            user=self._cfg.get(deployment, {}).get("database").get("username"),
            password=self._cfg.get(deployment, {}).get("database").get("password"),
            host=self._cfg.get(deployment, {}).get("database").get("host"),
            port=self._cfg.get(deployment, {}).get("database").get("port"),
            dbname=self._cfg.get(deployment, {}).get("database").get("database"),
        )
