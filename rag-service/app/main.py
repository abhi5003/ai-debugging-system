import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse

from app.config import settings
from app.models.incident import EnrichedIncident
from app.models.analysis import IncidentAnalysis
from app.graph.supervisor import rag_graph
from app.graph.state_factory import create_initial_state
from app.llm.factory import LLMFactory
from app.db import create_pool, close_pool
from pydantic import BaseModel

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s — %(message)s",
)
log = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    log.info("RAG service starting — model=%s  embed=%s",
             settings.llm_model, settings.embedding_model)
    await create_pool()
    try:
        yield
    finally:
        await close_pool()
        log.info("RAG service shutting down")


app = FastAPI(
    title="Incident RAG Service",
    version="1.0.0",
    lifespan=lifespan,
)


@app.post("/analyze", response_model=IncidentAnalysis)
async def analyze(incident: EnrichedIncident) -> IncidentAnalysis:
    log.info("Analyzing %s  priority=%s", incident.number, incident.priority)

    try:
        initial = create_initial_state(incident)
        final = await rag_graph.ainvoke(initial)
        result = IncidentAnalysis.from_state(final, incident)

        log.info("Completed %s  confidence=%.2f  attempts=%d  similar=%d",
                 incident.number, result.confidence,
                 result.retrieval_attempts,
                 len(result.similar_incident_numbers))
        return result

    except Exception as e:
        log.exception("Analysis failed for %s", incident.number)
        raise HTTPException(status_code=500, detail=str(e))


class EmbedRequest(BaseModel):
    text: str


@app.post("/embed")
async def embed(request: EmbedRequest) -> list[float]:
    try:
        return await LLMFactory.create_embeddings().aembed_query(request.text)
    except Exception as e:
        log.exception("Embed failed")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/health")
async def health():
    return {
        "status":  "ok",
        "service": "rag-service",
        "model":   settings.llm_model,
        "embed":   settings.embedding_model,
    }


@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    log.error("Unhandled exception: %s", exc)
    return JSONResponse(
        status_code=500,
        content={"detail": "Internal server error"},
    )
