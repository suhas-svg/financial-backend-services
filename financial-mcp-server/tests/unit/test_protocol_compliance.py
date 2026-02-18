"""
Unit tests for MCP protocol compliance features.
"""

from types import SimpleNamespace

from mcp_financial.protocol.compliance import ProtocolCompliance
from mcp_financial.protocol.versioning import VersionManager


def _codes(issues):
    return {issue.code for issue in issues}


class TestVersionManager:
    """Protocol version manager behavior."""

    def test_supported_versions_include_current(self):
        manager = VersionManager()

        supported = manager.get_supported_versions()

        assert "2024-11-05" in supported
        assert manager.get_latest_version() == "2024-11-05"

    def test_negotiate_version_falls_back_to_latest_supported(self):
        manager = VersionManager()

        # Unknown/older client version should negotiate to latest supported.
        negotiated = manager.negotiate_version("2024-01-01")

        assert negotiated == "2024-11-05"


class TestProtocolCompliance:
    """Protocol compliance validations."""

    def setup_method(self):
        self.compliance = ProtocolCompliance(VersionManager())

    def test_validate_initialize_request_happy_path(self):
        request = SimpleNamespace(
            protocolVersion="2024-11-05",
            capabilities={},
            clientInfo={"name": "test-client", "version": "1.0.0"},
        )

        issues = self.compliance.validate_initialize_request(request)

        assert issues == []

    def test_validate_initialize_request_missing_required_fields(self):
        request = SimpleNamespace(
            protocolVersion=None,
            capabilities=None,
            clientInfo=None,
        )

        issues = self.compliance.validate_initialize_request(request)
        codes = _codes(issues)

        assert "MISSING_PROTOCOL_VERSION" in codes
        assert "MISSING_CAPABILITIES" in codes
        assert "MISSING_CLIENT_INFO" in codes

    def test_validate_list_tools_result_with_invalid_tool_schema(self):
        invalid_tool = SimpleNamespace(
            name="create_account",
            description="create account",
            inputSchema={"type": "not-a-real-type"},
        )
        tools_result = SimpleNamespace(tools=[invalid_tool])

        issues = self.compliance.validate_list_tools_result(tools_result)
        codes = _codes(issues)

        assert "INVALID_SCHEMA_TYPE" in codes

    def test_validate_call_tool_result_warns_on_unstructured_error(self):
        error_result = SimpleNamespace(
            content=[SimpleNamespace(type="text", text='{"message":"failed"}')],
            isError=True,
        )

        issues = self.compliance.validate_call_tool_result(error_result)
        codes = _codes(issues)

        assert "UNSTRUCTURED_ERROR_RESPONSE" in codes

    def test_get_compliance_report_marks_non_compliant_on_errors(self):
        bad_request = SimpleNamespace(
            protocolVersion=None,
            capabilities={},
            clientInfo={"name": "client"},
        )

        report = self.compliance.get_compliance_report(
            initialize_request=bad_request
        )

        assert report["overall_status"] == "non_compliant"
        assert report["summary"]["errors"] >= 1
