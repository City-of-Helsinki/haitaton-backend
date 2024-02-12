from abc import ABC, abstractmethod

class GisProcessor(ABC):
    """Abstract base class for GIS processing classes.
    
    This class helps keeping interface consistent."""
    @abstractmethod
    def validate_deploy(self):
        pass
