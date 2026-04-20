#!/bin/bash
# scripts/push-to-github.sh
# Run this once from the repo root to initialise git and push to GitHub.
#
# Usage:
#   chmod +x scripts/push-to-github.sh
#   GITHUB_USER=yourname GITHUB_REPO=ai-debugging-system ./scripts/push-to-github.sh

set -euo pipefail

GITHUB_USER="${GITHUB_USER:-your-github-username}"
GITHUB_REPO="${GITHUB_REPO:-ai-debugging-system}"
DEFAULT_BRANCH="main"

echo "==> Initialising git repo..."
git init
git checkout -b "$DEFAULT_BRANCH"

echo "==> Staging all files..."
git add .
git commit -m "feat: initial commit — full AI incident debugging system

Layers 1-7 implemented:
- Layer 1: Spring Boot webhook receiver
- Layer 2: Validation + normalization
- Layer 3: Dynatrace enrichment
- Layer 4: Kafka incident-events producer
- Layer 5: Processor service (dedup, score, route)
- Layer 6: Python FastAPI + LangGraph agentic RAG
- Layer 7: Kafka incident-analysis + ServiceNow writeback + learning pipeline

Stack: Spring Boot 3 · Python FastAPI · LangGraph · Claude · pgvector · Kafka · Redis
"

echo "==> Adding remote..."
git remote add origin "https://github.com/${GITHUB_USER}/${GITHUB_REPO}.git"

echo "==> Pushing to GitHub..."
git push -u origin "$DEFAULT_BRANCH"

echo ""
echo "Done! Repo live at: https://github.com/${GITHUB_USER}/${GITHUB_REPO}"
