# 🐅 Kavach Cloud Backend

High-performance, asynchronous anti-theft security backend for **Kavach — Smart Anti-Theft Shield**, powered by **FastAPI (Python)** and **Tiger Data (TimescaleDB / PostgreSQL)**, designed for deployment on **DigitalOcean**.

---

## 🌟 Key Architecture & Features

- 🔐 **JWT Authentication & bcrypt Security**:
  - Secure user registration, credential hashing, and JWT Access + Refresh token issuance.
- 🐅 **Tiger Data (TimescaleDB) Ingestion**:
  - Automatically partitioned **Hypertables** for continuous time-series device telemetry (GPS coordinates, battery levels, accelerometer shock impacts) and passive behavioral observations.
  - 90%+ columnar compression policy for space-saving long-term storage.
  - Native `time_bucket()` analytical queries for escape speed & travel trajectory reconstruction.
- 🧠 **Autonomous Adaptive Behavioral Shield**:
  - Continuous behavioral observation ingestion (`device_behavior_observations` hypertable).
  - Spatial density clustering for automatic discovery of user safe zones (**Home**, **Work / Campus**).
  - Temporal habit profiling: habitual sleep & charging windows, peak activity hours.
  - Real-time **Anomaly Risk Scoring (0 - 100)**: dynamically adapts local device shield sensitivity (`SAFE_RELAXED`, `BALANCED`, `HIGH_VIGILANCE`, `AUTONOMOUS_LOCKDOWN`).
- 📱 **Device Pairing & Remote Commands**:
  - Pair Android hardware UUIDs to user accounts.
  - Queue remote commands (`ALARM_ON`, `ALARM_OFF`, `LOCATE_NOW`, `LOCK_DEVICE`) to remotely trigger device sirens or lock screens.
- 📜 **Interactive Swagger UI**:
  - Test every endpoint directly in your browser at `/docs` with zero frontend required.
- 🐳 **DigitalOcean Production Ready**:
  - Fully containerized with `Dockerfile` and `docker-compose.yml`.

---

## 🏛️ API Endpoints Overview

### 🔐 Authentication
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :---: |
| `POST` | `/api/v1/auth/register` | Register new account with email & password | No |
| `POST` | `/api/v1/auth/login` | Login to receive JWT Access + Refresh token | No |
| `POST` | `/api/v1/auth/refresh` | Exchange refresh token for fresh access token | No |
| `GET` | `/api/v1/auth/me` | Fetch authenticated user profile & emergency info | Yes (Bearer) |

### 📱 Device Fleet & Remote Dispatch
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :---: |
| `POST` | `/api/v1/devices/pair` | Pair phone UUID with user account | Yes (Bearer) |
| `GET` | `/api/v1/devices` | List all devices belonging to user | Yes (Bearer) |
| `PATCH`| `/api/v1/devices/{uuid}/status` | Update armed state & battery level | No (From App) |
| `POST` | `/api/v1/devices/{uuid}/command` | Queue remote command (`ALARM_ON`, `LOCATE_NOW`) | Yes (Bearer) |
| `GET` | `/api/v1/devices/{uuid}/commands/pending` | Mobile app polls for pending commands | No (From App) |

### 🧠 Adaptive Behavioral Shield & Routine Learning
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :---: |
| `POST` | `/api/v1/behavior/observe` | Ingest passive sensor state & return dynamic adaptation policy | No (From App) |
| `GET` | `/api/v1/behavior/{uuid}/policy` | Query current autonomous defensive policy for device | No (From App) |
| `GET` | `/api/v1/behavior/{uuid}/routine` | Inspect learned safe zones & habitual charging windows | Yes (Bearer) |
| `POST` | `/api/v1/behavior/{uuid}/recalculate` | Trigger Tiger Data spatial clustering to learn safe zones | Yes (Bearer) |
| `GET` | `/api/v1/behavior/{uuid}/anomalies` | Audit log of flagged anomalies and security actions | Yes (Bearer) |

### ✨ Google Gemini 1.5 AI Forensics
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :---: |
| `POST` | `/api/v1/ai/analyze-incident` | Generate Gemini 1.5 Forensic Assessment & Police Draft | No |
| `POST` | `/api/v1/ai/ask-copilot` | Natural language conversational Security Copilot | No |

### 🐅 Time-Series Telemetry (Tiger Data Hypertables)
| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :---: |
| `POST` | `/api/v1/telemetry/ping` | Ingest live GPS & sensor telemetry to Tiger Data | No (From App) |
| `GET` | `/api/v1/telemetry/live/{uuid}` | Fetch latest known position & sensor state | Yes (Bearer) |
| `GET` | `/api/v1/telemetry/history/{uuid}` | Fetch historical breadcrumb route | Yes (Bearer) |
| `GET` | `/api/v1/telemetry/analytics/{uuid}` | TimescaleDB `time_bucket()` speed & route query | Yes (Bearer) |

---

## 🚀 Quickstart: Running Locally

### 1. Prerequisites
- Python 3.10+ installed
- A free database instance from [tigerdata.com](https://www.tigerdata.com/) *(or local PostgreSQL)*

### 2. Configure Environment
1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
2. Paste your **Tiger Data** connection string into `DATABASE_URL` in `.env`:
   ```env
   DATABASE_URL="postgresql+asyncpg://tsdbadmin:<PASSWORD>@<HOST>.tigerdata.cloud:5432/tsdb?ssl=require"
   ```

### 3. Install & Run
```bash
# Create and activate virtual environment
python -m venv venv

# Windows:
venv\Scripts\activate

# Linux/macOS:
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Start Uvicorn development server
uvicorn app.main:app --reload --port 8000
```

### 4. Interactive Testing
Open your browser at:
👉 **`http://localhost:8000/docs`**

You can test registration, device pairing, telemetry pings, and behavioral observation ingestion with real-time anomaly calculation immediately!

---

## ☁️ Deployment to DigitalOcean

### 1-Click via Docker Compose (Recommended)
1. Launch a **DigitalOcean Droplet** (Ubuntu 24.04 LTS, $4-$6/mo or via GitHub Student Pack).
2. SSH into your Droplet:
   ```bash
   ssh root@<DROPLET_IP>
   ```
3. Install Docker:
   ```bash
   curl -fsSL https://get.docker.com | sh
   ```
4. Clone or copy your `kavach-backend` folder to the server:
   ```bash
   git clone <YOUR_REPO_URL>
   cd kavach-backend
   ```
5. Create `.env` with your Tiger Data connection string:
   ```bash
   nano .env
   ```
6. Start the service:
   ```bash
   docker compose up -d
   ```
7. Access your live API at:
   `http://<DROPLET_IP>:8000/docs`

---

## 📱 Mobile App (Kavach) Integration Payloads

### 1. Passive Behavioral Telemetry & Autonomous Adaptation
The Android client sends passive state updates at low cadence (e.g. every 5–10 minutes) or upon significant sensor transition:

```json
// POST /api/v1/behavior/observe
{
  "device_uuid": "d4f8e912-3b2a-4a81-9b1e-723a109a012b",
  "activity_type": "STATIONARY",
  "activity_confidence": 95,
  "is_charging": true,
  "charging_type": "AC",
  "battery_level": 92,
  "screen_on": false,
  "latitude": 28.6139,
  "longitude": 77.2090,
  "accuracy_meters": 10.0,
  "ambient_light_lux": 0.5,
  "step_count": 4200
}
```

**Response (Immediate Autonomous Defense Directive):**
```json
{
  "device_uuid": "d4f8e912-3b2a-4a81-9b1e-723a109a012b",
  "current_threat_mode": "SAFE_RELAXED",
  "current_risk_score": 0.0,
  "is_safe_zone": true,
  "matched_safe_zone": "Primary Residence (Home)",
  "motion_sensitivity": "LOW",
  "pocket_detection_sensitivity": "RELAXED",
  "alarm_grace_period_sec": 15,
  "ping_interval_sec": 600,
  "stealth_camera_capture": false,
  "screen_lockdown_required": false,
  "display_warning_banner": false,
  "active_anomalies": []
}
```

### 2. High-Frequency Incident Ping (Breach / Alarm Active)
```json
// POST /api/v1/telemetry/ping
{
  "device_uuid": "d4f8e912-3b2a-4a81-9b1e-723a109a012b",
  "latitude": 28.7500,
  "longitude": 77.4000,
  "accuracy": 4.5,
  "speed": 18.2,
  "shock_magnitude": 9.4,
  "battery_level": 78,
  "alarm_active": true,
  "trigger_reason": "UNAUTHORIZED_POCKET_SNATCH"
}
```

---

## 📄 License
Part of the **Kavach Security System** by **Kanak Verma (@kanak227)**.
