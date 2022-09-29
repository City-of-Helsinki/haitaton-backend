import yaml

from pathlib import Path

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
