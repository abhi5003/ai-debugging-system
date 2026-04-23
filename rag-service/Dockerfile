FROM python:3.12-slim

WORKDIR /app

# ✅ Fix imports for app/ structure
ENV PYTHONPATH=/app

# System deps
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc libpq-dev curl \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt .
RUN python -m pip install --no-cache-dir -r requirements.txt

# Copy source
COPY . .

# Non-root user
RUN useradd -m appuser && chown -R appuser /app
USER appuser

EXPOSE 8000

# Healthcheck
HEALTHCHECK --interval=15s --timeout=10s --retries=5 \
    CMD ["CMD-SHELL", "curl -f http://localhost:8000/health || exit 1"]

# Start app
CMD ["uvicorn", "app.main:app",
     "--host", "0.0.0.0",
     "--port", "8000",
     "--workers", "1",
     "--log-level", "info"]