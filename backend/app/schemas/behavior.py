from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field


class BehaviorObservationCreate(BaseModel):
    """Passive behavioral telemetry point sent by the Android client."""
    device_uuid: str
    activity_type: str = Field(default="STATIONARY", description="STATIONARY, WALKING, IN_VEHICLE, ON_BICYCLE, POCKET, TABLE")
    activity_confidence: int = Field(default=100, ge=0, le=100)
    is_charging: bool = False
    charging_type: str = Field(default="NONE", description="AC, USB, WIRELESS, NONE")
    battery_level: int = Field(default=100, ge=0, le=100)
    screen_on: bool = False
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    accuracy_meters: float = 0.0
    wifi_ssid_hash: Optional[str] = None
    bluetooth_devices_count: int = 0
    ambient_light_lux: float = 0.0
    step_count: int = 0
    timestamp: Optional[datetime] = None


class BehaviorObservationBatch(BaseModel):
    """Batch payload of multiple behavioral points to minimize network calls."""
    device_uuid: str
    observations: List[BehaviorObservationCreate]


class AdaptationPolicyResponse(BaseModel):
    """Autonomous defensive configuration instructing the Android app how to adapt."""
    device_uuid: str
    current_threat_mode: str = Field(description="SAFE_RELAXED, BALANCED, HIGH_VIGILANCE, AUTONOMOUS_LOCKDOWN")
    current_risk_score: float = Field(ge=0.0, le=100.0)
    is_safe_zone: bool
    matched_safe_zone: Optional[str] = None
    motion_sensitivity: str = Field(description="LOW, NORMAL, HIGH, MAXIMUM")
    pocket_detection_sensitivity: str = Field(description="RELAXED, NORMAL, HIGH, HAIR_TRIGGER")
    alarm_grace_period_sec: int
    ping_interval_sec: int
    stealth_camera_capture: bool
    screen_lockdown_required: bool
    display_warning_banner: bool
    active_anomalies: List[Dict[str, Any]] = []


class RoutineProfileResponse(BaseModel):
    """Overview of learned user routines and baselines."""
    device_uuid: str
    learned_safe_zones: List[Dict[str, Any]]
    habitual_charging_window: Dict[str, Any]
    typical_active_hours: Dict[str, Any]
    current_threat_mode: str
    current_risk_score: float
    last_evaluated_at: Optional[datetime]
    updated_at: Optional[datetime]


class AnomalyIncidentResponse(BaseModel):
    """Historical record of an anomaly flagged by the behavioral engine."""
    id: int
    time: datetime
    device_uuid: str
    anomaly_type: str
    severity: str
    anomaly_score: float
    details: Dict[str, Any]
    action_taken: str

