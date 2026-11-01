from datetime import datetime, timedelta, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import desc, select, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user
from app.db.models import Device, DeviceTelemetry, User
from app.db.session import get_db
from app.schemas.telemetry import RouteHistoryResponse, TelemetryOut, TelemetryPingRequest

router = APIRouter(prefix="/telemetry", tags=["Tiger Data Telemetry & Hypertables"])


@router.post("/ping", response_model=TelemetryOut)
async def record_telemetry_ping(
    payload: TelemetryPingRequest,
    db: AsyncSession = Depends(get_db),
):
    """
    Ingest a telemetry event from the device into the Tiger Data Timescale Hypertable.
    High-frequency stream (GPS, shock magnitude, battery, alarm status).
    """
    now = datetime.now(timezone.utc)

    # 1. Insert into TimescaleDB Hypertable
    telemetry = DeviceTelemetry(
        time=now,
        device_uuid=payload.device_uuid,
        latitude=payload.latitude,
        longitude=payload.longitude,
        accuracy_meters=payload.accuracy or 0.0,
        speed_mps=payload.speed or 0.0,
        shock_magnitude=payload.shock_magnitude or 0.0,
        battery_level=payload.battery_level or 100,
        alarm_active=payload.alarm_active,
        trigger_reason=payload.trigger_reason,
    )
    db.add(telemetry)

    # 2. Update device last_seen and battery in relational table
    dev_res = await db.execute(select(Device).where(Device.device_uuid == payload.device_uuid))
    device = dev_res.scalar_one_or_none()
    if device:
        device.last_seen = now
        if payload.battery_level is not None:
            device.battery_level = payload.battery_level
        if payload.alarm_active is not None:
            device.is_armed = payload.alarm_active or device.is_armed

    await db.commit()
    await db.refresh(telemetry)
    return telemetry


@router.get("/live/{device_uuid}", response_model=Optional[TelemetryOut])
async def get_live_device_telemetry(
    device_uuid: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Fetch the single latest location and sensor reading for a device."""
    result = await db.execute(
        select(DeviceTelemetry)
        .where(DeviceTelemetry.device_uuid == device_uuid)
        .order_by(desc(DeviceTelemetry.time))
        .limit(1)
    )
    return result.scalar_one_or_none()


@router.get("/history/{device_uuid}", response_model=RouteHistoryResponse)
async def get_route_history(
    device_uuid: str,
    hours: int = Query(default=24, ge=1, le=168, description="History window in hours"),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Retrieve breadcrumb path of all tracking points recorded in Tiger Data
    over the specified lookback window.
    """
    since_time = datetime.now(timezone.utc) - timedelta(hours=hours)

    result = await db.execute(
        select(DeviceTelemetry)
        .where(
            DeviceTelemetry.device_uuid == device_uuid,
            DeviceTelemetry.time >= since_time,
        )
        .order_by(DeviceTelemetry.time.asc())
    )
    points = result.scalars().all()

    return RouteHistoryResponse(
        device_uuid=device_uuid,
        total_points=len(points),
        points=points,
    )


@router.get("/analytics/{device_uuid}")
async def get_time_bucket_analytics(
    device_uuid: str,
    bucket_interval: str = Query(default="1 minute", description="Timescale time_bucket duration (e.g. '1 minute', '5 minutes')"),
    hours: int = Query(default=6, ge=1, le=48),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Demonstrates Tiger Data's native TimescaleDB hypertable aggregation.
    Aggregates thousands of GPS pings into smoothed intervals to compute escape speed and travel velocity.
    """
    try:
        query = text(f"""
            SELECT 
                time_bucket(:bucket, time) AS interval_start,
                AVG(latitude) AS avg_lat,
                AVG(longitude) AS avg_lng,
                MAX(speed_mps) AS max_speed,
                MAX(shock_magnitude) AS max_shock,
                MIN(battery_level) AS min_battery,
                COUNT(*) AS sample_count
            FROM device_telemetry
            WHERE device_uuid = :device_uuid
              AND time >= NOW() - (:hours || ' hours')::INTERVAL
            GROUP BY interval_start
            ORDER BY interval_start DESC
            LIMIT 100;
        """)
        res = await db.execute(query, {
            "bucket": bucket_interval,
            "device_uuid": device_uuid,
            "hours": str(hours)
        })
        rows = res.fetchall()
        
        return [
            {
                "interval_start": r[0],
                "avg_latitude": r[1],
                "avg_longitude": r[2],
                "max_speed_mps": r[3],
                "max_shock_magnitude": r[4],
                "battery_level": r[5],
                "sample_count": r[6]
            }
            for r in rows
        ]
    except Exception as e:
        # Fallback if non-TimescaleDB database
        return {"note": "TimescaleDB time_bucket function requires Tiger Data / Timescale extension", "error": str(e)}

