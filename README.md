# 🛡️ Kavach — Smart Anti-Theft Shield

[![Android](https://img.shields.io/badge/Platform-Android%208.0+-3DDC84.svg?logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![FastAPI](https://img.shields.io/badge/Backend-FastAPI%20(Python)-009688.svg?logo=fastapi)](https://fastapi.tiangolo.com)
[![Tiger Data](https://img.shields.io/badge/Database-Tiger%20Data%20(TimescaleDB)-FF6F00.svg)](https://www.tigerdata.com/)
[![Docker](https://img.shields.io/badge/Deployment-Docker%20%7C%20DigitalOcean-2496ED.svg?logo=docker)](https://www.docker.com/)
[![Author](https://img.shields.io/badge/Author-Kanak%20Verma-orange.svg)](https://github.com/kanak227)

Next-generation, tamper-resistant anti-theft defense system featuring an **intelligent Jetpack Compose Android client** paired with a **high-throughput asynchronous Python FastAPI cloud backend** running on **Tiger Data (TimescaleDB)**.

---

## 🏛️ Monorepo Architecture

```
Kavach/
├── kavach-frontend/          # 📱 Native Android Security Application
│   ├── app/                  # Jetpack Compose UI, Sensor Fusion & Background Service
│   ├── gradle/               # Gradle wrapper and version catalogs
│   ├── build.gradle.kts      # Root build configuration
│   ├── settings.gradle.kts   # Project and module definitions
│   └── README.md             # Mobile app documentation & architecture
│
├── kavach-backend/           # ⚡ High-Throughput FastAPI Cloud Backend
│   ├── app/
│   │   ├── api/              # Auth, Devices, Telemetry, and Behavioral endpoints
│   │   ├── core/             # JWT & bcrypt security, config
│   │   ├── db/               # TimescaleDB Hypertables & SQLAlchemy async models
│   │   ├── schemas/          # Pydantic v2 schemas
│   │   └── services/         # 🧠 Autonomous Adaptive Behavioral Engine
│   ├── Dockerfile            # Production containerization
│   ├── docker-compose.yml    # 1-command startup
│   ├── requirements.txt      # Python dependencies
│   ├── seed_db.py            # Tiger Data database seeder
│   └── README.md             # API documentation & Swagger guide
│
├── .gitignore                # Global ignore rules (secrets, build artifacts, cache)
└── README.md                 # Project entry point and documentation
```

---

## 🌟 Key Highlights

### 1. 📱 `kavach-frontend` (Android Client)
- **Stealth Decoy Clock Trap:** Displays an innocent ambient clock on motion detection; intruder feels no panic while owner disarms via biometrics.
- **24x7 Travel Mode:** Intelligent stillness analysis, horizontal zig-zag bump and shock immunity for trains, metros, and cars.
- **Dual-Sensor Pocket Fusion:** High-precision accelerometer, ambient light, and proximity fusion eliminates pocket false alarms.
- **Volume Floor Lock & Tamper Recovery:** Overrides mute buttons to blast siren at 100% volume; automatically recovers armed state after reboot.
- **Interactive Profile & Usage Analytics:** 5-tier Y-axis daily usage graph, photo picker with pinch-zoom overlay, and automatic sleep schedule.

### 2. ⚡ `kavach-backend` (Cloud API & Time-Series Engine)
- **🐅 Tiger Data (TimescaleDB) Hypertables:** Automated time-series chunking for GPS breadcrumbs and passive behavioral telemetry with **90%+ columnar compression**.
- **🧠 Autonomous Adaptive Routine Engine:** Spatial density clustering automatically discovers **Home** and **Work / Campus** safe zones; assesses real-time **Anomaly Risk Scores (0 - 100)** to dynamically command device defenses (`SAFE_RELAXED`, `BALANCED`, `HIGH_VIGILANCE`, `AUTONOMOUS_LOCKDOWN`).
- **🔐 JWT & bcrypt Security:** Stateless Bearer token authentication with access (24h) and refresh (30d) tokens.
- **📱 Remote Command Hub:** Remotely trigger alarms (`ALARM_ON`, `ALARM_OFF`, `LOCATE_NOW`, `LOCK_DEVICE`).
- **🐳 Cloud Ready:** Docker containerization with built-in health probes for DigitalOcean or local hosting.

---

## 🚀 Quickstart

### Running the Backend (`kavach-backend`)
```bash
cd kavach-backend

# 1. Create and activate virtual environment
python -m venv venv
venv\Scripts\activate       # Windows
# source venv/bin/activate  # Linux/macOS

# 2. Install dependencies
pip install -r requirements.txt

# 3. Configure Tiger Data connection in .env
# (See kavach-backend/.env.example for template)

# 4. Seed database
python seed_db.py

# 5. Start API server
uvicorn app.main:app --reload --port 8000
```
*API Documentation & Interactive Swagger UI:* **`http://localhost:8000/docs`**

### Building the Android App (`kavach-frontend`)
1. Open Android Studio $\rightarrow$ **Open Project** $\rightarrow$ Select `kavach-frontend`.
2. Sync Gradle dependencies.
3. Build & Run on an Android device or emulator (Android 8.0+ / API 26+).

---

## 📄 License
Developed for the hackathon by **Kanak Verma ([@kanak227](https://github.com/kanak227))**.

