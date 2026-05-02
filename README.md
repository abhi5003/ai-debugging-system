# AI Incident Debugging System

End-to-end pipeline that automatically analyzes IT incidents using Agentic RAG.

## Architecture

```
ServiceNow
   ↓
Webhook → Validation → Enrichment (Dynatrace)
   ↓
Kafka (incident-events)
   ↓
Processor Service
   ↓
RAG Service (LangGraph)

   retrieval → analysis → resolution → confidence
                     ↓
                (decision)
         ┌──────────┼───────────┬──────────┐
         │          │           │          │
      retry   deep_analysis   web_search   END
         │          │           │
         ↓          ↓           ↓
     retrieval   resolution   analysis
                    ↓           ↓
                 confidence → confidence

   ↓
Kafka (incident-analysis)
   ↓
ServiceNow + Learning (pgvector)
```

<img width="1440" height="2036" alt="image" src="https://github.com/user-attachments/assets/39871721-6269-498b-bb17-6eff0d879ac8" />



I can see your updated system flow document. Let me build a complete, production-grade interactive architecture diagram based on your **exact updated flow** — with all 9 steps, the feedback loop, and the adaptive learning cycle.Here's the complete written explanation that goes with the diagram.

---

## High-level flow — step by step

**Step 1 — Incident entry**
ServiceNow fires a signed webhook on ticket creation. The `WebhookController` (Spring Boot) validates the HMAC signature, converts the JSON to an internal model, and returns HTTP 202 immediately so ServiceNow never times out.

**Step 2 — Java pipeline: enrich and publish**
`ValidationService` checks the payload. `EnrichmentService` calls Dynatrace to pull CPU, memory, error rate, distributed traces, and service topology — giving the AI full observability context, not just the ticket text. The enriched event is published to Kafka `incident-events`.

**Step 3 — Processor layer: control flow**
The `DeduplicationService` does a fast operational similarity check (not learning — just guards against duplicate processing). `SeverityRouter` assigns priority. `RagDispatcher` makes the synchronous HTTP call to the FastAPI AI service — the only sync cross-layer boundary in the system.

**Step 4 — RAG service: the core AI brain**
Four agents run in sequence inside LangGraph:
- `retrieval_agent` calls `vector_search.py` which queries pgvector using **similarity + source trust + confidence + acceptance/rejection counts** — this is Step 6 (smart retrieval)
- `analysis_agent` uses current metrics + retrieved incidents, applying the HUMAN > AI rule to determine root cause
- `resolution_agent` generates ordered resolution steps
- `confidence_agent` scores the result 0–1

`supervisor.py` then routes: high confidence → END. Low confidence triggers either `deep_analysis_agent` (conflicting signals) or `web_search_agent` (unknown issue). The loop repeats until confidence is high or max attempts are hit.

**Steps 5 & 6 — Output and writeback**
The result is published to Kafka `incident-analysis`. `ServiceNowWritebackListener` picks it up and writes the AI suggestion back to ServiceNow with `status = PENDING_APPROVAL`. **The AI never auto-resolves.**

**Step 7 — Human-in-the-loop**
The engineer sees the AI suggestion and decides. ACCEPT stores the result with `source=AI` and the model's confidence score. REJECT stores the human correction with `source=HUMAN` and `confidence=1.0` (maximum trust). Both paths increment counters.

**Step 8 — Feedback pipeline**
ServiceNow fires a feedback webhook → Kafka `incident-feedback` → `FeedbackListener` → `LearningService`. The learning service writes the result to the `incident_embeddings` table and updates `acceptance_count` / `rejection_count`.

**Step 9 — Adaptive learning**
A scheduled job recalculates confidence for every embedding: `confidence = acceptance_count / feedback_count`. High-acceptance embeddings surface higher in future retrievals. High-rejection embeddings are deprioritised. This feeds directly back into Step 4.1 — closing the self-improvement loop.

---

## The one-line summary

> AI suggests → Human corrects → System learns → AI improves

## Services

| Service | Tech | Port |
|---------|------|------|
| java-pipeline | Spring Boot 3 | 8080 |
| rag-service | FastAPI + LangGraph | 8000 |
| PostgreSQL + pgvector | PostgreSQL 16 | 5432 |
| Kafka | Confluent | 9092 |
| Redis | Redis 7 | 6379 |
| Kafka UI | Provectus | 8090 |

## Quick Start (Local)

```bash
# 1. Start infrastructure
docker-compose up -d zookeeper kafka kafka-ui postgres redis

# 2. Start RAG service
cd rag-service
cp .env.example .env   # fill in API keys
docker-compose up -d rag-service

# 3. Start Java pipeline
cd java-pipeline
cp src/main/resources/application.yml.example src/main/resources/application-local.yml
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 4. Test end-to-end
./scripts/test-incident.sh
```

## Environment Variables

### Java Pipeline
| Variable | Description |
|----------|-------------|
| WEBHOOK_SECRET | Secret token for ServiceNow webhook |
| KAFKA_BROKERS | Kafka bootstrap servers |
| REDIS_HOST | Redis hostname |
| DT_API_TOKEN | Dynatrace API token |
| DT_BASE_URL | Dynatrace environment URL |
| SERVICENOW_BASE_URL | ServiceNow instance URL |
| SERVICENOW_USER | ServiceNow username |
| SERVICENOW_PASSWORD | ServiceNow password |
| RAG_SERVICE_URL | Python RAG service URL |

### RAG Service
| Variable | Description |
|----------|-------------|
| ANTHROPIC_API_KEY | Anthropic Claude API key |
| OPENAI_API_KEY | OpenAI embeddings API key |
| DATABASE_URL | PostgreSQL connection URL |

## Deployment

```bash
# Kubernetes
kubectl apply -f k8s/infra/
kubectl apply -f k8s/java-pipeline/
kubectl apply -f k8s/rag-service/
```
