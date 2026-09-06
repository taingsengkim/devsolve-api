# Moderation flags API

The report queue. Every endpoint here requires a Keycloak access token; the
`/api/v1/admin` half requires the `ADMIN` realm role.

A flag records a type and an ID and nothing else about what was reported, so a
client that wanted to show the queue had to fetch each reported thing itself —
twenty extra requests a page, each against a different endpoint, and each one
404ing the moment a moderator took the content down. The queue now resolves the
content in the same statement it reads the flag. One request a page, and the
content stays legible after it is removed.

## The queue

```http
GET /api/v1/admin/flags
    ?status=PENDING
    &flaggableType=SHOWCASE
    &reason=SPAM
    &search=flyway
    &sort=MOST_REPORTED
    &pageNumber=0
    &pageSize=20
Authorization: Bearer {{adminToken}}
```

| Parameter       | Values                                              |
|-----------------|-----------------------------------------------------|
| `status`        | `PENDING` (default), `REVIEWED`, `DISMISSED`        |
| `flaggableType` | `PROBLEM`, `SOLUTION`, `SHOWCASE`, `COMMENT`, `PROGRAM` |
| `reason`        | `SPAM`, `OFFENSIVE`, `DUPLICATE`, `OFF_TOPIC`, `OTHER` |
| `search`        | free text, up to 200 characters                     |
| `sort`          | `NEWEST` (default), `OLDEST`, `MOST_REPORTED`       |

`search` matches the reported writing itself, the person who wrote it, the
person who reported it, and the note the reporter left. Wildcards are escaped
rather than honoured, so searching `100%` finds a literal `100%`.

`sort` is a closed set, not a `field,direction` pair: two of the three orderings
are over values that are not columns of the flag table at all.

```json
{
  "id": "2ec0114a-236d-402e-9171-3c12f1e131de",
  "source": "USER",
  "reporter": {
    "id": "ecacd2dc-89f6-4c74-90ec-0aa9566baf9b",
    "name": "Taing Sengkim",
    "avatarUrl": "https://…",
    "reputation": 120
  },
  "flaggableType": "PROBLEM",
  "flaggableId": "a1445d37-c442-49af-8602-2dd054c29843",
  "reason": "OFF_TOPIC",
  "description": "Off topic submission",
  "status": "PENDING",
  "target": {
    "title": "Spring Boot Flyway Configuration",
    "snippet": "I have a problem with springboot flyway when running migrations…",
    "authorId": "a9b3c4d5-…",
    "authorName": "Spider Kim",
    "authorAvatarUrl": "https://…",
    "contentStatus": "PUBLISHED",
    "createdAt": "2026-08-14T08:15:00Z",
    "thumbnailUrl": null,
    "directUrl": "/community/problems/a1445d37-…"
  },
  "reportCountOnTarget": 3,
  "pendingReportCountOnTarget": 2,
  "allReasons": ["SPAM", "OFF_TOPIC"],
  "reviewedBy": null,
  "reviewedAt": null,
  "resolutionNote": null,
  "createdAt": "2026-08-14T07:52:34.168Z"
}
```

### `target`

Never null, including when the content is gone. A hard-deleted row comes back
as `contentStatus: "DELETED"` with everything else null, which is something the
UI can say plainly; an absent object would read as a bug.

`contentStatus` is one vocabulary across all five kinds — `PUBLISHED`,
`PENDING`, `DRAFT`, `REJECTED`, `REMOVED`, `DELETED`. The five kinds each have
their own status enum, and a moderator is asking one question of all of them:
can people see this right now? A problem that is `RESOLVED` or `CLOSED` is still
`PUBLISHED` here, because it is still on the site.

`title` is null for a comment, which has none. `snippet` is the opening of the
reported writing with whitespace collapsed, capped at 280 characters.

`directUrl` is relative to the site root. A solution has no page of its own, so
it links to its problem anchored (`#solution-…`); a comment links to whatever it
hangs off, likewise anchored. Null when there is nowhere to send anyone — the
content is gone, or it hangs off something the API does not know how to address.
The paths are deployment settings (`FRONTEND_PROBLEM_PATH`,
`FRONTEND_SHOWCASE_PATH`, `FRONTEND_PROGRAM_PATH`, `FRONTEND_REPORT_PATH`), so a
frontend route rename does not need a release.

### The counts

`reportCountOnTarget` counts every report ever raised on that content, across
every status. It deliberately ignores the filter that produced the row: a queue
filtered to `PENDING` that also counted only pending reports would say "1
report" about a post five people reported and one colleague already dismissed.

`pendingReportCountOnTarget` is how many are still open — what a single decision
would close.

All three of these are **null on `GET /api/v1/flags/mine`**. How many other
people reported the same post is moderation-internal; told to the reporter it
becomes a number to watch and a reason to organise more of them.

## Grouped: one card per reported thing

```http
GET /api/v1/admin/flags/grouped?status=PENDING&sort=MOST_REPORTED
Authorization: Bearer {{adminToken}}
```

Ten people reporting one spam post is one decision, not ten. Same filters as the
flat queue; `sort` defaults to `MOST_REPORTED` here.

```json
{
  "flaggableType": "PROBLEM",
  "flaggableId": "a1445d37-…",
  "target": { "…": "as above" },
  "reportCount": 10,
  "pendingCount": 8,
  "reasons": ["SPAM", "OFF_TOPIC"],
  "automated": true,
  "firstReportedAt": "2026-08-14T07:52:34.168Z",
  "lastReportedAt": "2026-08-15T09:03:11.900Z",
  "latestFlagId": "2ec0114a-…"
}
```

Its own path rather than a flag on the listing above: the rows are a different
shape, and an endpoint whose response schema depends on a query parameter is one
no generated client can type.

The filters select which content appears; the counts on a card are always of
every report on it. `automated` says the content filter is among the reporters.

## Acting on a card

```http
PATCH /api/v1/admin/flags/targets/{flaggableType}/{flaggableId}/resolve
PATCH /api/v1/admin/flags/targets/{flaggableType}/{flaggableId}/dismiss
Authorization: Bearer {{adminToken}}
Content-Type: application/json

{ "resolutionNote": "Advertising.", "removeContent": true }
```

Closes every open report on that content at once, and — for `resolve` with
`removeContent` — takes the content down once, however many people reported it.
`dismiss` takes no body.

```json
{
  "flaggableType": "PROBLEM",
  "flaggableId": "a1445d37-…",
  "affected": 8,
  "contentRemoved": true
}
```

`affected: 0` is not an error. Two moderators working one queue will reach the
same card, and the second one has nothing left to close. With `removeContent`
the content still comes down — an admin who ticked that box asked about the
content, not about the paperwork.

The per-flag `PATCH /api/v1/admin/flags/{id}/resolve` and `/dismiss` are
unchanged and still there for acting on one report.

## Badge counts

```http
GET /api/v1/admin/flags/summary
Authorization: Bearer {{adminToken}}
```

```json
{
  "totalPending": 86,
  "totalResolved": 142,
  "totalDismissed": 35,
  "byReason": { "SPAM": 45, "OFFENSIVE": 20, "OFF_TOPIC": 15, "DUPLICATE": 4, "OTHER": 2 },
  "byType": { "PROBLEM": 38, "SOLUTION": 24, "SHOWCASE": 16, "COMMENT": 8, "PROGRAM": 0 }
}
```

The two breakdowns describe the **open** queue only — they are read as "what is
waiting for me", and counting closed reports would make every number grow
forever while the work did not. The three totals are of everything.

Every enum value is present, zeros included: a tab that disappears when its
count reaches zero is a tab nobody can use to check that it reached zero.

Its own endpoint rather than a field on the page, so turning a page does not
recompute it.

## A reporter's own list

```http
GET /api/v1/flags/mine?status=PENDING&pageNumber=0&pageSize=20
Authorization: Bearer {{userToken}}
```

The same row shape, including `target` — a reporter looking at their own list is
asking what they reported, and two UUIDs did not answer that either. The three
sibling counts are null; see above.
