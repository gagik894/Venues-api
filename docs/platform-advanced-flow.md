# Platform API — Advanced Flow (Cart Hold → Checkout → Confirm → Release)

This guide summarizes how third parties should integrate the advanced flow securely and reliably.

## 1) Security & Auth

- **Headers (all platform endpoints)**
    - `X-Platform-ID`: your platform UUID
    - `X-Platform-Signature`: HMAC-SHA256 over `auth|{platformId}|{timestamp}|{nonce}|{bodyHash}` using your shared
      secret
    - `X-Platform-Timestamp`: ISO-8601 UTC, max ±5 minutes skew
    - `X-Platform-Nonce`: unique per request (replay-protected via Redis)
    - `Content-Type: application/json`
- **Body hash**: `SHA256` of raw request body (empty string => hash of empty payload).
- **Signature compare**: server uses constant-time check.
- **Idempotency**: send `Idempotency-Key` for all mutating calls (hold/checkout/confirm/release) to make retries safe.

### Signing example (Kotlin/JVM)

```kotlin
val data = "auth|$platformId|$timestamp|$nonce|$bodyHash"
val mac = Mac.getInstance("HmacSHA256").apply {
    init(SecretKeySpec(sharedSecret.toByteArray(), "HmacSHA256"))
}
val signature = mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
```

### Signing example (Node.js)

```js
const crypto = require('crypto');
const data = `auth|${platformId}|${timestamp}|${nonce}|${bodyHash}`;
const signature = crypto.createHmac('sha256', sharedSecret).update(data).digest('hex');
```

### Body hash (Python)

```py
import hashlib, json
body = json.dumps(payload, separators=(',', ':'), ensure_ascii=False).encode()
body_hash = hashlib.sha256(body).hexdigest()
```

## 2) Flow Overview

1) **Hold** `POST /api/v1/platforms/advanced/cart/hold` (or `/hold-simple` for batch)
    - Provide seats/GA/tables + optional `holdToken` to append.
    - Response: `holdToken`, `expiresAt`, items, pricing snapshot.
2) **Checkout** `POST /api/v1/platforms/advanced/cart/checkout`
    - Inputs: `holdToken`, optional guest info.
    - Response: totals, currency, items, `expiresAt`.
3) **Confirm** `POST /api/v1/platforms/advanced/cart/confirm`
    - Inputs: `holdToken`, payment refs.
    - Finalizes booking, inventory SOLD, tickets generated. Response has booking IDs/status.
4) **Release** `POST /api/v1/platforms/advanced/cart/release`
    - Inputs: `reservationToken` (holdToken).
    - Frees inventory if not confirmed.

## 3) Idempotency Keys (Required)

- Use a **stable key per business action** (e.g., `hold-{cartId}-{attempt}`) and reuse on retries of the same action.
- Server caches results for 24h; concurrent requests with same key serialize.
- Conflicts return `ResourceConflict`; retry with backoff or new key only if you are starting a fresh action.

## 4) Rate Limits

- Sliding window per platform (60s). Exceeding limit returns validation error with counts.
- Suggested client behavior: exponential backoff (e.g., 200ms, 400ms, 800ms) then surface error.

## 5) Error Handling Cheatsheet

- **401/403**: Missing/invalid signature, stale timestamp, reused nonce, inactive platform. Re-sign and retry; check
  clock sync.
- **422 / validation**: expired hold, wrong session, empty hold, over limit, capacity issues. Fix request or re-hold.
- **Conflict (idempotency)**: another request with same key in-flight; retry after short delay or use new key for new
  action.
- **Rate limit**: backoff and retry later.

## 6) Webhooks

- Events: seat/table reserved/released, GA availability change, booking confirmed.
- Headers:
    - `X-Venues-Signature`: HMAC-SHA256 over `webhook|platformId|timestamp|nonce|bodyHash` (sharedSecret)
    - `X-Venues-Timestamp` (ISO-8601 UTC), `X-Venues-Nonce` (unique), `X-Venues-Event-Type`
    - `X-Venues-Body-Hash`: SHA-256 hex of the JSON payload
- Verify server-side: recompute body hash, recompute signature, constant-time compare; reject stale timestamp or
  duplicate nonce.
- Implement 2xx acknowledgment; non-2xx will trigger retries with exponential backoff (up to configured attempts).

## 7) Sample cURL (Hold → Checkout → Confirm)

```sh
# Assume you computed headers/signature/bodyHash/idempotencyKey
curl -X POST https://api.yourhost.com/api/v1/platforms/advanced/cart/hold \
  -H "X-Platform-ID: <platform-uuid>" \
  -H "X-Platform-Signature: <hmac>" \
  -H "X-Platform-Timestamp: <iso-in-utc>" \
  -H "X-Platform-Nonce: <uuid-nonce>" \
  -H "Idempotency-Key: hold-123" \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<session-uuid>","seatIdentifiers":["A1","A2"]}'
```

Repeat with checkout/confirm using the `holdToken` from the hold response and **new** idempotency keys per action.

## 8) Best Practices for Integrators

- Keep clocks in sync (NTP); reject if drift > 5m.
- Persist `holdToken` per cart; expect `expiresAt` and re-hold if expired.
- Always send `Idempotency-Key` on hold/checkout/confirm/release.
- Log request IDs, idempotency keys, nonces for audit/support.
- Validate server responses rather than recalculating totals client-side.
- Backoff on rate-limit and idempotency conflicts; do not spam retries.

