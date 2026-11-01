from datetime import datetime
from typing import List, Optional
from pydantic import BaseModel


class TelemetryPingRequest(BaseModel):
    device_uuid: str
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    accuracy: Optional[float] = 0.0
    speed: Optional[float] = 0.0
    shock_magnitude: Optional[float] = 0.0
    battery_level: Optional[int] = 100
    alarm_active: bool = False
    trigger_reason: Optional[str] = None


class TelemetryOut(BaseModel):
    time: datetime
    device_uuid: str
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    accuracy_meters: float
    speed_mps: float
    shock_magnitude: float
    battery_level: int
    alarm_active: bool
    trigger_reason: Optional[str] = None

    class Config:
        from_attributes = True


class RouteHistoryResponse(BaseModel):
    device_uuid: str
    total_points: int
    points: List[TelemetryOut]

