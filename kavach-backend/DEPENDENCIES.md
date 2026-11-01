# Kavach Backend — Python Dependencies & Environment Guide 🛡️

This document provides a comprehensive technical breakdown of all Python dependencies required by the **Kavach Security Backend**, their architectural roles, underlying system bindings, and step-by-step instructions for environment configuration and troubleshooting.

---

## 1. System Requirements

* **Python Version:** Python **3.11.x** (or >= 3.10.x, 64-bit AMD64 recommended)
* **Operating System:** Windows 10/11, Linux (Ubuntu 22.04+), or macOS
* **Database Driver:** `asyncpg` (requires binary wheel or C-compiler on custom architectures)
* **Cloud Database:** Tiger Data / TimescaleDB (PostgreSQL 15+ with TimescaleDB hypertable extensions)
* **AI Engine:** Google Gemini 2.5 Flash (`models/gemini-2.5-flash`)

---

## 2. Complete Dependency Matrix

Below is the full inventory of direct dependencies pinned in [`requirements.txt`](file:///c:/Users/Kanak/Downloads/final/kavach-backend/requirements.txt):

| # | Package | Pinned Version | Primary Purpose | Key Modules Used |
|---|---|---|---|---|
| 1 | **`fastapi`** | `>=0.110.0` | High-performance asynchronous REST API framework | `fastapi.FastAPI`, `APIRouter`, `Depends`, `HTTPException`, `status` |
| 2 | **`uvicorn[standard]`** | `>=0.28.0` | Production ASGI web server with `uvloop`, `watchfiles`, `httptools` | `uvicorn.run`, CLI runner |
| 3 | **`sqlalchemy[asyncio]`** | `>=2.0.28` | Async ORM and SQL abstraction engine | `sqlalchemy.ext.asyncio.AsyncSession`, `select`, `update`, `insert` |
| 4 | **`greenlet`** | `>=3.0.0` | Coroutine concurrency primitive required by SQLAlchemy AsyncSession | `greenlet.getcurrent` (C-extension) |
| 5 | **`asyncpg`** | `>=0.29.0` | High-speed asynchronous PostgreSQL / TimescaleDB binary driver | `asyncpg.create_pool`, `asyncpg.exceptions` |
| 6 | **`pydantic`** | `>=2.6.4` | Data validation, type enforcement, and serialization (v2 core) | `pydantic.BaseModel`, `Field`, `validator`, `model_validator` |
| 7 | **`pydantic-settings`**| `>=2.2.1` | Environment variable parsing and `.env` loading | `pydantic_settings.BaseSettings`, `SettingsConfigDict` |
| 8 | **`bcrypt`** | `>=4.0.0` | Cryptographic password hashing (Blowfish cipher) | `bcrypt.hashpw`, `bcrypt.checkpw`, `bcrypt.gensalt` |
| 9 | **`pyjwt`** | `>=2.8.0` | RFC 7519 JSON Web Token signing and claim verification | `jwt.encode`, `jwt.decode`, `jwt.PyJWTError` |
| 10 | **`python-multipart`** | `>=0.0.9` | Streaming multipart/form-data parser for file uploads & OAuth2 forms | `multipart.multipart`, form decoding |
| 11 | **`email-validator`** | `>=2.0.0` | RFC 5322 email syntax & DNS deliverability validator | Used by Pydantic `EmailStr` for user registration |
| 12 | **`google-generativeai`** | `>=0.8.0` | Official Google SDK for Gemini 2.5 Flash API | `google.generativeai as genai`, `genai.GenerativeModel` |

---

## 3. Deep Dive: Package Roles in Kavach Architecture

### A. Web API Layer
* **`fastapi` & `uvicorn`**:
  * Hosts all 15+ REST endpoints under `/api/v1` (`/auth`, `/devices`, `/telemetry`, `/behavior`, `/ai`).
  * Employs asynchronous non-blocking request handlers (`async def`) capable of handling thousands of telemetry pings per second.
  * Auto-generates interactive OpenAPI documentation at `/docs` (Swagger UI) and `/redoc` (ReDoc).

### B. Persistence & TimescaleDB Layer
* **`sqlalchemy[asyncio]` & `greenlet`**:
  * Manages database models: `User`, `Device`, `DeviceTelemetry`, `DeviceBehaviorObservation`, `DeviceRoutineProfile`, `SecurityIncident`, `SafeZone`, and `RemoteCommand`.
  * `greenlet` is a mandatory C-extension that enables SQLAlchemy's async interface to switch contexts transparently between ORM operations and async database transactions.
* **`asyncpg`**:
  * Direct binary protocol driver connecting to **Tiger Data** (TimescaleDB cloud).
  * Executes Timescale-native SQL queries such as `time_bucket('1 hour', recorded_at)` and hypertable columnar compression policies.

### C. Threat Modeling & AI Forensics Layer
* **`google-generativeai`**:
  * Powers the Autonomous Forensic AI Copilot.
  * Direct integration with `models/gemini-2.5-flash` for:
    1. Incident threat severity classification (LOW, MEDIUM, HIGH, CRITICAL).
    2. Generating automated First Information Report (FIR) drafts for Indian Law Enforcement (BNS / IPC compliance).
    3. Emergency response playbooks (remote wipe sequences, SIM lockout advisories, bystander guidance).

### D. Identity & Cryptography Layer
* **`bcrypt`**:
  * Native password hashing using salted Blowfish cryptography to protect user passwords against rainbow-table attacks.
* **`pyjwt`**:
  * Issues stateless, cryptographically signed HS256 Bearer tokens with short-lived expiration (`ACCESS_TOKEN_EXPIRE_MINUTES=60`) for device pairing and dashboard sessions.
* **`email-validator`**:
  * Validates user email addresses during `/api/v1/auth/register` to prevent malformed or invalid accounts.

---

## 4. Setup & Installation Instructions

### Option 1: Direct Installation in Active Environment

Run in your terminal (PowerShell, CMD, or Bash):

```bash
cd kavach-backend
pip install -r requirements.txt
```

### Option 2: Fresh Isolated Virtual Environment (Recommended)

To avoid conflicts with external tools or agents (such as Hermes or system tools):

```powershell
# 1. Navigate to the backend directory
cd c:\Users\Kanak\Downloads\final\kavach-backend

# 2. Create a clean virtual environment
python -m venv .venv

# 3. Activate the virtual environment
# On Windows PowerShell:
.\.venv\Scripts\Activate.ps1
# On Windows Command Prompt:
.\.venv\Scripts\activate.bat
# On Linux/macOS:
source .venv/bin/activate

# 4. Upgrade pip and install all dependencies
python -m pip install --upgrade pip
pip install -r requirements.txt
```

---

## 5. Verification & Health Check

After installing dependencies, verify that your environment can load all modules and connect to Tiger Data:

```powershell
# Run the built-in diagnostic verifier
python verify_backend.py
```

Expected output:
```text
[OK] FastAPI and ASGI core loaded successfully.
[OK] SQLAlchemy and asyncpg loaded successfully.
[OK] Tiger Data TimescaleDB connection established.
[OK] Google Gemini 2.5 Flash initialized.
[SUCCESS] All Kavach backend dependencies are verified and operational!
```

---

## 6. Common Windows Issues & Solutions

### A. `ModuleNotFoundError: No module named 'sqlalchemy'` (or another module)
* **Cause:** Your terminal has another virtual environment active (e.g. `C:\Users\<user>\AppData\Local\hermes\hermes-agent\venv\`) which does not contain the project packages.
* **Fix:** Either:
  1. Run `pip install -r requirements.txt` directly inside that active terminal, OR
  2. Use the full path to your main Python binary:
     ```powershell
     & "C:\Users\Kanak\AppData\Local\Programs\Python\Python311\python.exe" -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
     ```

### B. `[WinError 10013] An attempt was made to access a socket in a way forbidden by its access permissions`
* **Cause:** Port `8000` is already in use by another running instance of Kavach or another application.
* **Fix:**
  * Check and kill the process holding port 8000:
    ```powershell
    Get-NetTCPConnection -LocalPort 8000 | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
    ```
  * Or start Uvicorn on a different port:
    ```powershell
    python -m uvicorn app.main:app --port 8001 --reload
    ```

### C. `ImportError: The SQLAlchemy asyncio module requires that the Python 'greenlet' library is installed`
* **Cause:** SQLAlchemy 2.0+ async features require the `greenlet` C-extension.
* **Fix:**
  ```powershell
  pip install greenlet>=3.0.0
  ```
