import asyncio
from app.mcp.server import stdio_server
from app.mcp.server import Server
from app.mcp.types import Tool, TextContent
from app.mcp.vector_search import vector_search
from app.mcp.web_search import web_search
from app.mcp.reranker import rerank_incidents
import json

app = Server("incident-rag-tools")


@app.list_tools()
async def list_tools():
    return [
        Tool(
            name="vector_search",
            description="Search similar incidents with optional re-ranking and metadata filtering",
            inputSchema={
                "type": "object",
                "properties": {
                    "query_embedding": {"type": "array", "items": {"type": "number"}},
                    "top_k": {"type": "integer", "default": 20},
                    "min_similarity": {"type": "number", "default": 0.60},
                    "context_text": {"type": "string", "description": "Current incident text for re-ranking"},
                    "configuration_item": {"type": "string", "description": "Filter by service/component"},
                    "priority": {"type": "string", "description": "Filter by priority tier (includes adjacent)"},
                    "max_age_days": {"type": "integer", "default": 180},
                },
                "required": ["query_embedding"]
            }
        ),

        Tool(
            name="web_search",
            description="Search web using Tavily for incident troubleshooting",
            inputSchema={
                "type": "object",
                "properties": {
                    "query": {"type": "string"}
                },
                "required": ["query"]
            }
        )

    ]


@app.call_tool()
async def call_tool(name: str, arguments: dict):

    print(f"MCP tool called: {name}", flush=True)
    print(f"MCP arguments: {arguments}", flush=True)

    if name == "vector_search":
        results = await vector_search(**arguments)

        context_text = arguments.get("context_text")
        if context_text and results:
            results = rerank_incidents(
                query_text=context_text,
                incidents=results,
                top_k=5,
            )

        print(f"MCP returning {len(results)} results (after reranking if applicable)", flush=True)
        return [TextContent(type="text", text=json.dumps(results, default=str))]
    if name == "web_search":
        results = await web_search(**arguments)
        print(f"MCP returning {len(results)} results", flush=True)
        return [TextContent(type="text", text=json.dumps(results, default=str))]

    raise ValueError(f"Unknown tool: {name}")


async def main():
    async with stdio_server() as (read, write):
        await app.run(read, write, app.create_initialization_options())

if __name__ == "__main__":
    asyncio.run(main())
