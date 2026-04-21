import asyncio
from mcp.server import stdio_server
from mcp.server import Server
from mcp.types import Tool, TextContent
from mcp.vector_search import vector_search
import json

app = Server("incident-rag-tools")

@app.list_tools()
async def list_tools():
    return [
        Tool(
            name="vector_search",
            description="Search similar incidents",
            inputSchema={
                "type": "object",
                "properties": {
                    "query_embedding": {"type": "array", "items": {"type": "number"}},
                    "top_k": {"type": "integer", "default": 5},
                    "min_similarity": {"type": "number", "default": 0.75}
                },
                "required": ["query_embedding"]
            }
        )
    ]

@app.call_tool()
async def call_tool(name: str, arguments: dict):

    print(f"🔧 MCP tool called: {name}", flush=True)
    print(f"📥 Arguments: {arguments}", flush=True)

    if name == "vector_search":
        results = await vector_search(**arguments)
        print(f"📤 Returning {len(results)} results", flush=True)
        return [TextContent(type="text", text=json.dumps(results, default=str))]

    raise ValueError(f"Unknown tool: {name}")

async def main():
    async with stdio_server() as (read, write):
        await app.run(read, write, app.create_initialization_options())

if __name__ == "__main__":
    asyncio.run(main())