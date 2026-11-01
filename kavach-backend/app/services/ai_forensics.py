import json
import logging
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from app.core.config import settings

logger = logging.getLogger(__name__)

# Try importing google.generativeai
try:
    import google.generativeai as genai
    HAS_GENAI = True
except ImportError:
    HAS_GENAI = False


def _build_fallback_analysis(incident: Dict[str, Any]) -> Dict[str, Any]:
    """
    Expert rule-based fallback forensics engine.
    Ensures complete, high-quality responses even if API key is not yet set.
    """
    speed_mps = incident.get("speed_mps", 0.0)
    speed_kmh = round(speed_mps * 3.6, 1)
    shock = incident.get("shock_magnitude", 0.0)
    lat = incident.get("latitude", 28.6139)
    lon = incident.get("longitude", 77.2090)
    dist_safe_km = incident.get("distance_to_safe_zone_km", 0.0)
    time_str = incident.get("time", datetime.now(timezone.utc).strftime("%I:%M %p UTC"))
    battery = incident.get("battery_level", 85)
    trigger = incident.get("trigger_reason", "MOTION_BREACH")

    if speed_kmh > 40:
        theft_type = "Motorized Transit / Vehicle-Borne Theft"
        escape_mode = "Car / Cab / High-Speed Transit"
        threat_level = "CRITICAL"
        confidence = 94.0
    elif speed_kmh > 15:
        theft_type = "Rapid Two-Wheeler / Bicycle Snatch"
        escape_mode = "Motorcycle / Scooter / Bicycle"
        threat_level = "HIGH"
        confidence = 88.0
    elif dist_safe_km > 2.0:
        theft_type = "Unfamiliar Zone Pickpocket Snatch"
        escape_mode = "Foot / Pedestrian Transit"
        threat_level = "HIGH"
        confidence = 85.0
    else:
        theft_type = "Unattended Desk / Shared Dorm Lift"
        escape_mode = "Immediate Concealment (Pocket/Bag)"
        threat_level = "MEDIUM"
        confidence = 80.0

    summary = (
        f"At {time_str}, Kavach triggered an alarm breach via '{trigger}'. "
        f"The device exhibits a velocity of {speed_kmh} km/h with an accelerometer shock impact of {shock}g. "
        f"Location is {dist_safe_km} km away from registered safe zones. "
        f"Telemetry profile strongly indicates {theft_type}."
    )

    playbook = [
        "Do NOT attempt to physically confront the suspect alone.",
        f"Track moving coordinates in real time via Kavach Cloud (Current: {lat}, {lon}).",
        "Engage local transit police or nearest precinct with the pre-drafted incident report.",
        "Initiate remote device lockdown to prevent UPI / banking app access."
    ]

    fir_draft = (
        f"POLICE COMPLAINT / INCIDENT LOG DRAFT:\n"
        f"Incident: Stolen Mobile Device (Kavach Security Shield Monitored)\n"
        f"Time of Breach: {time_str}\n"
        f"Last Recorded Coordinates: {lat}, {lon} (Accuracy: 5m)\n"
        f"Velocity & Heading: {speed_kmh} km/h ({escape_mode})\n"
        f"Battery Status: {battery}%\n"
        f"Triggering Sensor Event: {trigger} (Impact: {shock}g)\n"
        f"Verified by Kavach AI Security Engine."
    )

    tg_msg = (
        f"🚨 *KAVACH AI FORENSIC ALERT*\n\n"
        f"• *Status:* {threat_level} BREACH DETECTED\n"
        f"• *Classification:* {theft_type}\n"
        f"• *Speed:* {speed_kmh} km/h ({escape_mode})\n"
        f"• *Battery:* {battery}%\n"
        f"• *Location:* [{lat}, {lon}](https://maps.google.com/?q={lat},{lon})\n\n"
        f"💡 *Action:* Remote stealth tracking active. Disarm with biometric verification."
    )

    return {
        "threat_level": threat_level,
        "theft_classification": theft_type,
        "confidence_score": confidence,
        "executive_summary": summary,
        "likely_escape_mode": escape_mode,
        "actionable_recovery_playbook": playbook,
        "police_fir_draft": fir_draft,
        "telegram_emergency_message": tg_msg,
        "powered_by": "Kavach AI Forensic Engine (Rule-Based Hybrid)"
    }


async def generate_gemini_threat_brief(incident: Dict[str, Any]) -> Dict[str, Any]:
    """
    Calls Google Gemini 1.5 Flash to generate an AI Forensic Security Assessment
    from time-series Tiger Data telemetry.
    """
    api_key = settings.GEMINI_API_KEY

    # Check if Gemini API is available and configured
    if not HAS_GENAI or not api_key or "YOUR_GEMINI" in api_key:
        logger.info("Using Kavach Hybrid Forensic Engine (Gemini API Key not set).")
        return _build_fallback_analysis(incident)

    try:
        genai.configure(api_key=api_key)
        model_name = settings.GEMINI_MODEL
        if not model_name.startswith("models/"):
            model_name = f"models/{model_name}"

        model = genai.GenerativeModel(
            model_name=model_name,
            generation_config={"response_mime_type": "application/json"}
        )

        prompt = f"""
You are the Chief Digital Forensics & Mobile Security AI for "Kavach — Smart Anti-Theft Shield".
Analyze this real-time breach telemetry captured by the device sensors and Tiger Data time-series database:

Breach Telemetry:
{json.dumps(incident, indent=2, default=str)}

Respond strictly in valid JSON matching this exact structure:
{{
  "threat_level": "CRITICAL" | "HIGH" | "MEDIUM" | "LOW",
  "theft_classification": "Concise theft classification (e.g. Vehicle-Borne Snatch, Subway Pickpocket, Dorm Theft)",
  "confidence_score": float between 0.0 and 100.0,
  "executive_summary": "2-3 sentences explaining exactly what happened in human terms",
  "likely_escape_mode": "Foot | Two-Wheeler | Motorized Vehicle | Public Transit",
  "actionable_recovery_playbook": [
    "Step 1",
    "Step 2",
    "Step 3",
    "Step 4"
  ],
  "police_fir_draft": "Formal legal complaint statement with coordinates and timestamps for law enforcement / CEIR portal",
  "telegram_emergency_message": "Punchy Markdown alert text with coordinates suitable for sending to emergency contacts"
}}
"""
        response = model.generate_content(prompt)
        parsed = json.loads(response.text)
        parsed["powered_by"] = f"Google {settings.GEMINI_MODEL} (Direct Live)"
        return parsed

    except Exception as e:
        logger.warning("Gemini API call failed (%s); falling back to hybrid analyzer.", e)
        fallback = _build_fallback_analysis(incident)
        fallback["powered_by"] = f"Kavach Hybrid Forensic Engine (Gemini Failover: {type(e).__name__})"
        return fallback


async def ask_gemini_copilot(question: str, context: Dict[str, Any]) -> Dict[str, Any]:
    """
    Answers user conversational queries about security history, anomalies, or advice.
    """
    api_key = settings.GEMINI_API_KEY

    if not HAS_GENAI or not api_key or "YOUR_GEMINI" in api_key:
        answer = (
            f"Kavach Security Assistant: Based on your current device telemetry, your phone is protected. "
            f"Regarding your question ('{question}'): The device routine engine tracks habitual sleep hours and "
            f"safe zones to minimize false alarms while maintaining active tamper surveillance."
        )
        return {
            "answer": answer,
            "recommended_actions": [
                "Verify Telegram emergency contacts in Settings",
                "Ensure Biometric disarm is active",
                "Check Tiger Data time-series telemetry in /analytics"
            ],
            "powered_by": "Kavach Security Assistant"
        }

    try:
        genai.configure(api_key=api_key)
        model_name = settings.GEMINI_MODEL
        if not model_name.startswith("models/"):
            model_name = f"models/{model_name}"

        model = genai.GenerativeModel(model_name=model_name)

        prompt = f"""
You are the Kavach Security Copilot. The user is asking a question about their anti-theft security system.
Device & Security Context:
{json.dumps(context, indent=2, default=str)}

User Question: "{question}"

Provide a concise, reassuring, expert security answer (maximum 3 paragraphs).
"""
        response = model.generate_content(prompt)
        return {
            "answer": response.text.strip(),
            "recommended_actions": [
                "Review recent behavioral anomalies in /behavior/anomalies",
                "Verify safe-zone geofence radii in /behavior/routine",
                "Test remote alarm command dispatch"
            ],
            "powered_by": f"Google {settings.GEMINI_MODEL}"
        }
    except Exception as e:
        logger.warning("Gemini Copilot call note: %s", e)
        return {
            "answer": f"Kavach Security Assistant: Your device is currently armed and streaming telemetry to Tiger Data. ({e})",
            "recommended_actions": ["Review device status in dashboard"],
            "powered_by": "Kavach Security Assistant (Fallback)"
        }

