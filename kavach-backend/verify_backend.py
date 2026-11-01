import asyncio
import httpx
from app.main import app


async def main():
    print("Testing Kavach FastAPI endpoints using httpx AsyncClient...")
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
        # 1. Test root endpoint
        res_root = await client.get("/")
        print("GET / -> Status:", res_root.status_code)
        print("GET / -> Payload:", res_root.json())
        assert res_root.status_code == 200, "Root status != 200"

        # 2. Test health endpoint
        res_health = await client.get("/health")
        print("GET /health -> Status:", res_health.status_code)
        print("GET /health -> Payload:", res_health.json())
        assert res_health.status_code == 200, "Health status != 200"

        # 3. Test pair validation failure (422 without token or payload)
        res_pair = await client.post("/api/v1/devices/pair", json={})
        print("POST /api/v1/devices/pair without auth -> Status:", res_pair.status_code)
        assert res_pair.status_code in [401, 403, 422], "Auth guard not triggered"

        # 4. Test telemetry ping validation
        res_ping = await client.post("/api/v1/telemetry/ping", json={"device_uuid": "test-device"})
        print("POST /api/v1/telemetry/ping (empty telemetry) -> Status:", res_ping.status_code)
        assert res_ping.status_code in [400, 422], "Validation guard working"

    print("\n All local endpoint checks passed successfully!")


if __name__ == "__main__":
    asyncio.run(main())

