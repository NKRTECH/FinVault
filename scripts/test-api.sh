#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# FinVault API — Integration Test Script
# ═══════════════════════════════════════════════════════════════
#
# Usage:
#   ./scripts/test-api.sh                          # default: http://localhost:8080
#   ./scripts/test-api.sh http://host:port          # custom API base URL
#   ACTUATOR_URL=http://host:9090/actuator ./scripts/test-api.sh  # custom actuator
#
# Prerequisites:
#   - curl, jq
#   - FinVault backend running (docker compose up -d)
#   - Demo data seeded (dev/docker profile)
# ═══════════════════════════════════════════════════════════════

set -euo pipefail

# ── Windows / Git-Bash: locate jq ────────────────────────────
# Supports:
# 1) jq in PATH
# 2) jq.exe in PATH
# 3) repo-local jq/jq.exe (project root)
# 4) winget link: /c/Users/<user>/AppData/Local/Microsoft/WinGet/Links/jq.exe
# 5) winget package cache: /c/Users/<user>/AppData/Local/Microsoft/WinGet/Packages/jqlang.jq_*/jq.exe
_SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
_PROJECT_ROOT="$(cd "$_SCRIPT_DIR/.." && pwd)"
_JQ_BIN=""

if command -v jq >/dev/null 2>&1; then
  _JQ_BIN="$(command -v jq)"
elif command -v jq.exe >/dev/null 2>&1; then
  _JQ_BIN="$(command -v jq.exe)"
elif [ -f "$_PROJECT_ROOT/jq" ]; then
  _JQ_BIN="$_PROJECT_ROOT/jq"
elif [ -f "$_PROJECT_ROOT/jq.exe" ]; then
  _JQ_BIN="$_PROJECT_ROOT/jq.exe"
else
  for _WIN_USER in "${USER:-}" "${USERNAME:-}"; do
    [ -n "$_WIN_USER" ] || continue

    _WINGET_LINK="/c/Users/$_WIN_USER/AppData/Local/Microsoft/WinGet/Links/jq.exe"
    if [ -f "$_WINGET_LINK" ]; then
      _JQ_BIN="$_WINGET_LINK"
      break
    fi

    for _WINGET_PKG in /c/Users/$_WIN_USER/AppData/Local/Microsoft/WinGet/Packages/jqlang.jq_*/jq.exe; do
      if [ -f "$_WINGET_PKG" ]; then
        _JQ_BIN="$_WINGET_PKG"
        break 2
      fi
    done
  done

  if [ -z "$_JQ_BIN" ]; then
    for _WINGET_PKG_ANY in /c/Users/*/AppData/Local/Microsoft/WinGet/Packages/jqlang.jq_*/jq.exe; do
      if [ -f "$_WINGET_PKG_ANY" ]; then
        _JQ_BIN="$_WINGET_PKG_ANY"
        break
      fi
    done
  fi
fi

if [ -z "$_JQ_BIN" ]; then
  echo "Error: 'jq' not found. Install via: winget install jqlang.jq" >&2
  echo "Tip: If jq was just installed, close and reopen Git Bash so PATH refreshes." >&2
  exit 1
fi

# Route all jq calls through the resolved binary.
jq() {
  command "$_JQ_BIN" "$@"
}

# ── Config ────────────────────────────────────────────────────
BASE_URL="${1:-http://localhost:8080}"
API="$BASE_URL/api/v1"

# Derive actuator URL from the same host (management port 9090)
_BASE_HOST=$(echo "$BASE_URL" | sed -E 's|(https?://[^:/]+).*|\1|')
ACTUATOR="${ACTUATOR_URL:-${_BASE_HOST}:9090/actuator}"

PASS=0
FAIL=0
TOTAL=0

# ── Colors ────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# ── Helpers ───────────────────────────────────────────────────

banner() {
  echo ""
  echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
  echo -e "${BOLD}  $1${NC}"
  echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
}

section() {
  echo ""
  echo -e "${YELLOW}── $1 ──${NC}"
}

assert_status() {
  local test_name="$1"
  local expected="$2"
  local actual="$3"

  TOTAL=$((TOTAL + 1))
  if [ "$actual" -eq "$expected" ]; then
    echo -e "  ${GREEN}✓${NC} $test_name ${GREEN}(HTTP $actual)${NC}"
    PASS=$((PASS + 1))
  else
    echo -e "  ${RED}✗${NC} $test_name — expected $expected, got ${RED}$actual${NC}"
    echo -e "  ${YELLOW}Response: $RESP_BODY${NC}"
    FAIL=$((FAIL + 1))
  fi
}

assert_json_field() {
  local test_name="$1"
  local json="$2"
  local field="$3"
  local expected="$4"

  TOTAL=$((TOTAL + 1))
  local actual
  actual=$(echo "$json" | jq -r "$field" 2>/dev/null | tr -d '\r')
  expected=$(echo "$expected" | tr -d '\r')

  if [ "$actual" = "$expected" ]; then
    echo -e "  ${GREEN}✓${NC} $test_name"
    PASS=$((PASS + 1))
  else
    echo -e "  ${RED}✗${NC} $test_name — expected '$expected', got '$actual'"
    FAIL=$((FAIL + 1))
  fi
}

# Perform a curl request and capture both body and HTTP status code.
# Usage: do_request METHOD URL [DATA] [TOKEN]
# Sets: RESP_BODY, RESP_CODE
do_request() {
  local method="$1"
  local url="$2"
  local data="${3:-}"
  local token="${4:-}"

  local curl_args=(-s -w "\n%{http_code}" -X "$method" "$url")

  if [ -n "$token" ]; then
    curl_args+=(-H "Authorization: Bearer $token")
  fi

  if [ -n "$data" ]; then
    curl_args+=(-H "Content-Type: application/json" -d "$data")
  fi

  local response
  response=$(curl "${curl_args[@]}" 2>/dev/null || echo -e "\n000")

  RESP_BODY=$(echo "$response" | sed '$d')
  RESP_CODE=$(echo "$response" | tail -1)
}

# ── Preflight check ──────────────────────────────────────────
banner "FinVault API Test Suite"
echo -e "  Target: ${BOLD}$BASE_URL${NC}"
echo -e "  Time:   $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

echo -n "  Checking connectivity… "
if curl -sf "$BASE_URL/api/v1/auth/login" -X POST -H "Content-Type: application/json" \
  -d '{"username":"x","password":"x"}' > /dev/null 2>&1 || true; then
  echo -e "${GREEN}OK${NC}"
else
  echo -e "${RED}FAILED${NC}"
  echo "  Cannot reach $BASE_URL — is the server running?"
  exit 1
fi


# ═════════════════════════════════════════════════════════════
# 1. AUTHENTICATION
# ═════════════════════════════════════════════════════════════
banner "1. Authentication"

# ── 1.1 Login as admin ───────────────────────────────────────
section "1.1 Admin Login"
do_request POST "$API/auth/login" '{"username":"admin","password":"admin123"}'
assert_status "POST /auth/login (admin)" 200 "$RESP_CODE"
assert_json_field "Response success=true" "$RESP_BODY" ".success" "true"

ADMIN_TOKEN=$(echo "$RESP_BODY" | jq -r '.data.token' 2>/dev/null | tr -d '\r')
if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
  echo -e "  ${RED}✗ Could not extract admin JWT — aborting${NC}"
  exit 1
fi
echo -e "  ${GREEN}✓${NC} JWT token extracted (${#ADMIN_TOKEN} chars)"

# ── 1.2 Login as analyst ─────────────────────────────────────
section "1.2 Analyst Login"
do_request POST "$API/auth/login" '{"username":"analyst","password":"password123"}'
assert_status "POST /auth/login (analyst)" 200 "$RESP_CODE"

ANALYST_TOKEN=$(echo "$RESP_BODY" | jq -r '.data.token' 2>/dev/null | tr -d '\r')
echo -e "  ${GREEN}✓${NC} Analyst JWT extracted"

# ── 1.3 Login as viewer ──────────────────────────────────────
section "1.3 Viewer Login"
do_request POST "$API/auth/login" '{"username":"viewer","password":"password123"}'
assert_status "POST /auth/login (viewer)" 200 "$RESP_CODE"

VIEWER_TOKEN=$(echo "$RESP_BODY" | jq -r '.data.token' 2>/dev/null | tr -d '\r')
echo -e "  ${GREEN}✓${NC} Viewer JWT extracted"

# ── 1.4 Register new user ────────────────────────────────────
section "1.4 Register New User"
TIMESTAMP=$(date +%s)
do_request POST "$API/auth/register" \
  "{\"username\":\"testuser_$TIMESTAMP\",\"email\":\"test_$TIMESTAMP@finvault.com\",\"password\":\"test123456\",\"fullName\":\"Test User $TIMESTAMP\"}"
assert_status "POST /auth/register" 201 "$RESP_CODE"

TEST_TOKEN=$(echo "$RESP_BODY" | jq -r '.data.token' 2>/dev/null | tr -d '\r')
TOTAL=$((TOTAL + 1))
if [ -n "$TEST_TOKEN" ] && [ "$TEST_TOKEN" != "null" ] && [[ "$TEST_TOKEN" =~ ^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$ ]]; then
  echo -e "  ${GREEN}✓${NC} Register returns valid JWT token"
  PASS=$((PASS + 1))
else
  echo -e "  ${RED}✗${NC} Register returns valid JWT token — got '${TEST_TOKEN:0:30}...'"
  FAIL=$((FAIL + 1))
fi

# ── 1.5 Invalid credentials ──────────────────────────────────
section "1.5 Invalid Credentials"
do_request POST "$API/auth/login" '{"username":"admin","password":"wrongpassword"}'
assert_status "POST /auth/login (bad password)" 401 "$RESP_CODE"

# ── 1.6 Missing fields ───────────────────────────────────────
section "1.6 Validation Errors"
do_request POST "$API/auth/login" '{"username":"","password":""}'
assert_status "POST /auth/login (empty fields)" 400 "$RESP_CODE"

do_request POST "$API/auth/register" '{"username":"ab","email":"bad","password":"123","fullName":""}'
assert_status "POST /auth/register (validation)" 400 "$RESP_CODE"


# ═════════════════════════════════════════════════════════════
# 2. FINANCIAL RECORDS — CRUD
# ═════════════════════════════════════════════════════════════
banner "2. Financial Records"

# ── 2.1 Create (ADMIN) ───────────────────────────────────────
section "2.1 Create Record (Admin)"
do_request POST "$API/records" \
  '{"amount":999.99,"type":"EXPENSE","category":"Testing","description":"API test record","date":"2025-01-01"}' \
  "$ADMIN_TOKEN"
assert_status "POST /records (admin)" 201 "$RESP_CODE"
assert_json_field "Created record type" "$RESP_BODY" ".data.type" "EXPENSE"
assert_json_field "Created record amount" "$RESP_BODY" ".data.amount" "999.99"

RECORD_ID=$(echo "$RESP_BODY" | jq -r '.data.id' | tr -d '\r')
echo -e "  ${GREEN}✓${NC} Created record ID: $RECORD_ID"

# ── 2.2 Create denied for non-admin ──────────────────────────
section "2.2 Create Denied (Analyst)"
do_request POST "$API/records" \
  '{"amount":100,"type":"INCOME","category":"Test","description":"Should fail","date":"2025-01-01"}' \
  "$ANALYST_TOKEN"
assert_status "POST /records (analyst → 403)" 403 "$RESP_CODE"

section "2.3 Create Denied (Viewer)"
do_request POST "$API/records" \
  '{"amount":100,"type":"INCOME","category":"Test","description":"Should fail","date":"2025-01-01"}' \
  "$VIEWER_TOKEN"
assert_status "POST /records (viewer → 403)" 403 "$RESP_CODE"

# ── 2.4 Read (all roles) ─────────────────────────────────────
section "2.4 List Records"
do_request GET "$API/records?page=0&size=5" "" "$ADMIN_TOKEN"
assert_status "GET /records (admin)" 200 "$RESP_CODE"
assert_json_field "Success flag" "$RESP_BODY" ".success" "true"

TOTAL_RECORDS=$(echo "$RESP_BODY" | jq -r '.data.totalElements' | tr -d '\r')
echo -e "  ${GREEN}✓${NC} Total records in DB: $TOTAL_RECORDS"

do_request GET "$API/records?page=0&size=5" "" "$ANALYST_TOKEN"
assert_status "GET /records (analyst)" 200 "$RESP_CODE"

do_request GET "$API/records?page=0&size=5" "" "$VIEWER_TOKEN"
assert_status "GET /records (viewer)" 200 "$RESP_CODE"

# ── 2.5 Get by ID ────────────────────────────────────────────
section "2.5 Get Record by ID"
do_request GET "$API/records/$RECORD_ID" "" "$ADMIN_TOKEN"
assert_status "GET /records/$RECORD_ID" 200 "$RESP_CODE"
assert_json_field "Record ID matches" "$RESP_BODY" ".data.id" "$RECORD_ID"

# ── 2.6 Filter records ───────────────────────────────────────
section "2.6 Filter Records"
do_request GET "$API/records?type=INCOME&page=0&size=5" "" "$ADMIN_TOKEN"
assert_status "GET /records?type=INCOME" 200 "$RESP_CODE"

do_request GET "$API/records?category=Salary&page=0&size=5" "" "$ADMIN_TOKEN"
assert_status "GET /records?category=Salary" 200 "$RESP_CODE"

do_request GET "$API/records?startDate=2025-01-01&endDate=2025-03-31&page=0&size=5" "" "$ADMIN_TOKEN"
assert_status "GET /records?startDate&endDate" 200 "$RESP_CODE"

do_request GET "$API/records?minAmount=1000&maxAmount=6000&page=0&size=5" "" "$ADMIN_TOKEN"
assert_status "GET /records?minAmount&maxAmount" 200 "$RESP_CODE"

# ── 2.7 Update record ────────────────────────────────────────
section "2.7 Update Record (Admin)"
do_request PUT "$API/records/$RECORD_ID" \
  '{"amount":1234.56,"type":"INCOME","category":"Updated","description":"Updated via API test","date":"2025-02-01"}' \
  "$ADMIN_TOKEN"
assert_status "PUT /records/$RECORD_ID" 200 "$RESP_CODE"
assert_json_field "Updated amount" "$RESP_BODY" ".data.amount" "1234.56"
assert_json_field "Updated category" "$RESP_BODY" ".data.category" "Updated"

# ── 2.8 Delete record ────────────────────────────────────────
section "2.8 Delete Record (Admin)"
do_request DELETE "$API/records/$RECORD_ID" "" "$ADMIN_TOKEN"
assert_status "DELETE /records/$RECORD_ID" 200 "$RESP_CODE"

# ── 2.9 Unauthenticated access ───────────────────────────────
section "2.9 Unauthenticated Access"
do_request GET "$API/records" "" ""
assert_status "GET /records (no token → 401)" 401 "$RESP_CODE"


# ═════════════════════════════════════════════════════════════
# 3. USER MANAGEMENT
# ═════════════════════════════════════════════════════════════
banner "3. User Management"

# ── 3.1 Get current user ─────────────────────────────────────
section "3.1 Get Current User Profile"
do_request GET "$API/users/me" "" "$ADMIN_TOKEN"
assert_status "GET /users/me (admin)" 200 "$RESP_CODE"
assert_json_field "Username is admin" "$RESP_BODY" ".data.username" "admin"

do_request GET "$API/users/me" "" "$ANALYST_TOKEN"
assert_status "GET /users/me (analyst)" 200 "$RESP_CODE"
assert_json_field "Username is analyst" "$RESP_BODY" ".data.username" "analyst"

# ── 3.2 List users (admin only) ──────────────────────────────
section "3.2 List Users (Admin)"
do_request GET "$API/users?page=0&size=10" "" "$ADMIN_TOKEN"
assert_status "GET /users (admin)" 200 "$RESP_CODE"

USER_COUNT=$(echo "$RESP_BODY" | jq -r '.data.totalElements' | tr -d '\r')
echo -e "  ${GREEN}✓${NC} Total users: $USER_COUNT"

section "3.3 List Users (Analyst → 403)"
do_request GET "$API/users?page=0&size=10" "" "$ANALYST_TOKEN"
assert_status "GET /users (analyst → 403)" 403 "$RESP_CODE"

# ── 3.4 Get user by ID ───────────────────────────────────────
section "3.4 Get User by ID"
# Get the test user's ID from the users list
TEST_USER_ID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" "$API/users?page=0&size=50" \
  | jq -r ".data.content[] | select(.username | startswith(\"testuser_\")) | .id" | head -1 | tr -d '\r')
if [ -n "$TEST_USER_ID" ] && [ "$TEST_USER_ID" != "null" ]; then
  do_request GET "$API/users/$TEST_USER_ID" "" "$ADMIN_TOKEN"
  assert_status "GET /users/$TEST_USER_ID" 200 "$RESP_CODE"
else
  echo -e "  ${YELLOW}⚠${NC} Skipped — test user not found"
fi

# ── 3.5 Update user (admin) ──────────────────────────────────
section "3.5 Update User"
if [ -n "$TEST_USER_ID" ] && [ "$TEST_USER_ID" != "null" ]; then
  do_request PUT "$API/users/$TEST_USER_ID" \
    '{"email":"updated_'$TIMESTAMP'@finvault.com","fullName":"Updated Test User '$TIMESTAMP'"}' \
    "$ADMIN_TOKEN"
  assert_status "PUT /users/$TEST_USER_ID" 200 "$RESP_CODE"
  assert_json_field "Updated email" "$RESP_BODY" ".data.email" "updated_$TIMESTAMP@finvault.com"
fi

# ── 3.6 Assign roles ─────────────────────────────────────────
section "3.6 Assign Roles"
if [ -n "$TEST_USER_ID" ] && [ "$TEST_USER_ID" != "null" ]; then
  do_request PATCH "$API/users/$TEST_USER_ID/roles" \
    '{"roles":["ROLE_ANALYST","ROLE_VIEWER"]}' \
    "$ADMIN_TOKEN"
  assert_status "PATCH /users/$TEST_USER_ID/roles" 200 "$RESP_CODE"
fi

# ── 3.7 Update status ────────────────────────────────────────
section "3.7 Update User Status"
if [ -n "$TEST_USER_ID" ] && [ "$TEST_USER_ID" != "null" ]; then
  do_request PATCH "$API/users/$TEST_USER_ID/status?status=INACTIVE" "" "$ADMIN_TOKEN"
  assert_status "PATCH /users/$TEST_USER_ID/status=INACTIVE" 200 "$RESP_CODE"

  do_request PATCH "$API/users/$TEST_USER_ID/status?status=ACTIVE" "" "$ADMIN_TOKEN"
  assert_status "PATCH /users/$TEST_USER_ID/status=ACTIVE" 200 "$RESP_CODE"
fi

# ── 3.8 Change password ──────────────────────────────────────
section "3.8 Change Password"
do_request PUT "$API/users/me/password" \
  '{"currentPassword":"test123456","newPassword":"newpass123456"}' \
  "$TEST_TOKEN"
assert_status "PUT /users/me/password" 200 "$RESP_CODE"

# ── 3.9 Delete user (admin) ──────────────────────────────────
section "3.9 Delete User"
if [ -n "$TEST_USER_ID" ] && [ "$TEST_USER_ID" != "null" ]; then
  do_request DELETE "$API/users/$TEST_USER_ID" "" "$ADMIN_TOKEN"
  assert_status "DELETE /users/$TEST_USER_ID" 200 "$RESP_CODE"
fi


# ═════════════════════════════════════════════════════════════
# 4. DASHBOARD ANALYTICS
# ═════════════════════════════════════════════════════════════
banner "4. Dashboard Analytics"

# ── 4.1 Summary ──────────────────────────────────────────────
section "4.1 Dashboard Summary"
do_request GET "$API/dashboard/summary" "" "$ADMIN_TOKEN"
assert_status "GET /dashboard/summary (admin)" 200 "$RESP_CODE"
assert_json_field "Has totalIncome" "$RESP_BODY" ".data.totalIncome | type" "number"

do_request GET "$API/dashboard/summary?startDate=2025-01-01&endDate=2025-06-30" "" "$ANALYST_TOKEN"
assert_status "GET /dashboard/summary (analyst, date range)" 200 "$RESP_CODE"

section "4.2 Summary Denied (Viewer)"
do_request GET "$API/dashboard/summary" "" "$VIEWER_TOKEN"
assert_status "GET /dashboard/summary (viewer → 403)" 403 "$RESP_CODE"

# ── 4.3 Category breakdown ───────────────────────────────────
section "4.3 Category Breakdown"
do_request GET "$API/dashboard/category-breakdown" "" "$ADMIN_TOKEN"
assert_status "GET /dashboard/category-breakdown" 200 "$RESP_CODE"

# ── 4.4 Monthly trend ────────────────────────────────────────
section "4.4 Monthly Trend"
do_request GET "$API/dashboard/monthly-trend?startDate=2025-01-01&endDate=2025-06-30" "" "$ANALYST_TOKEN"
assert_status "GET /dashboard/monthly-trend" 200 "$RESP_CODE"

# ── 4.5 Recent activity ──────────────────────────────────────
section "4.5 Recent Activity"
do_request GET "$API/dashboard/recent-activity" "" "$VIEWER_TOKEN"
assert_status "GET /dashboard/recent-activity (viewer)" 200 "$RESP_CODE"

do_request GET "$API/dashboard/recent-activity" "" "$ADMIN_TOKEN"
assert_status "GET /dashboard/recent-activity (admin)" 200 "$RESP_CODE"


# ═════════════════════════════════════════════════════════════
# 5. AI FEATURES (best-effort — requires GEMINI_API_KEY)
# ═════════════════════════════════════════════════════════════
banner "5. AI Features"

section "5.1 Categorize (Analyst)"
do_request POST "$API/ai/categorize" \
  '{"description":"Paid electricity bill for March"}' \
  "$ANALYST_TOKEN"
if [ "$RESP_CODE" -eq 200 ]; then
  assert_status "POST /ai/categorize" 200 "$RESP_CODE"
  echo -e "  ${GREEN}✓${NC} Suggested category: $(echo "$RESP_BODY" | jq -r '.data.category')"
elif [ "$RESP_CODE" -eq 500 ] || [ "$RESP_CODE" -eq 503 ]; then
  echo -e "  ${YELLOW}⚠${NC} AI endpoint returned $RESP_CODE (Gemini API key may not be configured — skipping)"
else
  assert_status "POST /ai/categorize" 200 "$RESP_CODE"
fi

section "5.2 Categorize Denied (Viewer)"
do_request POST "$API/ai/categorize" \
  '{"description":"test"}' \
  "$VIEWER_TOKEN"
assert_status "POST /ai/categorize (viewer → 403)" 403 "$RESP_CODE"

section "5.3 Financial Insights"
do_request GET "$API/ai/insights" "" "$ADMIN_TOKEN"
if [ "$RESP_CODE" -eq 200 ]; then
  assert_status "GET /ai/insights" 200 "$RESP_CODE"
elif [ "$RESP_CODE" -eq 500 ] || [ "$RESP_CODE" -eq 503 ]; then
  echo -e "  ${YELLOW}⚠${NC} AI endpoint returned $RESP_CODE (Gemini API key may not be configured — skipping)"
else
  assert_status "GET /ai/insights" 200 "$RESP_CODE"
fi


# ═════════════════════════════════════════════════════════════
# 6. ACTUATOR / HEALTH
# ═════════════════════════════════════════════════════════════
banner "6. Actuator & Health"

section "6.1 Health Check"
do_request GET "$ACTUATOR/health" "" ""
assert_status "GET /actuator/health" 200 "$RESP_CODE"
assert_json_field "Status is UP" "$RESP_BODY" ".status" "UP"

section "6.2 Info"
do_request GET "$ACTUATOR/info" "" ""
assert_status "GET /actuator/info" 200 "$RESP_CODE"

section "6.3 Prometheus Metrics"
TOTAL=$((TOTAL + 1))
PROM_RESP=$(curl -s -o /dev/null -w "%{http_code}" "$ACTUATOR/prometheus" 2>/dev/null || echo "000")
if [ "$PROM_RESP" -eq 200 ]; then
  echo -e "  ${GREEN}✓${NC} GET /actuator/prometheus ${GREEN}(HTTP 200)${NC}"
  PASS=$((PASS + 1))
else
  echo -e "  ${RED}✗${NC} GET /actuator/prometheus — got HTTP $PROM_RESP"
  FAIL=$((FAIL + 1))
fi


# ═════════════════════════════════════════════════════════════
# 7. SWAGGER / OPENAPI
# ═════════════════════════════════════════════════════════════
banner "7. API Documentation"

section "7.1 OpenAPI JSON"
TOTAL=$((TOTAL + 1))
OPENAPI_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/v3/api-docs" 2>/dev/null || echo "000")
if [ "$OPENAPI_CODE" -eq 200 ]; then
  echo -e "  ${GREEN}✓${NC} GET /v3/api-docs ${GREEN}(HTTP 200)${NC}"
  PASS=$((PASS + 1))
else
  echo -e "  ${RED}✗${NC} GET /v3/api-docs — got HTTP $OPENAPI_CODE"
  FAIL=$((FAIL + 1))
fi

section "7.2 Swagger UI"
TOTAL=$((TOTAL + 1))
SWAGGER_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/swagger-ui/index.html" 2>/dev/null || echo "000")
if [ "$SWAGGER_CODE" -eq 200 ]; then
  echo -e "  ${GREEN}✓${NC} GET /swagger-ui/index.html ${GREEN}(HTTP 200)${NC}"
  PASS=$((PASS + 1))
else
  echo -e "  ${RED}✗${NC} GET /swagger-ui/index.html — got HTTP $SWAGGER_CODE"
  FAIL=$((FAIL + 1))
fi


# ═════════════════════════════════════════════════════════════
# 8. EDGE CASES & VALIDATION
# ═════════════════════════════════════════════════════════════
banner "8. Edge Cases & Validation"

section "8.1 Record Validation"
do_request POST "$API/records" \
  '{"amount":-100,"type":"INVALID","category":"","description":"","date":"2099-12-31"}' \
  "$ADMIN_TOKEN"
assert_status "POST /records (invalid payload → 400)" 400 "$RESP_CODE"

section "8.2 Non-existent Record"
do_request GET "$API/records/999999" "" "$ADMIN_TOKEN"
assert_status "GET /records/999999 (→ 404)" 404 "$RESP_CODE"

section "8.3 Non-existent User"
do_request GET "$API/users/999999" "" "$ADMIN_TOKEN"
assert_status "GET /users/999999 (→ 404)" 404 "$RESP_CODE"

section "8.4 Duplicate Registration"
do_request POST "$API/auth/register" \
  '{"username":"admin","email":"admin@finvault.com","password":"admin123","fullName":"Dup Admin"}'
assert_status "POST /auth/register (duplicate → 409)" 409 "$RESP_CODE"


# ═════════════════════════════════════════════════════════════
# RESULTS
# ═════════════════════════════════════════════════════════════
echo ""
banner "Test Results"
echo ""
echo -e "  ${GREEN}Passed:${NC}  $PASS"
echo -e "  ${RED}Failed:${NC}  $FAIL"
echo -e "  ${BOLD}Total:${NC}   $TOTAL"
echo ""

if [ "$FAIL" -eq 0 ]; then
  echo -e "  ${GREEN}${BOLD}🎉 All tests passed!${NC}"
  echo ""
  exit 0
else
  echo -e "  ${RED}${BOLD}⚠  $FAIL test(s) failed.${NC}"
  echo ""
  exit 1
fi
