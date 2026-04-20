#!/bin/bash
# scripts/test-incident.sh
# Sends a test incident to the webhook endpoint

WEBHOOK_URL="${WEBHOOK_URL:-http://localhost:8080}"
WEBHOOK_SECRET="${WEBHOOK_SECRET:-dev-secret}"

echo "Sending test incident to $WEBHOOK_URL..."

curl -s -w "\nHTTP Status: %{http_code}\n" \
  -X POST "$WEBHOOK_URL/webhook/incident" \
  -H "Content-Type: application/json" \
  -H "X-Secret-Token: $WEBHOOK_SECRET" \
  -d '{
    "sysId":            "test-sys-001",
    "number":           "INC0099001",
    "shortDescription": "Payment service DB connection pool exhausted causing 500 errors",
    "priority":         "1",
    "state":            "1",
    "assignedTo":       "on-call-team",
    "cmdbCi":           "payment-service",
    "updatedAt":        "2025-04-19 10:00:00"
  }'

echo ""
echo "Check Kafka UI at http://localhost:8090 for incident-events and incident-analysis topics"
echo "Check logs: docker-compose logs -f java-pipeline rag-service"
