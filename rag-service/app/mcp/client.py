import json
from mcp.client.stdio import stdio_client
import asyncio
import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MCP_PATH = os.path.join(BASE_DIR, "run_mcp.py")


class MCPClient:
    def __init__(self):
        self.client = None
        self.connected = False

    async def connect(self):
        if not self.connected:
            self.client = await stdio_client(
                command="python",
                args=[MCP_PATH]
            )
            self.connected = True

    async def vector_search(self, embedding, top_k, min_similarity):
        await self.connect()

        print("📡 Calling MCP vector_search...", flush=True)

        result = await asyncio.wait_for(
            self.client.call_tool(
                "vector_search",
                {
                    "query_embedding": embedding,
                    "top_k": top_k,
                    "min_similarity": min_similarity
                }
            ), timeout=10)

        print("📥 MCP response received", flush=True)

        return json.loads(result[0].text)

    async def web_search(self, query: str):
        await self.connect()

        print("📡 Calling MCP web_search...", flush=True)

        result = await asyncio.wait_for(
            self.client.call_tool(
                "web_search",
                {
                    "query": query
                }
            ), timeout=10)

        print("📥 MCP response received", flush=True)

        return json.loads(result[0].text)
