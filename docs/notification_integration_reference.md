# 🔔 Notification System — Integration Reference

> **For Next.js frontend developers.** This document describes the full contract of the notification system — types, endpoints, SSE protocol, and data relationships. No implementation details.

---

## TypeScript Types

### Core types

```ts
type NotificationType =
  | 'COMMENT'
  | 'REPORT'
  | 'PROGRAM'
  | 'SOLUTION'
  | 'PROBLEM'
  | 'KYC'
  | 'ORGANIZATION'
  | 'INVITATION'
  | 'DISPUTE'
  | 'RECOGNITION'
  | 'SHOWCASE'

type Notification = {
  id:             string | null   // UUID — null only on bulk follower SSE push events
  title:          string
  content:        string
  notifiableType: NotificationType
  notifiableId:   string          // UUID of the related entity
  read:           boolean
  readAt:         string | null   // ISO-8601 LocalDateTime, e.g. "2025-08-13T10:30:00"
  createdAt:      string          // ISO-8601 LocalDateTime, always present
}
```

> **`createdAt` / `readAt` format:** Java `LocalDateTime` serializes as `"YYYY-MM-DDTHH:mm:ss"` — **no timezone suffix**. Treat as the server's local time (UTC+7 for your deployment). Parse with `new Date(value)` but be aware it may interpret as local browser time.

### Paginated response (Spring `Page<T>`)

```ts
type Page<T> = {
  content:          T[]
  totalElements:    number   // total records across all pages
  totalPages:       number
  number:           number   // current page index (0-based)
  size:             number   // page size requested
  numberOfElements: number   // items in this page
  first:            boolean
  last:             boolean
  empty:            boolean
}

type NotificationPage = Page<Notification>
```

### Other response shapes

```ts
type UnreadCountResponse = {
  unreadCount: number   // long — total unread notifications for current user
}
```

---

## Endpoints

All endpoints require: `Authorization: Bearer <token>`  
Base path: `/api/v1/notifications`  
All responses are `application/json`.

---

### 1. `GET /api/v1/notifications` — Paginated inbox

**Purpose:** Fetch the current user's notification history. Sorted newest-first.

**Request — query params:**

| Param | Type | Default | Constraints | Description |
|---|---|---|---|---|
| `pageNumber` | `number` | `0` | `≥ 0` | Page index (0-based) |
| `pageSize` | `number` | `20` | `1–100` | Items per page |
| `unreadOnly` | `boolean` | `false` | — | `true` = only unread notifications |

**Response:** `200 OK` → `NotificationPage`

```json
{
  "content": [
    {
      "id": "3f7a2c1d-1234-5678-abcd-000000000001",
      "title": "New problem published",
      "content": "Binary Trees Explained",
      "notifiableType": "PROBLEM",
      "notifiableId": "9b1c0000-0000-0000-0000-000000000042",
      "read": false,
      "readAt": null,
      "createdAt": "2025-08-12T17:00:00"
    }
  ],
  "totalElements": 47,
  "totalPages": 3,
  "number": 0,
  "size": 20,
  "numberOfElements": 20,
  "first": true,
  "last": false,
  "empty": false
}
```

---

### 2. `GET /api/v1/notifications/unread-count` — Badge count

**Purpose:** Lightweight poll for the bell badge. Only returns the count — no notification data.

**Request:** No params, no body.

**Response:** `200 OK` → `UnreadCountResponse`

```json
{ "unreadCount": 5 }
```

---

### 3. `PATCH /api/v1/notifications/{notificationId}/read` — Mark one as read

**Purpose:** Mark a single notification as read. Only the owner can do this (scoped to current user by the backend).

**Request:**

| Part | Value |
|---|---|
| `{notificationId}` | UUID string — the notification's `id` |
| Body | Empty |

**Response:** `200 OK` → `Notification` (the updated notification with `read: true` and `readAt` set)

```json
{
  "id": "3f7a2c1d-1234-5678-abcd-000000000001",
  "title": "New problem published",
  "content": "Binary Trees Explained",
  "notifiableType": "PROBLEM",
  "notifiableId": "9b1c0000-0000-0000-0000-000000000042",
  "read": true,
  "readAt": "2025-08-12T17:05:12",
  "createdAt": "2025-08-12T17:00:00"
}
```

> **Error `404`** — if the notification doesn't exist or doesn't belong to the current user.

---

### 4. `PATCH /api/v1/notifications/read-all` — Mark all as read

**Purpose:** Marks every unread notification as read for the current user in one call.

**Request:** No params, no body.

**Response:** `204 No Content` — empty body.

---

### 5. `GET /api/v1/notifications/stream` — SSE real-time stream

**Purpose:** Long-lived connection. Server pushes a new event every time the current user receives a notification.

**Request:**

| Header | Value |
|---|---|
| `Authorization` | `Bearer <token>` |
| `Accept` | `text/event-stream` |

> ⚠️ Native browser `EventSource` cannot send custom headers. You must use a fetch-based SSE library (e.g. `@microsoft/fetch-event-source`) to send the `Authorization` header.

**Connection stays open** until:
- Client closes it (abort / logout)
- Server restarts
- Network drops

The server sends a `heartbeat` comment every **30 seconds** to keep the connection alive through proxies. Ignore it.

**SSE event format received:**

```
id: 3f7a2c1d-1234-5678-abcd-000000000001
event: notification
data: {"id":"3f7a2c1d-...","title":"New problem published","content":"Binary Trees Explained","notifiableType":"PROBLEM","notifiableId":"9b1c...","read":false,"readAt":null,"createdAt":"2025-08-12T17:00:00"}

: heartbeat
```

| SSE field | Value |
|---|---|
| `event` | Always `"notification"` |
| `id` | The notification UUID — or `"bulk-{timestamp}"` for follower bulk push events |
| `data` | JSON string — deserializes to `Notification` |

> **`id: null` in data vs SSE `id`:** For bulk follower notifications (problem published, program updated, etc.), the SSE `id` header is set to `"bulk-{timestamp}"` because the batch SQL insert doesn't return individual row IDs. However, the actual `id` field in the `data` JSON payload will be `null`. When the user opens the inbox panel, fetch the REST endpoint — it will return the stored notifications with their real UUIDs.

**Response:** `200 OK`, `Content-Type: text/event-stream`

---

## Notification Triggers Catalog

Every notification that the system can produce, what causes it, and who receives it.

| Event | `notifiableType` | `title` | Who receives | Trigger |
|---|---|---|---|---|
| Problem published | `PROBLEM` | `"New problem published"` | All followers of the **problem author** | Admin approves a problem |
| Solution approved | `SOLUTION` | `"New solution posted"` | All followers of the **problem** | Admin approves a solution revision |
| Program published | `PROGRAM` | `"New program published"` | All followers of the **organization** | Admin approves a new program |
| Program updated | `PROGRAM` | `"Program updated"` | All followers of the **program** | A program update is logged (while approved & public) |
| Showcase published | `SHOWCASE` | `"New showcase published"` | All followers of the **showcase author** | Showcase passes review |
| Showcase updated | `SHOWCASE` | `"Showcase updated"` | All followers of the **showcase** | A showcase revision is approved |
| Report disclosed | `REPORT` | `"New disclosed security report"` | All followers of the **reporter** | An org discloses a resolved security report |
| Org registered | `ORGANIZATION` | `"New organization registration"` | All **ADMIN users** | New org registration submitted |
| Org resubmitted | `ORGANIZATION` | `"Organization registration resubmitted"` | All **ADMIN users** | Rejected org re-submits registration |
| Org approved | `ORGANIZATION` | `"Organization approved"` | The org **owner** | Admin approves the org |
| Org rejected | `ORGANIZATION` | `"Organization registration rejected"` | The org **owner** | Admin rejects the org |

---

## `notifiableType` → Deep Link Route Mapping

Use `notifiableType` + `notifiableId` together to navigate the user to the relevant content when they click a notification.

| `notifiableType` | Navigate to | Notes |
|---|---|---|
| `PROBLEM` | `/problems/{notifiableId}` | Problem detail page |
| `SOLUTION` | `/problems/{?}/solutions/{notifiableId}` | Need to fetch problem ID from solution, or navigate to solution directly |
| `PROGRAM` | `/programs/{notifiableId}` | Program detail page |
| `SHOWCASE` | `/showcases/{notifiableId}` | Showcase detail page |
| `ORGANIZATION` | `/organizations/{notifiableId}` | Organization detail page |
| `REPORT` | `/reports/{notifiableId}` | Security report detail |
| `COMMENT` | Context-dependent | Depends on what was commented on |
| `INVITATION` | `/organizations/invitations` | User's invitation inbox |
| `KYC` | `/profile/kyc` | KYC status page |
| `DISPUTE` | `/disputes/{notifiableId}` | Dispute detail |
| `RECOGNITION` | `/recognitions/{notifiableId}` | Recognition detail |

---

## Data Relationships

```
User ──────────────────────── Notification (many)
                                    │
                         ┌──────────┴──────────┐
                    notifiableType          notifiableId
                         │                      │
                         ▼                      ▼
                  PROBLEM / SOLUTION /    UUID of that
                  PROGRAM / SHOWCASE /    specific entity
                  ORGANIZATION / ...
```

- One `Notification` belongs to **exactly one user** (`userId` scoped — you only ever see your own)
- `notifiableType` tells you **what kind of thing** triggered the notification
- `notifiableId` is the **UUID of that thing** — use it to build a deep link
- `read` / `readAt` track whether the user has seen it
- `createdAt` is always set (auto on insert)

---

## Full Integration Flow

```
1. User logs in
        │
        ▼
2. GET /api/v1/notifications/unread-count
   → show number on bell badge immediately

3. Open SSE stream: GET /api/v1/notifications/stream
   → stays open, auto-reconnects on drop

        │── server pushes event ──▶ increment badge count + show toast
        │                          (SSE data is a Notification object)
        │
4. User clicks bell
        │
        ▼
5. GET /api/v1/notifications?pageNumber=0&pageSize=20
   → render notification list (real UUIDs, all fields present)

6. User scrolls down
        │
        ▼
   GET /api/v1/notifications?pageNumber=1&pageSize=20
   → append next page (check `last` field to stop)

7. User clicks a notification (if unread)
        │
        ├─▶ PATCH /api/v1/notifications/{id}/read
        │   → update `read: true`, `readAt` in UI
        │   → decrement badge count
        │
        └─▶ Navigate to deep link using notifiableType + notifiableId

8. User clicks "Mark all read"
        │
        ▼
   PATCH /api/v1/notifications/read-all
   → 204 No Content
   → set badge = 0, mark all in local state as read

9. User logs out
        │
        ▼
   Abort the SSE connection (stop the stream)
   Clear notification state
```

---

## Auth Requirements

| Endpoint | Auth required |
|---|---|
| All `/api/v1/notifications/*` | ✅ Bearer JWT (Keycloak) |
| SSE stream | ✅ Bearer JWT — must be sent as `Authorization` header (not cookie) |

**Scoping:** All endpoints are automatically scoped to the currently authenticated user. No `userId` is ever passed — the backend extracts it from the JWT token. You cannot access another user's notifications.

---

## Constraints & Limits

| Rule | Value |
|---|---|
| `pageSize` min | `1` |
| `pageSize` max | `100` |
| `pageNumber` min | `0` |
| Heartbeat interval | Every `30 seconds` |
| SSE connection timeout | None (infinite) |
| Notification deduplication | By `(userId, eventKey)` — same event cannot notify the same user twice |
