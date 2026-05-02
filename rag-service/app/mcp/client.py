import json
import logging
from mcp.client.stdio import stdio_client
import asyncio
import os

log = logging.getLogger(__name__)

from app.config import settings

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MCP_PATH = os.path.join(BASE_DIR, "run_mcp.py")


class MCPClient:
    """Singleton MCP client that manages a single connection to the MCP server.

    The Singleton pattern ensures only one subprocess connection exists,
    preventing resource waste and race conditions when multiple agents
    (retrieval, web_search) need to call MCP tools concurrently.
    """

    _instance = None

    def __init__(self):
        self.client = None
        self._context_manager = None
        self.connected = False

    @classmethod
    def get_instance(cls) -> "MCPClient":
        """Return the shared singleton instance."""
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    @classmethod
    def reset(cls):
        """Reset the singleton — useful for testing."""
        if cls._instance is not None:
            asyncio.create_task(cls._instance.close())
            cls._instance = None

    async def connect(self):
        if not self.connected:
            self._context_manager = stdio_client(
                command="python",
                args=[MCP_PATH]
            )
            self.client = await self._context_manager.__aenter__()
            self.connected = True

    async def close(self):
        if self.connected and self._context_manager is not None:
            await self._context_manager.__aexit__(None, None, None)
            self.connected = False
            self.client = None
            self._context_manager = None

    async def __aenter__(self):
        await self.connect()
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        await self.close()

    async def vector_search(
        self,
        embedding,
        top_k,
        min_similarity,
        context_text: str | None = None,
        configuration_item: str | None = None,
        priority: str | None = None,
        max_age_days: int | None = None,
    ):
        await self.connect()

        log.debug("Calling MCP vector_search with top_k=%d min_sim=%.2f", top_k, min_similarity)

        arguments = {
            "query_embedding": embedding,
            "top_k": top_k,
            "min_similarity": min_similarity,
        }

        if context_text is not None:
            arguments["context_text"] = context_text
        if configuration_item is not None:
            arguments["configuration_item"] = configuration_item
        if priority is not None:
            arguments["priority"] = priority
        if max_age_days is not None:
            arguments["max_age_days"] = max_age_days

        result = await asyncio.wait_for(
            self.client.call_tool(
                "vector_search",
                arguments
            ), timeout=settings.mcp_tool_timeout)

        log.debug("MCP vector_search response received, %d results", len(json.loads(result[0].text)))

        return json.loads(result[0].text)

    async def web_search(self, query: str):
        await self.connect()

        log.debug("Calling MCP web_search with query: %s", query)

        result = await asyncio.wait_for(
            self.client.call_tool(
                "web_search",
                {
                    "query": query
                }
            ), timeout=settings.mcp_tool_timeout)

        log.debug("MCP web_search response received")

        return json.loads(result[0].text)
