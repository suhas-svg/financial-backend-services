"""
Main entry point for the MCP Financial Server.
"""

import asyncio
import logging
import signal
import sys
from typing import Optional

from mcp_financial.server import create_server
from mcp_financial.config.settings import get_settings
from mcp_financial.utils.logging import setup_logging
from mcp_financial.utils.metrics import setup_metrics

logger = logging.getLogger(__name__)


class MCPServerRunner:
    """MCP Server runner with graceful shutdown."""
    
    def __init__(self):
        self.server = None
        self.metrics_server = None
        self.shutdown_event = asyncio.Event()
        
    async def start(self):
        """Start the MCP server."""
        try:
            # Load settings
            settings = get_settings()
            
            # Setup logging
            setup_logging(settings.log_level, settings.log_format)
            logger.info("Starting MCP Financial Server")
            
            # Setup metrics
            if settings.metrics_enabled:
                self.metrics_server = setup_metrics(settings.metrics_port)
                
            # Create and initialize server
            self.server = await create_server(settings)
            logger.info("MCP Financial Server started successfully")
            
            # Setup signal handlers for graceful shutdown
            self._setup_signal_handlers()
            
            # Wait for shutdown signal
            await self.shutdown_event.wait()
            
        except Exception as e:
            logger.error(f"Failed to start MCP server: {e}")
            raise
            
    async def shutdown(self):
        """Graceful shutdown of the server."""
        logger.info("Shutting down MCP Financial Server")
        
        try:
            # Close server connections
            if self.server:
                # Shutdown server components
                await self.server.shutdown()
                
                # Close HTTP clients
                if hasattr(self.server, 'account_client'):
                    await self.server.account_client.close()
                if hasattr(self.server, 'transaction_client'):
                    await self.server.transaction_client.close()
                    
            # Stop metrics server
            if self.metrics_server:
                self.metrics_server.shutdown()
                
            logger.info("MCP Financial Server shutdown complete")
            
        except Exception as e:
            logger.error(f"Error during shutdown: {e}")
            
    def _setup_signal_handlers(self):
        """Setup signal handlers for graceful shutdown."""
        def signal_handler(signum, frame):
            logger.info(f"Received signal {signum}, initiating shutdown")
            asyncio.create_task(self._handle_shutdown())
            
        signal.signal(signal.SIGINT, signal_handler)
        signal.signal(signal.SIGTERM, signal_handler)
        
    async def _handle_shutdown(self):
        """Handle shutdown signal."""
        await self.shutdown()
        self.shutdown_event.set()


async def main():
    """Main application entry point."""
    runner = MCPServerRunner()
    
    try:
        await runner.start()
    except KeyboardInterrupt:
        logger.info("Received keyboard interrupt")
    except Exception as e:
        logger.error(f"Application error: {e}")
        sys.exit(1)
    finally:
        await runner.shutdown()


if __name__ == "__main__":
    # Run the server
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nShutdown complete")
    except Exception as e:
        print(f"Fatal error: {e}")
        sys.exit(1)