# API Test Commands — Godown AI WMS Bot

> Base URL: `http://localhost:8080`
> Replace `<TOKEN>` with the JWT access token from /auth/login

---

## Auth Endpoints

### Register
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "manager1",
    "email": "manager@godown.ai",
    "password": "Password123",
    "role": "MANAGER",
    "warehouseId": "WH001"
  }' | jq .
```

### Login
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"manager@godown.ai","password":"Password123"}' | jq .
```

### Refresh Token
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<REFRESH_TOKEN>"}' | jq .
```

### Get Current User (Me)
```bash
curl -s http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

### Logout
```bash
curl -s -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer <TOKEN>"
```

---

## Chat Endpoints

### Send a chat message (SSE stream)
```bash
curl -s -N -X POST http://localhost:8080/api/v1/chat \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{
    "message": "How much low stock do we have?",
    "language": "en",
    "role": "manager",
    "warehouseId": "WH001",
    "sessionId": "session-abc-123",
    "warehouseName": "Mumbai Central Godown",
    "context": {
      "pendingInward": 5,
      "pendingOutward": 2,
      "lowStockCount": 3,
      "openGatePasses": 1
    }
  }'
```

### Hindi message
```bash
curl -s -N -X POST http://localhost:8080/api/v1/chat \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"message":"नमस्ते","language":"hi","role":"manager","warehouseId":"WH001","sessionId":"s1"}'
```

### List chat sessions
```bash
curl -s "http://localhost:8080/api/v1/chat/sessions?warehouseId=WH001&page=0&size=20" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

### Rename a session
```bash
curl -s -X PUT http://localhost:8080/api/v1/chat/sessions/session-abc-123/title \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Morning Dispatch Check"}'
```

### Get session messages
```bash
curl -s "http://localhost:8080/api/v1/chat/sessions/session-abc-123/messages" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

### Submit feedback on a message
```bash
curl -s -X POST "http://localhost:8080/api/v1/chat/messages/<MESSAGE_UUID>/feedback" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"helpful":true}'
```

### Delete (soft) a session
```bash
curl -s -X DELETE "http://localhost:8080/api/v1/chat/sessions/session-abc-123" \
  -H "Authorization: Bearer <TOKEN>"
```

---

## Inward Transactions

### Create inward
```bash
curl -s -X POST http://localhost:8080/api/v1/inward \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "warehouseId": "WH001",
    "commodityName": "Wheat",
    "supplierName": "Kumar Traders",
    "vehicleNumber": "MH12AB1234",
    "grnNumber": "GRN-2025-001",
    "quantityBags": 100,
    "unitWeight": 50.0,
    "unit": "KG",
    "remarks": "Good quality",
    "inwardDate": "2025-03-06"
  }' | jq .
```

### List inward (with filters)
```bash
curl -s "http://localhost:8080/api/v1/inward?warehouseId=WH001&status=PENDING&page=0&size=20" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

### Approve inward
```bash
curl -s -X PUT "http://localhost:8080/api/v1/inward/<UUID>/approve" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

### Reject inward
```bash
curl -s -X PUT "http://localhost:8080/api/v1/inward/<UUID>/reject?reason=Quality+Issue" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

---

## Outward Transactions

### Create outward
```bash
curl -s -X POST http://localhost:8080/api/v1/outward \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "warehouseId": "WH001",
    "itemName": "Wheat",
    "customerName": "Sharma Flour Mill",
    "vehicleNumber": "MH14CD5678",
    "dispatchNumber": "DISP-2025-001",
    "quantityBags": 50,
    "unitWeight": 50.0,
    "unit": "KG",
    "outwardDate": "2025-03-06"
  }' | jq .
```

### List outward
```bash
curl -s "http://localhost:8080/api/v1/outward?warehouseId=WH001&page=0&size=20" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

### Approve outward
```bash
curl -s -X PUT "http://localhost:8080/api/v1/outward/<UUID>/approve" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

---

## Gate Pass

### Create gate pass
```bash
curl -s -X POST http://localhost:8080/api/v1/gate-pass \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "warehouseId": "WH001",
    "vehicleNumber": "MH12AB1234",
    "driverName": "Ramesh Kumar",
    "purpose": "INWARD",
    "commodityName": "Wheat",
    "bagsCount": 100
  }' | jq .
```

### Close gate pass (vehicle exit)
```bash
curl -s -X PUT "http://localhost:8080/api/v1/gate-pass/<UUID>/close" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

### Get active gate passes
```bash
curl -s "http://localhost:8080/api/v1/gate-pass/active?warehouseId=WH001" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

### Get overstaying vehicles (> 4 hours)
```bash
curl -s "http://localhost:8080/api/v1/gate-pass/overstay?warehouseId=WH001" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

---

## Inventory

### List inventory (paginated)
```bash
curl -s "http://localhost:8080/api/v1/inventory?warehouseId=WH001&page=0&size=20" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

### Get low stock items
```bash
curl -s "http://localhost:8080/api/v1/inventory/low-stock?warehouseId=WH001" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

### Get inventory summary
```bash
curl -s "http://localhost:8080/api/v1/inventory/summary?warehouseId=WH001" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

---

## Bonds

### Create bond
```bash
curl -s -X POST http://localhost:8080/api/v1/bonds \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "warehouseId": "WH001",
    "bondNumber": "BOND-2025-001",
    "itemName": "Rice",
    "quantity": 500,
    "unit": "QTL",
    "bondDate": "2025-01-01",
    "expiryDate": "2025-06-30",
    "lenderName": "SBI Agri Branch"
  }' | jq .
```

### Get expiring bonds (next 30 days)
```bash
curl -s "http://localhost:8080/api/v1/bonds/expiring?warehouseId=WH001" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

---

## Reports

### Generate CSV stock summary
```bash
curl -s -X POST http://localhost:8080/api/v1/reports/generate \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "reportType": "STOCK_SUMMARY",
    "warehouseId": "WH001",
    "format": "CSV"
  }' | jq .
```

### Generate PDF inward summary
```bash
curl -s -X POST http://localhost:8080/api/v1/reports/generate \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "reportType": "INWARD_SUMMARY",
    "warehouseId": "WH001",
    "format": "PDF"
  }' | jq .
```

### Poll report status
```bash
curl -s "http://localhost:8080/api/v1/reports/<REPORT_UUID>/status" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

### Download report (saves to file)
```bash
curl -s "http://localhost:8080/api/v1/reports/<REPORT_UUID>/download" \
  -H "Authorization: Bearer <TOKEN>" \
  -o "report.csv"
```

### Report history
```bash
curl -s "http://localhost:8080/api/v1/reports/history?warehouseId=WH001" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

---

## Dashboard

### Get dashboard snapshot (60s cached)
```bash
curl -s "http://localhost:8080/api/v1/dashboard/snapshot?warehouseId=WH001" \
  -H "Authorization: Bearer <TOKEN>" | jq .
```

---

## Voice Transcription

### Transcribe audio (Sarvam STT)
```bash
curl -s -X POST http://localhost:8080/api/v1/voice/transcribe \
  -H "Authorization: Bearer <TOKEN>" \
  -F "audio=@recording.wav" \
  -F "language=hi" | jq .
```

---

## Actuator

### Health check (public)
```bash
curl -s http://localhost:8080/actuator/health | jq .
```

---

## Environment Variables Reference (Render / Production)

| Variable | Required | Example |
|---|---|---|
| `DB_URL` | Yes | `jdbc:postgresql://...supabase.co:5432/postgres` |
| `DB_USER` | Yes | `postgres` |
| `DB_PASSWORD` | Yes | `your-db-password` |
| `JWT_SECRET` | Yes | 32+ bytes, Base64-encoded |
| `JWT_EXPIRY_HOURS` | No (default 24) | `24` |
| `JWT_REFRESH_EXPIRY_DAYS` | No (default 7) | `7` |
| `GROQ_API_KEY` | Recommended | `gsk_...` |
| `SARVAM_API_KEY` | Optional | `sk-...` |
| `OLLAMA_BASE_URL` | Optional | `http://localhost:11434` |
| `FRONTEND_URL` | Yes | `https://yourapp.netlify.app` |
| `PORT` | Yes (Render sets it) | `8080` |
| `SPRING_PROFILES_ACTIVE` | Yes | `production` |
| `AI_PROVIDER` | No (default ollama) | `groq` |

### Generate a secure JWT_SECRET
```bash
openssl rand -base64 48
```
