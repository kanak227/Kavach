import asyncio
from datetime import datetime, timedelta, timezone
import logging
from sqlalchemy import select, text

from app.core.security import get_password_hash
from app.db.init_db import init_database
from app.db.models import (
    BehavioralAnomaly,
    BehavioralObservation,
    Device,
    DeviceRoutineProfile,
    DeviceTelemetry,
    RemoteCommand,
    User,
)
from app.db.session import AsyncSessionLocal

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)


async def seed_data():
    logger.info("Initializing database schema and Tiger Data Timescale Hypertables...")
    await init_database()
    logger.info("Database schemas and Hypertables ready.")

    async with AsyncSessionLocal() as db:
        # 1. Create or fetch Demo User
        user_email = "kanakverma325@gmail.com"
        stmt = select(User).where(User.email == user_email)
        res = await db.execute(stmt)
        user = res.scalars().first()

        if not user:
            user = User(
                email=user_email,
                hashed_password=get_password_hash("Kavach@2026"),
                full_name="Kanak Verma",
                emergency_phone="+919876543210",
                is_active=True
            )
            db.add(user)
            await db.flush()
            logger.info("Created Demo User: %s (Password: Kavach@2026)", user_email)
        else:
            logger.info("Existing Demo User found: %s", user_email)

        # 2. Create Paired Devices
        devices_data = [
            {
                "uuid": "kavach-oneplus-11-demo",
                "model": "OnePlus 11 5G (Kanak's Primary)",
                "battery": 87,
                "is_armed": True
            },
            {
                "uuid": "kavach-pixel-8-demo",
                "model": "Google Pixel 8 Pro (Test Shield)",
                "battery": 64,
                "is_armed": False
            }
        ]

        for d_info in devices_data:
            d_stmt = select(Device).where(Device.device_uuid == d_info["uuid"])
            d_res = await db.execute(d_stmt)
            dev = d_res.scalars().first()
            if not dev:
                dev = Device(
                    user_id=user.id,
                    device_uuid=d_info["uuid"],
                    device_model=d_info["model"],
                    is_armed=d_info["is_armed"],
                    battery_level=d_info["battery"],
                    last_seen=datetime.now(timezone.utc)
                )
                db.add(dev)
                await db.flush()
                logger.info("Paired Device: %s (%s)", d_info["uuid"], d_info["model"])

        # 3. Seed Routine Profile for Primary Device
        primary_uuid = "kavach-oneplus-11-demo"
        prof_stmt = select(DeviceRoutineProfile).where(DeviceRoutineProfile.device_uuid == primary_uuid)
        prof_res = await db.execute(prof_stmt)
        profile = prof_res.scalars().first()

        safe_zones = [
            {
                "label": "Primary Residence (Home)",
                "lat": 28.6139,
                "lng": 77.2090,
                "radius_m": 200,
                "dwell_observations": 140,
                "charging_frequency": 95
            },
            {
                "label": "Primary Campus / Office",
                "lat": 28.5450,
                "lng": 77.1926,
                "radius_m": 250,
                "dwell_observations": 85,
                "charging_frequency": 30
            }
        ]

        if not profile:
            profile = DeviceRoutineProfile(
                device_uuid=primary_uuid,
                learned_safe_zones=safe_zones,
                habitual_charging_window={"start_hour": 23, "end_hour": 7, "consistency_pct": 94},
                typical_active_hours={"active_hours": list(range(7, 23))},
                current_threat_mode="SAFE_RELAXED",
                current_risk_score=0.0,
                last_evaluated_at=datetime.now(timezone.utc)
            )
            db.add(profile)
            logger.info("Seeded Learned Routine Profile for %s", primary_uuid)

        # 4. Seed Time-Series Telemetry (Tiger Data Hypertable)
        now = datetime.now(timezone.utc)
        base_lat, base_lng = 28.6139, 77.2090

        logger.info("Seeding time-series telemetry breadcrumbs into Tiger Data hypertable...")
        for i in range(15):
            t_point = now - timedelta(minutes=(15 - i) * 10)
            offset = i * 0.0012
            telem = DeviceTelemetry(
                time=t_point,
                device_uuid=primary_uuid,
                latitude=base_lat + offset,
                longitude=base_lng + (offset * 0.8),
                accuracy_meters=6.0,
                speed_mps=14.5 if i > 8 else 0.0,
                shock_magnitude=2.1 if i == 12 else 0.4,
                battery_level=max(15, 95 - i),
                alarm_active=(i == 12),
                trigger_reason="THEFT_SPEED_ANOMALY" if i == 12 else None
            )
            db.add(telem)

        # 5. Seed Behavioral Observations (Tiger Data Hypertable)
        logger.info("Seeding behavioral observations into Tiger Data hypertable...")
        for i in range(10):
            t_obs = now - timedelta(hours=i * 2)
            is_night = t_obs.hour >= 23 or t_obs.hour < 7
            obs = BehavioralObservation(
                time=t_obs,
                device_uuid=primary_uuid,
                activity_type="STATIONARY" if is_night else "WALKING",
                activity_confidence=98 if is_night else 88,
                is_charging=is_night,
                charging_type="AC" if is_night else "NONE",
                battery_level=98 if is_night else 65,
                screen_on=not is_night,
                latitude=base_lat if is_night else 28.5450,
                longitude=base_lng if is_night else 77.1926,
                accuracy_meters=8.0,
                is_safe_zone=True,
                ambient_light_lux=0.2 if is_night else 450.0,
                step_count=120 if is_night else 3400 + (i * 400),
                anomaly_score=0.0 if is_night else 15.0
            )
            db.add(obs)

        # 6. Seed a Sample Flagged Anomaly
        anom = BehavioralAnomaly(
            time=now - timedelta(hours=5),
            device_uuid=primary_uuid,
            anomaly_type="SUSPICIOUS_SLEEP_WINDOW_TRANSIT",
            severity="CRITICAL",
            anomaly_score=92.5,
            details={
                "trigger": "Transit detected at 03:15 AM outside safe zone",
                "expected_state": "STATIONARY (Home)",
                "actual_state": "IN_VEHICLE (48 km/h)",
                "distance_to_nearest_safe_zone_km": 11.4
            },
            action_taken="AUTONOMOUS_LOCKDOWN"
        )
        db.add(anom)

        # 7. Seed Remote Commands Queue
        cmd = RemoteCommand(
            device_uuid=primary_uuid,
            command_type="LOCATE_NOW",
            is_executed=False,
            created_at=now - timedelta(minutes=5)
        )
        db.add(cmd)

        await db.commit()
        logger.info("All seed data successfully committed to Tiger Data!")


if __name__ == "__main__":
    asyncio.run(seed_data())

