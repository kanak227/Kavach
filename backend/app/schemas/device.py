from datetime import datetime
from typing import Optional
from pydantic import BaseModel


class DevicePairRequest(BaseModel):
    device_uuid: str
    device_model: Optional[str] = "Android Device"


class DeviceStatusUpdate(BaseModel):
    is_armed: Optional[bool] = None
    battery_level: Optional[int] = None


class DeviceOut(BaseModel):
    id: int
    device_uuid: str
    device_model: str
    is_armed: bool
    battery_level: int
    last_seen: datetime

    class Config:
        from_attributes = True


class RemoteCommandCreate(BaseModel):
    command_type: str  # ALARM_ON, ALARM_OFF, LOCATE_NOW


class RemoteCommandOut(BaseModel):
    id: int
    device_uuid: str
    command_type: str
    is_executed: bool
    created_at: datetime

    class Config:
        from_attributes = True

