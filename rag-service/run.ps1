# Activate virtual environment
.\venv\Scripts\Activate.ps1

# Run FastAPI app
python -m uvicorn app.main:app --reload