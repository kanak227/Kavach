import logging
from sqlalchemy import text
from app.db.session import engine, Base
from app.db.models import (
    User,
    Device,
    DeviceTelemetry,
    RemoteCommand,
    BehavioralObservation,
    DeviceRoutineProfile,
    BehavioralAnomaly,
)

logger = logging.getLogger(__name__)


async def init_database() -> None:
    """Initialize database tables and TimescaleDB Hypertables on Tiger Data."""
    try:
        async with engine.begin() as conn:
            # 1. Create standard SQLAlchemy tables
            await conn.run_sync(Base.metadata.create_all)
            logger.info("Core database tables verified / created.")

            # 2. Check if TimescaleDB extension is available (Tiger Data)
            try:
                # Check for Timescale extension
                await conn.execute(text("CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;"))
                
                # Convert device_telemetry into a Hypertable
                await conn.execute(
                    text("SELECT create_hypertable('device_telemetry', 'time', if_not_exists => TRUE);")
                )
                logger.info("Tiger Data Timescale Hypertable created for 'device_telemetry'.")

                # Convert device_behavior_observations into a Hypertable
                await conn.execute(
                    text("SELECT create_hypertable('device_behavior_observations', 'time', if_not_exists => TRUE);")
                )
                logger.info("Tiger Data Timescale Hypertable created for 'device_behavior_observations'.")

                # Configure TimescaleDB compression policies
                try:
                    await conn.execute(
                        text("""
                            ALTER TABLE device_telemetry SET (
                                timescaledb.compress,
                                timescaledb.compress_segmentby = 'device_uuid'
                            );
                        """)
                    )
                    await conn.execute(
                        text("SELECT add_compression_policy('device_telemetry', INTERVAL '7 days', if_not_exists => TRUE);")
                    )
                    logger.info("TimescaleDB 7-day columnar compression policy activated for telemetry.")
                except Exception as comp_err1:
                    logger.debug("Telemetry compression note: %s", comp_err1)

                try:
                    await conn.execute(
                        text("""
                            ALTER TABLE device_behavior_observations SET (
                                timescaledb.compress,
                                timescaledb.compress_segmentby = 'device_uuid'
                            );
                        """)
                    )
                    await conn.execute(
                        text("SELECT add_compression_policy('device_behavior_observations', INTERVAL '14 days', if_not_exists => TRUE);")
                    )
                    logger.info("TimescaleDB 14-day columnar compression policy activated for behavior observations.")
                except Exception as comp_err2:
                    logger.debug("Behavior compression note: %s", comp_err2)

            except Exception as e:
                # Normal fallback if running against SQLite or non-Timescale PostgreSQL
                logger.info("Standard relational tables initialized (Non-Timescale engine).")

    except Exception as e:
        logger.error("Database initialization failed: %s", e)
        raise e
