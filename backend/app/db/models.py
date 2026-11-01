from datetime import datetime, timezone
from sqlalchemy import (
    Boolean,
    Column,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    JSON,
    String,
    Text,
)
from sqlalchemy.orm import relationship
from app.db.session import Base


class User(Base):
    """User account model."""
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    email = Column(String(255), unique=True, index=True, nullable=False)
    hashed_password = Column(String(255), nullable=False)
    full_name = Column(String(100), nullable=True)
    emergency_phone = Column(String(30), nullable=True)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))

    devices = relationship("Device", back_populates="user", cascade="all, delete-orphan")


class Device(Base):
    """Registered user devices (phones, tablets)."""
    __tablename__ = "devices"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    device_uuid = Column(String(100), unique=True, index=True, nullable=False)
    device_model = Column(String(100), default="Android Device")
    is_armed = Column(Boolean, default=False)
    battery_level = Column(Integer, default=100)
    last_seen = Column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))

    user = relationship("User", back_populates="devices")
    routine_profile = relationship("DeviceRoutineProfile", back_populates="device", uselist=False, cascade="all, delete-orphan")


class DeviceTelemetry(Base):
    """
    Time-series table converted into a TimescaleDB Hypertable on Tiger Data.
    Partitioned on `time` for high-throughput ingestion and 90%+ columnar compression.
    """
    __tablename__ = "device_telemetry"

    time = Column(DateTime(timezone=True), primary_key=True, default=lambda: datetime.now(timezone.utc), index=True)
    device_uuid = Column(String(100), primary_key=True, index=True, nullable=False)
    latitude = Column(Float, nullable=True)
    longitude = Column(Float, nullable=True)
    accuracy_meters = Column(Float, default=0.0)
    speed_mps = Column(Float, default=0.0)
    shock_magnitude = Column(Float, default=0.0)
    battery_level = Column(Integer, default=100)
    alarm_active = Column(Boolean, default=False)
    trigger_reason = Column(String(100), nullable=True)


class RemoteCommand(Base):
    """Queue for remote actions dispatched from web/user to the phone."""
    __tablename__ = "remote_commands"

    id = Column(Integer, primary_key=True, index=True)
    device_uuid = Column(String(100), index=True, nullable=False)
    command_type = Column(String(50), nullable=False)  # ALARM_ON, ALARM_OFF, LOCATE_NOW, LOCK_DEVICE
    is_executed = Column(Boolean, default=False)
    created_at = Column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))


# =========================================================================
# Behavioral Tracking & Adaptive Shield Models (Tiger Data Powered)
# =========================================================================

class BehavioralObservation(Base):
    """
    Continuous passive sensor snapshot ingested from the Android client.
    Converted into a TimescaleDB Hypertable on Tiger Data partitioned by `time`.
    """
    __tablename__ = "device_behavior_observations"

    time = Column(DateTime(timezone=True), primary_key=True, default=lambda: datetime.now(timezone.utc), index=True)
    device_uuid = Column(String(100), primary_key=True, index=True, nullable=False)
    
    # Motion & Activity Recognition
    activity_type = Column(String(50), default="STATIONARY")  # STATIONARY, WALKING, IN_VEHICLE, POCKET, TABLE
    activity_confidence = Column(Integer, default=100)        # 0 - 100%
    
    # Power & Charging Context
    is_charging = Column(Boolean, default=False)
    charging_type = Column(String(20), default="NONE")        # AC, USB, WIRELESS, NONE
    battery_level = Column(Integer, default=100)
    screen_on = Column(Boolean, default=False)
    
    # Location & Spatial Context
    latitude = Column(Float, nullable=True)
    longitude = Column(Float, nullable=True)
    accuracy_meters = Column(Float, default=0.0)
    is_safe_zone = Column(Boolean, default=False)
    
    # Environment & Ambience
    wifi_ssid_hash = Column(String(64), nullable=True)        # Hashed for privacy
    bluetooth_devices_count = Column(Integer, default=0)
    ambient_light_lux = Column(Float, default=0.0)
    step_count = Column(Integer, default=0)
    
    # Real-time computed anomaly assessment
    anomaly_score = Column(Float, default=0.0)                # 0.0 to 100.0


class DeviceRoutineProfile(Base):
    """
    Learned routine baselines computed across Tiger Data historical telemetry.
    Stores habitual safe zones, typical active/sleep hours, and baseline confidence.
    """
    __tablename__ = "device_routine_profiles"

    id = Column(Integer, primary_key=True, index=True)
    device_uuid = Column(String(100), ForeignKey("devices.device_uuid", ondelete="CASCADE"), unique=True, index=True, nullable=False)
    
    # Learned Safe Zones: e.g. [{"label": "Home", "lat": 28.5, "lng": 77.2, "radius_m": 150, "dwell_hours": 11.2}]
    learned_safe_zones = Column(JSON, default=list)
    
    # Learned Charging Window: e.g. {"start_hour": 23, "end_hour": 7, "consistency_pct": 94}
    habitual_charging_window = Column(JSON, default=dict)
    
    # Typical Active Hours: e.g. {"active_hours": [7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22]}
    typical_active_hours = Column(JSON, default=dict)
    
    # Current Autonomous Stance
    current_threat_mode = Column(String(50), default="BALANCED")  # SAFE_RELAXED, BALANCED, HIGH_VIGILANCE, LOCKDOWN
    current_risk_score = Column(Float, default=0.0)               # 0.0 to 100.0
    
    last_evaluated_at = Column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))
    updated_at = Column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc))

    device = relationship("Device", back_populates="routine_profile")


class BehavioralAnomaly(Base):
    """
    Audit log of flagged behavioral anomalies and the autonomous actions triggered.
    """
    __tablename__ = "behavioral_anomalies"

    id = Column(Integer, primary_key=True, index=True)
    time = Column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), index=True)
    device_uuid = Column(String(100), index=True, nullable=False)
    anomaly_type = Column(String(100), nullable=False)
    severity = Column(String(20), default="MEDIUM")               # LOW, MEDIUM, HIGH, CRITICAL
    anomaly_score = Column(Float, default=50.0)
    details = Column(JSON, default=dict)
    action_taken = Column(String(100), default="ADAPT_HIGH_VIGILANCE")
