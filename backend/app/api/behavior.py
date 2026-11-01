import logging
from datetime import datetime, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import desc, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user
from app.db.models import (
    BehavioralAnomaly,
    BehavioralObservation,
    Device,
    DeviceRoutineProfile,
    User,
)
from app.db.session import get_db
from app.schemas.behavior import (
    AdaptationPolicyResponse,
    AnomalyIncidentResponse,
    BehaviorObservationCreate,
    RoutineProfileResponse,
)
from app.services.behavior_engine import (
    cluster_safe_zones_from_history,
    evaluate_behavioral_risk,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/behavior", tags=["Adaptive Behavioral Shield"])


async def get_or_create_routine_profile(
    db: AsyncSession,
    device_uuid: str
) -> DeviceRoutineProfile:
    """Helper to fetch or initialize a routine profile for a device."""
    stmt = select(DeviceRoutineProfile).where(DeviceRoutineProfile.device_uuid == device_uuid)
    result = await db.execute(stmt)
    profile = result.scalars().first()

    if not profile:
        profile = DeviceRoutineProfile(
            device_uuid=device_uuid,
            learned_safe_zones=[],
            habitual_charging_window={"start_hour": 23, "end_hour": 7, "consistency_pct": 0},
            typical_active_hours={"active_hours": list(range(7, 23))},
            current_threat_mode="BALANCED",
            current_risk_score=0.0
        )
        db.add(profile)
        await db.flush()

    return profile


@router.post(
    "/observe",
    response_model=AdaptationPolicyResponse,
    summary="Ingest passive behavioral observation and return dynamic adaptation policy"
)
async def ingest_behavior_observation(
    payload: BehaviorObservationCreate,
    db: AsyncSession = Depends(get_db)
):
    """
    Ingests passive sensor state into Tiger Data's Timescale Hypertable,
    evaluates behavioral anomalies against the user's learned routine,
    and returns immediate policy adjustments (e.g., sensitivity, ping rate, lockdown).
    """
    obs_time = payload.timestamp or datetime.now(timezone.utc)

    # 1. Fetch or initialize the learned profile
    profile = await get_or_create_routine_profile(db, payload.device_uuid)

    safe_zones = profile.learned_safe_zones or []
    charging_window = profile.habitual_charging_window or {}
    active_hours = profile.typical_active_hours or {}

    # 2. Evaluate risk using the Behavioral Engine
    eval_result = evaluate_behavioral_risk(
        obs_time=obs_time,
        activity_type=payload.activity_type,
        is_charging=payload.is_charging,
        battery_level=payload.battery_level,
        screen_on=payload.screen_on,
        lat=payload.latitude,
        lon=payload.longitude,
        safe_zones=safe_zones,
        charging_window=charging_window,
        active_hours=active_hours,
    )

    risk_score = eval_result["risk_score"]
    threat_mode = eval_result["threat_mode"]
    policy_dict = eval_result["policy"]
    detected_anomalies = eval_result["detected_anomalies"]

    # 3. Save observation into Tiger Data Hypertable
    observation = BehavioralObservation(
        time=obs_time,
        device_uuid=payload.device_uuid,
        activity_type=payload.activity_type,
        activity_confidence=payload.activity_confidence,
        is_charging=payload.is_charging,
        charging_type=payload.charging_type,
        battery_level=payload.battery_level,
        screen_on=payload.screen_on,
        latitude=payload.latitude,
        longitude=payload.longitude,
        accuracy_meters=payload.accuracy_meters,
        is_safe_zone=eval_result["is_safe_zone"],
        wifi_ssid_hash=payload.wifi_ssid_hash,
        bluetooth_devices_count=payload.bluetooth_devices_count,
        ambient_light_lux=payload.ambient_light_lux,
        step_count=payload.step_count,
        anomaly_score=risk_score
    )
    db.add(observation)

    # 4. Log anomalies if risk threshold is elevated
    if risk_score >= 50.0 and detected_anomalies:
        for anom in detected_anomalies:
            anomaly_entry = BehavioralAnomaly(
                time=obs_time,
                device_uuid=payload.device_uuid,
                anomaly_type=anom["type"],
                severity=anom["severity"],
                anomaly_score=risk_score,
                details={"details": anom["details"], "lat": payload.latitude, "lon": payload.longitude},
                action_taken=threat_mode
            )
            db.add(anomaly_entry)

    # 5. Update profile state
    profile.current_threat_mode = threat_mode
    profile.current_risk_score = risk_score
    profile.last_evaluated_at = obs_time

    await db.commit()

    return AdaptationPolicyResponse(
        device_uuid=payload.device_uuid,
        current_threat_mode=threat_mode,
        current_risk_score=risk_score,
        is_safe_zone=eval_result["is_safe_zone"],
        matched_safe_zone=eval_result["matched_safe_zone"],
        motion_sensitivity=policy_dict["motion_sensitivity"],
        pocket_detection_sensitivity=policy_dict["pocket_detection_sensitivity"],
        alarm_grace_period_sec=policy_dict["alarm_grace_period_sec"],
        ping_interval_sec=policy_dict["ping_interval_sec"],
        stealth_camera_capture=policy_dict["stealth_camera_capture"],
        screen_lockdown_required=policy_dict["screen_lockdown_required"],
        display_warning_banner=policy_dict["display_warning_banner"],
        active_anomalies=detected_anomalies
    )


@router.get(
    "/{device_uuid}/policy",
    response_model=AdaptationPolicyResponse,
    summary="Query current autonomous defensive policy for a device"
)
async def get_adaptation_policy(
    device_uuid: str,
    db: AsyncSession = Depends(get_db)
):
    """Returns the current adapted defensive settings the device should enforce."""
    profile = await get_or_create_routine_profile(db, device_uuid)

    eval_result = evaluate_behavioral_risk(
        obs_time=datetime.now(timezone.utc),
        activity_type="STATIONARY",
        is_charging=True,
        battery_level=100,
        screen_on=False,
        lat=None,
        lon=None,
        safe_zones=profile.learned_safe_zones or [],
        charging_window=profile.habitual_charging_window or {},
        active_hours=profile.typical_active_hours or {},
    )
    policy_dict = eval_result["policy"]

    return AdaptationPolicyResponse(
        device_uuid=device_uuid,
        current_threat_mode=profile.current_threat_mode,
        current_risk_score=profile.current_risk_score,
        is_safe_zone=False,
        matched_safe_zone=None,
        motion_sensitivity=policy_dict["motion_sensitivity"],
        pocket_detection_sensitivity=policy_dict["pocket_detection_sensitivity"],
        alarm_grace_period_sec=policy_dict["alarm_grace_period_sec"],
        ping_interval_sec=policy_dict["ping_interval_sec"],
        stealth_camera_capture=policy_dict["stealth_camera_capture"],
        screen_lockdown_required=policy_dict["screen_lockdown_required"],
        display_warning_banner=policy_dict["display_warning_banner"],
        active_anomalies=[]
    )


@router.get(
    "/{device_uuid}/routine",
    response_model=RoutineProfileResponse,
    summary="Inspect learned behavioral profile (safe zones, sleep windows)"
)
async def get_device_routine(
    device_uuid: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """Retrieve learned behavioral profile and habitual safe zones."""
    # Verify device ownership
    dev_stmt = select(Device).where(Device.device_uuid == device_uuid, Device.user_id == current_user.id)
    dev_res = await db.execute(dev_stmt)
    if not dev_res.scalars().first():
        raise HTTPException(status_code=404, detail="Device not found or not paired with this user")

    profile = await get_or_create_routine_profile(db, device_uuid)

    return RoutineProfileResponse(
        device_uuid=profile.device_uuid,
        learned_safe_zones=profile.learned_safe_zones or [],
        habitual_charging_window=profile.habitual_charging_window or {},
        typical_active_hours=profile.typical_active_hours or {},
        current_threat_mode=profile.current_threat_mode,
        current_risk_score=profile.current_risk_score,
        last_evaluated_at=profile.last_evaluated_at,
        updated_at=profile.updated_at
    )


@router.post(
    "/{device_uuid}/recalculate",
    response_model=RoutineProfileResponse,
    summary="Trigger Tiger Data spatial clustering to learn safe zones from history"
)
async def recalculate_routine(
    device_uuid: str,
    limit: int = Query(500, description="Number of past observations to cluster"),
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Queries past behavioral observations from Tiger Data hypertable,
    clusters dwell coordinates into safe zones (Home, Campus),
    and updates the learned routine profile.
    """
    dev_stmt = select(Device).where(Device.device_uuid == device_uuid, Device.user_id == current_user.id)
    dev_res = await db.execute(dev_stmt)
    if not dev_res.scalars().first():
        raise HTTPException(status_code=404, detail="Device not found")

    # Fetch recent observations
    obs_stmt = (
        select(BehavioralObservation)
        .where(BehavioralObservation.device_uuid == device_uuid)
        .order_by(desc(BehavioralObservation.time))
        .limit(limit)
    )
    obs_res = await db.execute(obs_stmt)
    rows = obs_res.scalars().all()

    obs_dicts = [
        {
            "latitude": r.latitude,
            "longitude": r.longitude,
            "is_charging": r.is_charging,
            "activity_type": r.activity_type
        }
        for r in rows
    ]

    # Run clustering
    discovered_safe_zones = cluster_safe_zones_from_history(obs_dicts)

    profile = await get_or_create_routine_profile(db, device_uuid)
    if discovered_safe_zones:
        profile.learned_safe_zones = discovered_safe_zones

    profile.updated_at = datetime.now(timezone.utc)
    await db.commit()
    await db.refresh(profile)

    return RoutineProfileResponse(
        device_uuid=profile.device_uuid,
        learned_safe_zones=profile.learned_safe_zones or [],
        habitual_charging_window=profile.habitual_charging_window or {},
        typical_active_hours=profile.typical_active_hours or {},
        current_threat_mode=profile.current_threat_mode,
        current_risk_score=profile.current_risk_score,
        last_evaluated_at=profile.last_evaluated_at,
        updated_at=profile.updated_at
    )


@router.get(
    "/{device_uuid}/anomalies",
    response_model=List[AnomalyIncidentResponse],
    summary="List recent behavioral anomalies flagged by the system"
)
async def get_recent_anomalies(
    device_uuid: str,
    limit: int = Query(20, ge=1, le=100),
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """Audit log of recent security anomalies detected for this device."""
    dev_stmt = select(Device).where(Device.device_uuid == device_uuid, Device.user_id == current_user.id)
    dev_res = await db.execute(dev_stmt)
    if not dev_res.scalars().first():
        raise HTTPException(status_code=404, detail="Device not found")

    stmt = (
        select(BehavioralAnomaly)
        .where(BehavioralAnomaly.device_uuid == device_uuid)
        .order_by(desc(BehavioralAnomaly.time))
        .limit(limit)
    )
    res = await db.execute(stmt)
    anomalies = res.scalars().all()

    return [
        AnomalyIncidentResponse(
            id=a.id,
            time=a.time,
            device_uuid=a.device_uuid,
            anomaly_type=a.anomaly_type,
            severity=a.severity,
            anomaly_score=a.anomaly_score,
            details=a.details or {},
            action_taken=a.action_taken
        )
        for a in anomalies
    ]

