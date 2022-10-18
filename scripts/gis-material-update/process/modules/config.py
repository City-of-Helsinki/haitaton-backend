import yaml

from pathlib import Path
from os.path import exists

class Config:
    """Class to handle configuration."""

    def __init__(self):
        self._cfg = self._cfg_file()
        self._deployment_profile = "local_development"

    def with_deployment_profile(self, deployment_profile : str):
        """Use deployment profile.
        
        Supported configurations:
            - local_development
            - local_docker_development"""
        self._deployment_profile = deployment_profile
        return self

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

    def _file_directory(self, storage : str = "download_dir") -> str:
        """Obtain directory for input/output files. Detect running environment automatically.
        
        Supported storage parameter values:
            download_dir - directory for download files
            output_dir - directory for gis output files."""

        deployment_profile = self.deployment_profile() 
        directory = self._cfg.get("storage").get(deployment_profile).get(storage)

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

    def local_file(self, item : str) -> str:
        """Return local file name from configuration."""
        file_path = self._file_directory()
        return "/".join([file_path, self._cfg.get(item, {}).get('local_file')])

    def target_file(self, item : str) -> str:
        """Return target file name from configuration."""
        file_path = self._file_directory("output_dir")
        return "/".join([file_path, self._cfg.get(item, {}).get('target_file')])

    def target_buffer_file(self, item : str) -> str:
        """Return target buffer file name from configuration."""
        file_path = self._file_directory("output_dir")
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

        deployment_profile = self.deployment_profile() 
        directory = self._cfg.get("storage").get(deployment_profile).get("download_dir")
        candidate_path = None

        # TODO: how to handle this part in configuration YAML
        if deployment_profile == "local_development":
            candidate_path = Path(__file__).parent.parent.parent / directory
        elif deployment_profile == "local_docker_development":
            candidate_path = Path(directory)
            
        candidate_file = self._cfg.get(data_descriptor).get(file_desc)
        # candidate_paths = [
        #     Path(__file__).parent.parent.parent / "haitaton-downloads",
        #     self._cfg.get("common").get("download_path")
        #     ]
        # for c_p in candidate_paths:
        #     cand_path = Path(c_p) / candidate_file
        #     if Path(c_p).is_dir():
        #         return str(cand_path)
        # do not guess but assign path by deployment
        file_path = Path(self._cfg.get("storage").get(self.deployment_profile()).get("download_dir"))
        file_path /= candidate_file

        if file_path.is_file():
            return str(file_path)

        return None

    def pg_conn_string(self, deployment : str = None) -> str:
        """Return connection string for database.
        
        deployment -parameter refers to deployment configuration."""
        if deployment is None:
            deployment = self.deployment_profile()

        return "PG:'host={} dbname={} user={} password={} port={}'".format(
            self._cfg.get("database").get(deployment).get("host"),
            self._cfg.get("database").get(deployment).get("database"),
            self._cfg.get("database").get(deployment).get("username"),
            self._cfg.get("database").get(deployment).get("password"),
            self._cfg.get("database").get(deployment).get("port")
        )

    def pg_conn_uri(self, deployment : str = None) -> str:
        """Return PostgreSQL connect URI
        
        deployment -parameter refers to deployment in configuration"""
        if deployment is None:
            deployment = self.deployment_profile()

        return "postgresql://{user}:{password}@{host}:{port}/{dbname}".format(
            host=self._cfg.get("database").get(deployment).get("host"),
            dbname=self._cfg.get("database").get(deployment).get("database"),
            user=self._cfg.get("database").get(deployment).get("username"),
            password=self._cfg.get("database").get(deployment).get("password"),
            port=self._cfg.get("database").get(deployment).get("port")
        )

    def deployment_profile(self) -> str:
        return self._deployment_profile
