import math
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Tuple


def haversine_distance_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculate the great circle distance in meters between two coordinates."""
    R = 6371000.0  # Earth's radius in meters
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)

    a = (
        math.sin(delta_phi / 2.0) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2.0) ** 2
    )
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return R * c


def find_matching_safe_zone(
    lat: Optional[float],
    lon: Optional[float],
    safe_zones: List[Dict[str, Any]]
) -> Tuple[bool, Optional[Dict[str, Any]], float]:
    """
    Check if the coordinate falls within any learned safe zone.
    Returns: (is_inside, matched_zone, distance_to_nearest_meters)
    """
    if lat is None or lon is None or not safe_zones:
        return False, None, float("inf")

    nearest_dist = float("inf")
    nearest_zone = None

    for zone in safe_zones:
        z_lat = zone.get("lat")
        z_lon = zone.get("lng")
        radius = zone.get("radius_m", 200.0)

        if z_lat is not None and z_lon is not None:
            dist = haversine_distance_meters(lat, lon, z_lat, z_lon)
            if dist < nearest_dist:
                nearest_dist = dist
                nearest_zone = zone
            if dist <= radius:
                return True, zone, dist

    return False, nearest_zone, nearest_dist


def evaluate_behavioral_risk(
    obs_time: datetime,
    activity_type: str,
    is_charging: bool,
    battery_level: int,
    screen_on: bool,
    lat: Optional[float],
    lon: Optional[float],
    safe_zones: List[Dict[str, Any]],
    charging_window: Dict[str, Any],
    active_hours: Dict[str, Any],
) -> Dict[str, Any]:
    """
    Computes real-time anomaly score (0.0 to 100.0), identifies triggers,
    and determines the autonomous adaptation policy for the Kavach mobile shield.
    """
    current_hour = obs_time.hour
    is_inside_safe_zone, matched_zone, dist_to_safe = find_matching_safe_zone(lat, lon, safe_zones)

    sleep_start = charging_window.get("start_hour", 23)
    sleep_end = charging_window.get("end_hour", 7)

    # Determine if current time falls within user's habitual sleep / charging window
    if sleep_start > sleep_end:
        is_sleep_window = current_hour >= sleep_start or current_hour < sleep_end
    else:
        is_sleep_window = sleep_start <= current_hour < sleep_end

    risk_score = 0.0
    detected_anomalies: List[Dict[str, Any]] = []

    # 1. Location Anomaly Check
    if safe_zones and not is_inside_safe_zone:
        if dist_to_safe > 5000:  # > 5 km away from any safe place
            risk_score += 25.0
            detected_anomalies.append({
                "type": "UNFAMILIAR_LOCATION",
                "severity": "MEDIUM",
                "details": f"Distance to closest safe zone is {round(dist_to_safe / 1000.0, 1)} km"
            })
        else:
            risk_score += 10.0

    # 2. Habitual Sleep Window Anomalies
    if is_sleep_window:
        # User is usually asleep; check if moving
        if activity_type in ["IN_VEHICLE", "ON_BICYCLE", "RUNNING"]:
            severity = "CRITICAL" if not is_inside_safe_zone else "HIGH"
            penalty = 55.0 if not is_inside_safe_zone else 35.0
            risk_score += penalty
            detected_anomalies.append({
                "type": "SUSPICIOUS_SLEEP_WINDOW_TRANSIT",
                "severity": severity,
                "details": f"Rapid transit '{activity_type}' detected during habitual rest hour ({current_hour}:00)"
            })

        # Charger unexpectedly removed during night rest outside safe zone
        if not is_charging and not is_inside_safe_zone:
            risk_score += 35.0
            detected_anomalies.append({
                "type": "UNPLUGGED_AT_STRANGE_HOURS",
                "severity": "HIGH",
                "details": f"Device unplugged during habitual charging window in unfamiliar territory"
            })

    # 3. Safe Zone Relaxation
    if is_inside_safe_zone:
        # Being in Home or Work naturally attenuates risk
        zone_label = matched_zone.get("label", "Safe Zone") if matched_zone else "Safe Zone"
        risk_score = max(0.0, risk_score - 30.0)

    # Clamp risk score
    risk_score = min(100.0, max(0.0, round(risk_score, 1)))

    # 4. Determine Autonomous Adaptation Policy
    if risk_score >= 80.0:
        threat_mode = "AUTONOMOUS_LOCKDOWN"
        policy = {
            "mode": threat_mode,
            "motion_sensitivity": "MAXIMUM",
            "pocket_detection_sensitivity": "HAIR_TRIGGER",
            "alarm_grace_period_sec": 0,  # Instant alarm
            "ping_interval_sec": 15,       # Live high-frequency pursuit
            "stealth_camera_capture": True,
            "screen_lockdown_required": True,
            "display_warning_banner": True
        }
    elif risk_score >= 50.0:
        threat_mode = "HIGH_VIGILANCE"
        policy = {
            "mode": threat_mode,
            "motion_sensitivity": "HIGH",
            "pocket_detection_sensitivity": "HIGH",
            "alarm_grace_period_sec": 3,
            "ping_interval_sec": 45,
            "stealth_camera_capture": True,
            "screen_lockdown_required": False,
            "display_warning_banner": False
        }
    elif risk_score >= 20.0:
        threat_mode = "BALANCED"
        policy = {
            "mode": threat_mode,
            "motion_sensitivity": "NORMAL",
            "pocket_detection_sensitivity": "NORMAL",
            "alarm_grace_period_sec": 7,
            "ping_interval_sec": 180,
            "stealth_camera_capture": False,
            "screen_lockdown_required": False,
            "display_warning_banner": False
        }
    else:
        threat_mode = "SAFE_RELAXED"
        policy = {
            "mode": threat_mode,
            "motion_sensitivity": "LOW",
            "pocket_detection_sensitivity": "RELAXED",
            "alarm_grace_period_sec": 15,  # Relaxed grace period so user isn't annoyed
            "ping_interval_sec": 600,      # Power-saving 10-minute cadence
            "stealth_camera_capture": False,
            "screen_lockdown_required": False,
            "display_warning_banner": False
        }

    return {
        "risk_score": risk_score,
        "threat_mode": threat_mode,
        "is_safe_zone": is_inside_safe_zone,
        "matched_safe_zone": matched_zone.get("label") if matched_zone else None,
        "detected_anomalies": detected_anomalies,
        "policy": policy
    }


def cluster_safe_zones_from_history(
    observations: List[Dict[str, Any]],
    cluster_radius_meters: float = 180.0
) -> List[Dict[str, Any]]:
    """
    Identifies high-dwell clusters from historical observation coordinates
    and automatically labels the top dwell points as 'Primary Residence (Home)'
    and 'Primary Work / Study Campus'.
    """
    valid_points = [
        o for o in observations
        if o.get("latitude") is not None and o.get("longitude") is not None
    ]

    if not valid_points:
        return []

    clusters: List[Dict[str, Any]] = []

    for pt in valid_points:
        lat = pt["latitude"]
        lon = pt["longitude"]
        matched_cluster = None

        for c in clusters:
            dist = haversine_distance_meters(lat, lon, c["lat"], c["lng"])
            if dist <= cluster_radius_meters:
                matched_cluster = c
                break

        if matched_cluster:
            matched_cluster["count"] += 1
            # Running average coordinate
            count = matched_cluster["count"]
            matched_cluster["lat"] += (lat - matched_cluster["lat"]) / count
            matched_cluster["lng"] += (lon - matched_cluster["lng"]) / count
            if pt.get("is_charging"):
                matched_cluster["charging_count"] += 1
        else:
            clusters.append({
                "lat": lat,
                "lng": lon,
                "count": 1,
                "charging_count": 1 if pt.get("is_charging") else 0,
                "radius_m": cluster_radius_meters
            })

    # Sort clusters by frequency of dwell
    clusters.sort(key=lambda x: x["count"], reverse=True)

    # Take top 3 safe clusters
    top_clusters = clusters[:3]
    labeled_zones = []

    for idx, cluster in enumerate(top_clusters):
        if idx == 0:
            label = "Primary Residence (Home)"
        elif idx == 1:
            label = "Primary Campus / Office"
        else:
            label = f"Frequent Safe Zone #{idx + 1}"

        labeled_zones.append({
            "label": label,
            "lat": round(cluster["lat"], 6),
            "lng": round(cluster["lng"], 6),
            "radius_m": cluster["radius_m"],
            "dwell_observations": cluster["count"],
            "charging_frequency": cluster["charging_count"]
        })

    return labeled_zones

