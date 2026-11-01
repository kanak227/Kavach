import asyncio
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.ai import router as ai_router
from app.api.auth import router as auth_router
from app.api.behavior import router as behavior_router
from app.api.devices import router as devices_router
from app.api.telemetry import router as telemetry_router
from app.core.config import settings
from app.db.init_db import init_database

logging.basicConfig(
    level=logging.INFO if not settings.DEBUG else logging.DEBUG,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifecycle event handler for database connection and setup."""
    logger.info("Starting Kavach Cloud API v%s...", settings.VERSION)
    is_placeholder = any(p in settings.DATABASE_URL for p in ["YOUR_HOST", "YOUR_PASSWORD", "localhost"])
    if is_placeholder:
        logger.info("ℹ️ Running with placeholder or local database config. Supply Tiger Data credentials in .env to connect live.")
    else:
        try:
            await asyncio.wait_for(init_database(), timeout=5.0)
            logger.info("Database schemas and Tiger Data hypertables connected successfully.")
        except Exception as e:
            logger.warning("Database init note: %s. API server is running.", e)
    yield
    logger.info("Kavach Cloud API shutting down...")


app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description=(
        "⚡ High-performance asynchronous backend for **Kavach — Smart Anti-Theft Shield**.\n\n"
        "Features:\n"
        "- 🔐 **JWT Authentication & bcrypt security**\n"
        "- 🐅 **Tiger Data (TimescaleDB) Telemetry & Behavior Ingestion** with 90%+ compression\n"
        "- 🧠 **Adaptive Behavioral Routine Engine** (Autonomous Safe-Zone discovery & anomaly scoring)\n"
        "- ✨ **Google Gemini 1.5 AI Forensics** (Automated theft diagnosis, police draft & recovery playbook)\n"
        "- 📱 **Device Pairing & Remote Command Dispatch** (ALARM_ON, LOCATE_NOW, LOCK_DEVICE)\n"
        "- 🌊 **Time-bucketed GPS Route Reconstruction** for recovery pursuit\n"
        "- ☁️ **DigitalOcean Production Ready**"
    ),
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc"
)

# CORS Middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount API Routers
app.include_router(auth_router, prefix="/api/v1")
app.include_router(devices_router, prefix="/api/v1")
app.include_router(telemetry_router, prefix="/api/v1")
app.include_router(behavior_router, prefix="/api/v1")
app.include_router(ai_router, prefix="/api/v1")


@app.get("/", tags=["Health Check"])
async def root():
    return {
        "status": "online",
        "service": settings.PROJECT_NAME,
        "version": settings.VERSION,
        "environment": settings.ENVIRONMENT,
        "database": "Tiger Data (TimescaleDB / PostgreSQL)",
        "docs": "/docs"
    }


@app.get("/health", tags=["Health Check"])
async def health_check():
    return {
        "status": "healthy",
        "timestamp": "ok"
    }

