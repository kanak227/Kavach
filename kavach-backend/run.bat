@echo off
cd /d "%~dp0"
echo Starting Kavach FastAPI Backend Server...
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
pause
