import logging
from contextlib import asynccontextmanager
from datetime import datetime, timezone

from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse

from config import settings
from models.incident import EnrichedIncident
from models.analysis import IncidentAnalysis
from graph.supervisor import rag_graph
from graph.state import AgentState
from langchain_openai import OpenAIEmbeddings
from pydantic import BaseModel

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s — %(message)s",
)
log = logging.getLogger(__name__)

_embeddings = OpenAIEmbeddings(
    model=settings.embedding_model,
    api_key=settings.openai_api_key,
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    log.info("RAG service starting — model=%s  embed=%s",
             settings.llm_model, settings.embedding_model)
    yield
    log.info("RAG service shutting down")


app = FastAPI(
    title="Incident RAG Service",
    version="1.0.0",
    lifespan=lifespan,
)


# ── Main analysis endpoint ────────────────────────────────────────────────────

@app.post("/analyze", response_model=IncidentAnalysis)
async def analyze(incident: EnrichedIncident) -> IncidentAnalysis:
    log.info("Analyzing %s  priority=%s", incident.number, incident.priority)

    try:
        initial: AgentState = {
            "incident":           incident,
            "embedding":          [],
            "similar_incidents":  [],
            "retrieval_attempts": 0,
            "root_cause":         "",
            "resolution":         "",
            "immediate_actions":  [],
            "confidence":         0.0,
            "needs_reretrieval":  False,
            "reasoning_trace":    [],
            "deep_analysis_done": False,
            "web_search_done":    False,
            "web_results":        [],
            "loop_count":         0,
            "max_loops":          3,
        }

        final = await rag_graph.ainvoke(initial)

        result = IncidentAnalysis(
            sys_id=incident.sys_id,
            number=incident.number,
            root_cause=final["root_cause"],
            resolution=final["resolution"],
            immediate_actions=final["immediate_actions"],
            confidence=final["confidence"],
            similar_incident_numbers=[
                s["number"] for s in final["similar_incidents"]
            ],
            agent_reasoning_trace=final["reasoning_trace"],
            retrieval_attempts=final["retrieval_attempts"],
            analyzed_at=datetime.now(timezone.utc),
        )

        log.info("Completed %s  confidence=%.2f  attempts=%d  similar=%d",
                 incident.number, result.confidence,
                 result.retrieval_attempts,
                 len(result.similar_incident_numbers))
        return result

    except Exception as e:
        log.exception("Analysis failed for %s", incident.number)
        raise HTTPException(status_code=500, detail=str(e))


# ── Embedding endpoint (used by Java LearningService) ────────────────────────

class EmbedRequest(BaseModel):
    text: str


@app.post("/embed")
async def embed(request: EmbedRequest) -> list[float]:
    try:
        return await _embeddings.aembed_query(request.text)
    except Exception as e:
        log.exception("Embed failed")
        raise HTTPException(status_code=500, detail=str(e))


# ── Health check ──────────────────────────────────────────────────────────────

@app.get("/health")
async def health():
    return {
        "status":  "ok",
        "service": "rag-service",
        "model":   settings.llm_model,
        "embed":   settings.embedding_model,
    }


# ── Global exception handler ──────────────────────────────────────────────────

@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    log.error("Unhandled exception: %s", exc)
    return JSONResponse(
        status_code=500,
        content={"detail": "Internal server error"},
    )
