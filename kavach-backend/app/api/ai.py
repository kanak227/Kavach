import logging
from datetime import datetime, timezone
from typing import Any, Dict
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import desc, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import (
    BehavioralAnomaly,
    Device,
    DeviceRoutineProfile,
    DeviceTelemetry,
)
from app.db.session import get_db
from app.schemas.ai import (
    CopilotQueryRequest,
    CopilotQueryResponse,
    IncidentAnalysisRequest,
    IncidentAnalysisResponse,
)
from app.services.ai_forensics import (
    ask_gemini_copilot,
    generate_gemini_threat_brief,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ai", tags=["Google Gemini AI Forensics"])


@router.post(
    "/analyze-incident",
    response_model=IncidentAnalysisResponse,
    summary="Generate Gemini 1.5 Forensic Threat Brief from Tiger Data breach telemetry"
)
async def analyze_incident(
    payload: IncidentAnalysisRequest,
    db: AsyncSession = Depends(get_db)
):
    """
    Ingests breach sensor metrics (speed, coordinates, shock impact, safe-zone delta)
    and produces an AI-powered forensic incident report, police draft, and recovery playbook.
    """
    incident_context: Dict[str, Any] = {
        "device_uuid": payload.device_uuid,
        "time": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC"),
        "trigger_reason": "UNAUTHORIZED_POCKET_SNATCH",
        "speed_mps": 14.5,
        "shock_magnitude": 8.7,
        "battery_level": 78,
        "latitude": 28.6295,
        "longitude": 77.2215,
        "distance_to_safe_zone_km": 8.2
    }

    # Fetch latest telemetry from Tiger Data hypertable if available
    telem_stmt = (
        select(DeviceTelemetry)
        .where(DeviceTelemetry.device_uuid == payload.device_uuid)
        .order_by(desc(DeviceTelemetry.time))
        .limit(1)
    )
    telem_res = await db.execute(telem_stmt)
    latest_telem = telem_res.scalars().first()

    if latest_telem:
        incident_context["speed_mps"] = latest_telem.speed_mps
        incident_context["shock_magnitude"] = latest_telem.shock_magnitude
        incident_context["battery_level"] = latest_telem.battery_level
        incident_context["latitude"] = latest_telem.latitude
        incident_context["longitude"] = latest_telem.longitude
        incident_context["trigger_reason"] = latest_telem.trigger_reason or "MOTION_SHOCK_BREACH"
        incident_context["time"] = latest_telem.time.strftime("%Y-%m-%d %H:%M:%S UTC")

    # Fetch latest anomaly details
    anom_stmt = (
        select(BehavioralAnomaly)
        .where(BehavioralAnomaly.device_uuid == payload.device_uuid)
        .order_by(desc(BehavioralAnomaly.time))
        .limit(1)
    )
    anom_res = await db.execute(anom_stmt)
    latest_anom = anom_res.scalars().first()
    if latest_anom:
        incident_context["anomaly_type"] = latest_anom.anomaly_type
        incident_context["anomaly_score"] = latest_anom.anomaly_score
        if latest_anom.details:
            incident_context.update(latest_anom.details)

    # Apply any custom overrides from request
    if payload.custom_telemetry:
        incident_context.update(payload.custom_telemetry)

    if payload.intruder_detected_photo_desc:
        incident_context["front_camera_photo_analysis"] = payload.intruder_detected_photo_desc

    # Run Gemini AI Forensics
    analysis = await generate_gemini_threat_brief(incident_context)

    return IncidentAnalysisResponse(
        device_uuid=payload.device_uuid,
        threat_level=analysis.get("threat_level", "HIGH"),
        theft_classification=analysis.get("theft_classification", "Transit Theft"),
        confidence_score=analysis.get("confidence_score", 90.0),
        executive_summary=analysis.get("executive_summary", "Security breach detected."),
        likely_escape_mode=analysis.get("likely_escape_mode", "Motorized Vehicle"),
        actionable_recovery_playbook=analysis.get("actionable_recovery_playbook", []),
        police_fir_draft=analysis.get("police_fir_draft", ""),
        telegram_emergency_message=analysis.get("telegram_emergency_message", ""),
        powered_by=analysis.get("powered_by", "Google Gemini 1.5 Flash")
    )


@router.post(
    "/ask-copilot",
    response_model=CopilotQueryResponse,
    summary="Ask Kavach Security Copilot powered by Google Gemini"
)
async def ask_copilot(
    payload: CopilotQueryRequest,
    db: AsyncSession = Depends(get_db)
):
    """
    Conversational security assistant. Inquires about past device anomalies,
    reasoning behind threat mode adaptations, and mobile defense best practices.
    """
    # Build context from device profile
    prof_stmt = select(DeviceRoutineProfile).where(DeviceRoutineProfile.device_uuid == payload.device_uuid)
    prof_res = await db.execute(prof_stmt)
    profile = prof_res.scalars().first()

    context = {
        "device_uuid": payload.device_uuid,
        "current_threat_mode": profile.current_threat_mode if profile else "BALANCED",
        "current_risk_score": profile.current_risk_score if profile else 0.0,
        "safe_zones_count": len(profile.learned_safe_zones) if profile and profile.learned_safe_zones else 0,
        "habitual_charging_window": profile.habitual_charging_window if profile else {}
    }

    ai_result = await ask_gemini_copilot(payload.question, context)

    return CopilotQueryResponse(
        device_uuid=payload.device_uuid,
        question=payload.question,
        answer=ai_result["answer"],
        recommended_actions=ai_result.get("recommended_actions", []),
        powered_by=ai_result.get("powered_by", "Google Gemini 1.5 Flash")
    )
