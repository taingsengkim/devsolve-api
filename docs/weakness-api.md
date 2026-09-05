# Weakness API

The vulnerability class a report is filed under. A closed vocabulary on
purpose: its whole value is that it aggregates, and "injection is a third of
everything filed against this program" is only answerable while every injection
report carries the same identifier.

All of these require a Keycloak access token. The `/api/v1/admin` half requires
the `ADMIN` realm role.

## The picker

```http
GET /api/v1/weaknesses?search=89&page=0&size=20
GET /api/v1/weaknesses/{{weaknessId}}
Authorization: Bearer {{userToken}}
```

`search` matches the CWE identifier as well as the name, so both `89` and `sql`
find SQL Injection. Omitted, it returns everything active.

## What the platform actually receives

```http
GET /api/v1/weaknesses/popular?limit=10
Authorization: Bearer {{userToken}}
```

The classes most reported here, most first. Active catalog entries only, and
only ones something has been filed under — a submission form that would rather
show five classes this platform receives than thirty in alphabetical order.

```json
[
  {
    "id": "…",
    "cweId": "CWE-89",
    "name": "SQL Injection",
    "isActive": true,
    "reportCount": 41,
    "validCount": 27,
    "share": 18.4,
    "lastReportedAt": "2026-09-04T22:11:07"
  }
]
```

- `reportCount` — every report filed under it, whatever became of it.
  Duplicates and rejections included: this is how often the class is *seen*.
- `validCount` — the subset a triager agreed with (confirmed, retesting or
  resolved). The gap between the two is the noise a class attracts.
- `share` — `reportCount` as a percentage of every *classified* report, to one
  decimal place. Reports triage has not classified are left out of the
  denominator rather than counted as a class of their own, so these do not sum
  to 100 across a partial list and are not depressed by an unclassified backlog.

`limit` defaults to 10 and is capped at 50. The ordering is fixed, so a `sort`
parameter is refused rather than silently ignored.

## Admin: the catalog, ranked by use

```http
GET /api/v1/admin/weaknesses/stats?includeUnused=false&activeOnly=false&page=0&size=20
Authorization: Bearer {{adminToken}}
```

The same figures over the whole catalog, paged. `includeUnused=true` adds the
entries nothing has ever been filed under — the list for deciding what to
retire. `activeOnly` defaults to false, because the history under a retired
entry is exactly what says whether retiring it was right.

## Admin: what reporters typed instead

```http
GET /api/v1/admin/weaknesses/suggested?page=0&size=20
Authorization: Bearer {{adminToken}}
```

A reporter who finds nothing in the catalog that fits may name the class
themselves. That text stays on the report — it never writes to the catalog,
which is shared by every program and shown in every picker. This endpoint reads
those back grouped and counted, which is the platform's own record of where its
vocabulary is short.

```json
{
  "content": [
    {
      "name": "Prototype pollution",
      "reportCount": 6,
      "inCatalog": false,
      "firstSuggestedAt": "2026-07-02T09:31:44",
      "lastSuggestedAt": "2026-09-01T16:02:10"
    }
  ]
}
```

Grouped case-insensitively on the trimmed text, so `SSRF`, `ssrf ` and `Ssrf`
are one gap reported three times rather than three gaps. `name` is one of the
spellings reporters actually used.

`inCatalog` is the interesting flag. True means the catalog already has an
entry by that name and reporters are typing a class they could have picked —
the gap is in the picker or the search, not the vocabulary.

Promoting a suggestion is an ordinary create:

```http
POST /api/v1/admin/weaknesses
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

```json
{
  "cweId": "CWE-1321",
  "name": "Prototype pollution",
  "description": "…"
}
```

That adds the class for everyone from then on. It does not rewrite the reports
that suggested it — triage reclassifies each of those as it settles them, which
is also what clears the free-text field.

## Curation

```http
PATCH /api/v1/admin/weaknesses/{{weaknessId}}
DELETE /api/v1/admin/weaknesses/{{weaknessId}}
Authorization: Bearer {{adminToken}}
```

Delete is only for an entry no report has ever been filed under. One that
reports point at is retired with `{"isActive": false}` instead, which takes it
out of every picker and leaves those reports reading correctly.
