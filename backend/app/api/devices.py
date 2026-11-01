from datetime import datetime, timezone
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_user
from app.db.models import Device, RemoteCommand, User
from app.db.session import get_db
from app.schemas.device import (
    DeviceOut,
    DevicePairRequest,
    DeviceStatusUpdate,
    RemoteCommandCreate,
    RemoteCommandOut,
)

router = APIRouter(prefix="/devices", tags=["Devices & Remote Commands"])


@router.post("/pair", response_model=DeviceOut)
async def pair_device(
    payload: DevicePairRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Pair a hardware device UUID with the logged-in user's account."""
    result = await db.execute(select(Device).where(Device.device_uuid == payload.device_uuid))
    device = result.scalar_one_or_none()

    if device:
        # Re-assign or update existing device
        device.user_id = current_user.id
        device.device_model = payload.device_model or device.device_model
        device.last_seen = datetime.now(timezone.utc)
    else:
        # Register new device
        device = Device(
            user_id=current_user.id,
            device_uuid=payload.device_uuid,
            device_model=payload.device_model or "Android Device",
            last_seen=datetime.now(timezone.utc),
        )
        db.add(device)

    await db.commit()
    await db.refresh(device)
    return device


@router.get("", response_model=List[DeviceOut])
async def list_my_devices(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Retrieve all devices registered under the authenticated user."""
    result = await db.execute(select(Device).where(Device.user_id == current_user.id))
    return result.scalars().all()


@router.patch("/{device_uuid}/status", response_model=DeviceOut)
async def update_device_status(
    device_uuid: str,
    payload: DeviceStatusUpdate,
    db: AsyncSession = Depends(get_db),
):
    """Update armed state and battery level from the mobile background service."""
    result = await db.execute(select(Device).where(Device.device_uuid == device_uuid))
    device = result.scalar_one_or_none()

    if not device:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Device not found",
        )

    if payload.is_armed is not None:
        device.is_armed = payload.is_armed
    if payload.battery_level is not None:
        device.battery_level = payload.battery_level
    device.last_seen = datetime.now(timezone.utc)

    await db.commit()
    await db.refresh(device)
    return device


@router.post("/{device_uuid}/command", response_model=RemoteCommandOut)
async def send_remote_command(
    device_uuid: str,
    payload: RemoteCommandCreate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Queue a remote command for the device (e.g. ALARM_ON, ALARM_OFF, LOCATE_NOW)."""
    # Verify device belongs to this user
    result = await db.execute(
        select(Device).where(Device.device_uuid == device_uuid, Device.user_id == current_user.id)
    )
    device = result.scalar_one_or_none()
    if not device:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Device not found under your account.",
        )

    cmd = RemoteCommand(
        device_uuid=device_uuid,
        command_type=payload.command_type.upper(),
    )
    db.add(cmd)
    await db.commit()
    await db.refresh(cmd)
    return cmd


@router.get("/{device_uuid}/commands/pending", response_model=List[RemoteCommandOut])
async def poll_pending_commands(
    device_uuid: str,
    db: AsyncSession = Depends(get_db),
):
    """Endpoint called by mobile background service to check and consume pending commands."""
    result = await db.execute(
        select(RemoteCommand)
        .where(RemoteCommand.device_uuid == device_uuid, RemoteCommand.is_executed == False)
        .order_by(RemoteCommand.created_at.asc())
    )
    commands = result.scalars().all()

    # Mark as executed so they aren't processed twice
    if commands:
        for cmd in commands:
            cmd.is_executed = True
        await db.commit()

    return commands

