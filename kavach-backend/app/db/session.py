import logging
from typing import AsyncGenerator
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import declarative_base
from app.core.config import settings

logger = logging.getLogger(__name__)

Base = declarative_base()

# Connection engine
# For postgresql+asyncpg with Tiger Data, SSL is automatically required
# Normalize DATABASE_URL for asyncpg (asyncpg requires ?ssl=require, not ?sslmode=require)
db_url = settings.DATABASE_URL
if "sslmode=" in db_url:
    db_url = db_url.replace("sslmode=", "ssl=")

connect_args = {}
if "postgresql" in db_url:
    connect_args["timeout"] = 10
    connect_args["command_timeout"] = 15
elif "sqlite" in db_url:
    connect_args["check_same_thread"] = False

engine = create_async_engine(
    db_url,
    echo=settings.DEBUG,
    future=True,
    pool_pre_ping=True,
    connect_args=connect_args
)

AsyncSessionLocal = async_sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autocommit=False,
    autoflush=False
)


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    """Dependency that provides an asynchronous database session."""
    async with AsyncSessionLocal() as session:
        try:
            yield session
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()

