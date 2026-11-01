from datetime import datetime
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class IncidentAnalysisRequest(BaseModel):
    """Request payload to perform AI forensic analysis on a security incident."""
    device_uuid: str
    incident_id: Optional[int] = Field(default=None, description="Optional DB incident ID to query from Tiger Data")
    custom_telemetry: Optional[Dict[str, Any]] = Field(default=None, description="Direct telemetry overrides (speed, lat, lon, shock, battery)")
    intruder_detected_photo_desc: Optional[str] = Field(default=None, description="Visual description of the silent photo taken by front camera")


class IncidentAnalysisResponse(BaseModel):
    """Comprehensive AI forensic report produced by Google Gemini."""
    device_uuid: str
    threat_level: str = Field(description="LOW, MEDIUM, HIGH, CRITICAL")
    theft_classification: str = Field(description="e.g. Vehicle-Borne Rapid Pursuit, Public Transit Snatch, Dorm Theft")
    confidence_score: float = Field(ge=0.0, le=100.0)
    executive_summary: str = Field(description="Clear human explanation of what occurred")
    likely_escape_mode: str = Field(description="Foot, 2-Wheeler, Motorized Vehicle, Metro Transit")
    actionable_recovery_playbook: List[str] = Field(description="Immediate tactical steps for owner and recovery team")
    police_fir_draft: str = Field(description="Pre-composed official incident statement for CEIR / Police reporting")
    telegram_emergency_message: str = Field(description="Formatted markdown snippet ready to forward to Telegram/SMS")
    powered_by: str = "Google Gemini 1.5 Flash"


class CopilotQueryRequest(BaseModel):
    """User question directed to the Kavach AI Security Copilot."""
    device_uuid: str
    question: str = Field(description="User question regarding past anomalies, device status, or security settings")


class CopilotQueryResponse(BaseModel):
    """Response from the Kavach AI Security Copilot."""
    device_uuid: str
    question: str
    answer: str
    recommended_actions: List[str] = []
    powered_by: str = "Google Gemini 1.5 Flash"
