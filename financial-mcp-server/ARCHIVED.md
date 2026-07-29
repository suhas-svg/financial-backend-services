# Archived subsystem

`financial-mcp-server` is historical, unsupported code and is not part of the
controlled synthetic-beta runtime, release authority, or current architecture.

The subsystem was archived because its broad test suite is incompatible with
the current MCP/FastMCP/Pydantic dependency surface and it did not have a locked,
reproducible dependency graph. Its deployment manifests and container build
were removed to prevent it from being mistaken for a supported service.

The source and tests remain for historical reference. They must not be deployed,
connected to customer data, or used to move money. Re-activation requires a new
reviewed design, zero failing tests, hashed dependency locks, current protocol
and authorization validation, container and dependency scanning, and explicit
integration into `Required Acceptance`.
