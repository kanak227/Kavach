Set-Location $PSScriptRoot
Write-Host "Starting Kavach FastAPI Backend Server on http://localhost:8000 ..." -ForegroundColor Cyan
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
