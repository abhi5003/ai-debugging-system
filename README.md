# AI Incident Debugging System

End-to-end pipeline that automatically analyzes IT incidents using Agentic RAG.

## Architecture

```
ServiceNow → Spring Boot Webhook → Validate → Enrich (Dynatrace) → Kafka
→ Processor Service → Python RAG Service (FastAPI + LangGraph + Claude)
→ Kafka incident-analysis → ServiceNow Writeback + Learning Pipeline (pgvector)
```

<img width="1440" height="1986" alt="image" src="https://github.com/user-attachments/assets/7a4bb52c-151e-483e-bfe4-5d457b9b778a" />


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
