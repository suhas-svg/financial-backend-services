"""
MCP protocol versioning and backward compatibility support.
"""

import logging
from typing import Dict, List, Optional, Any, Set
from dataclasses import dataclass
from datetime import datetime
from enum import Enum

logger = logging.getLogger(__name__)


class ProtocolVersionStatus(Enum):
    """Status of protocol versions."""
    SUPPORTED = "supported"
    DEPRECATED = "deprecated"
    UNSUPPORTED = "unsupported"


@dataclass
class ProtocolVersion:
    """MCP protocol version information."""
    version: str
    status: ProtocolVersionStatus
    release_date: datetime
    deprecation_date: Optional[datetime] = None
    end_of_life_date: Optional[datetime] = None
    features: Set[str] = None
    breaking_changes: List[str] = None
    
    def __post_init__(self):
        if self.features is None:
            self.features = set()
        if self.breaking_changes is None:
            self.breaking_changes = []


class VersionManager:
    """Manager for MCP protocol versions and compatibility."""
    
    def __init__(self):
        self.versions: Dict[str, ProtocolVersion] = {}
        self.current_version = "2024-11-05"
        self.logger = logging.getLogger(__name__)
        self._initialize_supported_versions()
        
    def _initialize_supported_versions(self) -> None:
        """Initialize supported protocol versions."""
        # MCP Protocol 2024-11-05 (Current)
        self.versions["2024-11-05"] = ProtocolVersion(
            version="2024-11-05",
            status=ProtocolVersionStatus.SUPPORTED,
            release_date=datetime(2024, 11, 5),
            features={
                "tools",
                "resources", 
                "prompts",
                "sampling",
                "logging",
                "completion",
                "roots"
            }
        )
        
        # MCP Protocol 2024-10-07 (Previous)
        self.versions["2024-10-07"] = ProtocolVersion(
            version="2024-10-07",
            status=ProtocolVersionStatus.SUPPORTED,
            release_date=datetime(2024, 10, 7),
            features={
                "tools",
                "resources",
                "prompts",
                "sampling",
                "logging"
            }
        )
        
        # Future version placeholder
        self.versions["2025-01-01"] = ProtocolVersion(
            version="2025-01-01",
            status=ProtocolVersionStatus.UNSUPPORTED,
            release_date=datetime(2025, 1, 1),
            features={
                "tools",
                "resources",
                "prompts", 
                "sampling",
                "logging",
                "completion",
                "roots",
                "streaming",
                "batch_operations"
            }
        )
        
        self.logger.info(f"Initialized {len(self.versions)} protocol versions")
        
    def is_version_supported(self, version: str) -> bool:
        """Check if a protocol version is supported."""
        protocol_version = self.versions.get(version)
        return (protocol_version is not None and 
                protocol_version.status == ProtocolVersionStatus.SUPPORTED)
                
    def get_supported_versions(self) -> List[str]:
        """Get list of supported protocol versions."""
        return [
            version for version, info in self.versions.items()
            if info.status == ProtocolVersionStatus.SUPPORTED
        ]
        
    def get_latest_version(self) -> str:
        """Get the latest supported protocol version."""
        return self.current_version
        
    def get_version_info(self, version: str) -> Optional[ProtocolVersion]:
        """Get information about a protocol version."""
        return self.versions.get(version)
        
    def negotiate_version(self, client_version: str) -> str:
        """Negotiate protocol version with client."""
        if self.is_version_supported(client_version):
            self.logger.info(f"Using client requested version: {client_version}")
            return client_version
            
        # Find the highest supported version that's <= client version
        supported_versions = self.get_supported_versions()
        compatible_versions = [
            v for v in supported_versions 
            if self._compare_versions(v, client_version) <= 0
        ]
        
        if compatible_versions:
            # Sort and get the highest compatible version
            compatible_versions.sort(key=self._version_sort_key, reverse=True)
            negotiated_version = compatible_versions[0]
            self.logger.info(f"Negotiated version {negotiated_version} for client version {client_version}")
            return negotiated_version
        else:
            # Fall back to latest supported version
            self.logger.warning(f"No compatible version found for {client_version}, using latest: {self.current_version}")
            return self.current_version
            
    def _compare_versions(self, version1: str, version2: str) -> int:
        """Compare two version strings. Returns -1, 0, or 1."""
        try:
            # Parse date-based versions (YYYY-MM-DD)
            parts1 = [int(x) for x in version1.split('-')]
            parts2 = [int(x) for x in version2.split('-')]
            
            for p1, p2 in zip(parts1, parts2):
                if p1 < p2:
                    return -1
                elif p1 > p2:
                    return 1
                    
            # If all parts are equal, compare lengths
            if len(parts1) < len(parts2):
                return -1
            elif len(parts1) > len(parts2):
                return 1
            else:
                return 0
                
        except (ValueError, IndexError):
            # Fall back to string comparison
            if version1 < version2:
                return -1
            elif version1 > version2:
                return 1
            else:
                return 0
                
    def _version_sort_key(self, version: str) -> tuple:
        """Generate sort key for version string."""
        try:
            return tuple(int(x) for x in version.split('-'))
        except ValueError:
            return (0, 0, 0)  # Fallback for invalid versions
            
    def get_version_capabilities(self, version: str) -> Set[str]:
        """Get capabilities for a specific protocol version."""
        version_info = self.get_version_info(version)
        return version_info.features if version_info else set()
        
    def is_feature_supported(self, version: str, feature: str) -> bool:
        """Check if a feature is supported in a specific version."""
        capabilities = self.get_version_capabilities(version)
        return feature in capabilities
        
    def get_breaking_changes(self, from_version: str, to_version: str) -> List[str]:
        """Get breaking changes between two versions."""
        breaking_changes = []
        
        # Get all versions between from_version and to_version
        all_versions = list(self.versions.keys())
        all_versions.sort(key=self._version_sort_key)
        
        try:
            from_idx = all_versions.index(from_version)
            to_idx = all_versions.index(to_version)
            
            if from_idx < to_idx:
                # Forward compatibility check
                for i in range(from_idx + 1, to_idx + 1):
                    version_info = self.versions[all_versions[i]]
                    breaking_changes.extend(version_info.breaking_changes)
                    
        except ValueError:
            self.logger.warning(f"Could not find version range {from_version} to {to_version}")
            
        return breaking_changes
        
    def add_version(self, version_info: ProtocolVersion) -> None:
        """Add a new protocol version."""
        self.versions[version_info.version] = version_info
        self.logger.info(f"Added protocol version {version_info.version}")
        
    def deprecate_version(self, version: str, deprecation_date: Optional[datetime] = None) -> bool:
        """Mark a version as deprecated."""
        if version not in self.versions:
            return False
            
        self.versions[version].status = ProtocolVersionStatus.DEPRECATED
        if deprecation_date:
            self.versions[version].deprecation_date = deprecation_date
            
        self.logger.info(f"Deprecated protocol version {version}")
        return True
        
    def get_compatibility_matrix(self) -> Dict[str, Dict[str, bool]]:
        """Get compatibility matrix between versions."""
        matrix = {}
        versions = list(self.versions.keys())
        
        for v1 in versions:
            matrix[v1] = {}
            for v2 in versions:
                # Simple compatibility: same version or backward compatible
                matrix[v1][v2] = (v1 == v2 or 
                                self._compare_versions(v1, v2) >= 0)
                                
        return matrix
        
    def validate_version_transition(self, from_version: str, to_version: str) -> Dict[str, Any]:
        """Validate transition between protocol versions."""
        result = {
            "valid": False,
            "warnings": [],
            "errors": [],
            "breaking_changes": []
        }
        
        if from_version not in self.versions:
            result["errors"].append(f"Unknown source version: {from_version}")
            return result
            
        if to_version not in self.versions:
            result["errors"].append(f"Unknown target version: {to_version}")
            return result
            
        from_info = self.versions[from_version]
        to_info = self.versions[to_version]
        
        # Check if target version is supported
        if to_info.status == ProtocolVersionStatus.UNSUPPORTED:
            result["errors"].append(f"Target version {to_version} is not supported")
            
        if to_info.status == ProtocolVersionStatus.DEPRECATED:
            result["warnings"].append(f"Target version {to_version} is deprecated")
            
        # Get breaking changes
        breaking_changes = self.get_breaking_changes(from_version, to_version)
        result["breaking_changes"] = breaking_changes
        
        if breaking_changes:
            result["warnings"].append(f"Found {len(breaking_changes)} breaking changes")
            
        result["valid"] = len(result["errors"]) == 0
        return result