from __future__ import annotations
from os.path import exists
from pathlib import Path
import yaml
import os

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
        candidate_paths = [Path("/haitaton-gis-validate-deploy"), Path(__file__).parent.parent.parent]

        for config_file in [cand / "config.yaml" for cand in candidate_paths]:
            if config_file.is_file():
                with open(str(config_file), "r") as stream:
                    try:
                        parsed = yaml.safe_load(stream)
                    except yaml.YAMLError as exc:
                        raise Exception(exc)
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
        elif deployment_profile == "docker_development":
            directory_name = Path(directory)
        else:
            raise ValueError("Storage type not detected!")

        if directory_name.exists():
            return str(directory_name)
        else:
            return None

    def deployment_profile(self) -> str:
        return self._deployment_profile

    def target_file(self, item: str) -> str:
        """Return target file name from configuration."""
        file_path = self._file_directory("output_dir")
        return "/".join([file_path, self._cfg.get(item, {}).get("target_file")])

    def target_buffer_file(self, item: str) -> str:
        """Return target buffer file name from configuration."""
        file_path = self._file_directory("output_dir")
        return "/".join([file_path, self._cfg.get(item, {}).get("target_buffer_file")])

    def buffer(self, item: str) -> list[int]:
        """Return buffer value list from configuration."""
        return self._cfg.get(item, {}).get("buffer")

    def tormays_table_org(self, item: str) -> str:
        """Return buffer value list from configuration."""
        return self._cfg.get(item, {}).get("tormays_table_org")

    def validate_limit_min(self, item: str):
        """Return buffer value list from configuration."""
        return self._cfg.get(item, {}).get("validate_limit_min")

    def validate_limit_max(self, item: str):
        """Return buffer value list from configuration."""
        return self._cfg.get(item, {}).get("validate_limit_max")

    def pg_conn_uri(self, deployment: str = None) -> str:
        """Return PostgreSQL connection URI

        deployment -parameter refers to deployment in configuration"""
        if deployment is None:
            deployment = self.deployment_profile()

        return "postgresql://{user}:{password}@{host}:{port}/{dbname}".format(
            user=os.environ.get("HAITATON_USER",self._cfg.get(deployment, {}).get("database").get("username")),
            password=os.environ.get("HAITATON_PASSWORD",self._cfg.get(deployment, {}).get("database").get("password")),
            host=os.environ.get("HAITATON_HOST",self._cfg.get(deployment, {}).get("database").get("host")),
            port=os.environ.get("HAITATON_PORT",self._cfg.get(deployment, {}).get("database").get("port")),
            dbname=os.environ.get("HAITATON_DATABASE",self._cfg.get(deployment, {}).get("database").get("database")),
            )

    def logging_filename(self) -> str:
        """Return logging file name information from config file."""
        return self._cfg.get("logging").get("logging_filename")

    def logging_filemode(self) -> str:
        """Return logging file mode information from config file."""
        return self._cfg.get("logging").get("logging_filemode")

    def logging_level(self) -> str:
        """Return logging file level information from config file."""
        return self._cfg.get("logging").get("logging_level")

    def logging_format(self) -> str:
        """Return logging file level information from config file."""
        return self._cfg.get("logging").get("logging_format")
