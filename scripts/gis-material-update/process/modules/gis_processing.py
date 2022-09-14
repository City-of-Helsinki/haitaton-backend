from abc import ABC, abstractmethod

class GisProcessor(ABC):
    """Abstract base class for GIS processing classes.
    
    This class helps keeping interface consistent."""
    @abstractmethod
    def process(self):
        pass

    @abstractmethod
    def persist_to_database(self):
        pass

    @abstractmethod
    def save_to_file(self):
        pass