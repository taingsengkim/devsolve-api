# Problem API

All write operations require a Keycloak access token. Public reads return only
problems in the published lifecycle (`PUBLISHED`, `RESOLVED`, or `CLOSED`).
Authors and admins may retrieve non-public problems according to the service
access rules.

## Create a draft

```http
POST /api/v1/problems/drafts
Authorization: Bearer {{userToken}}
Content-Type: application/json
```

```json
{
  "categoryId": null,
  "title": "Why does my refresh token fail after deployment?",
  "sdlcPhase": "DEPLOYMENT",
  "description": "",
  "technologies": [
    {
      "name": "Node.js",
      "version": "18"
    }
  ],
  "tagIds": [],
  "tags": [
    "jwt",
    "authentication"
  ]
}
```

Drafts may temporarily omit category and description. The server obtains
`authorId` from the JWT subject.

## Create and immediately submit

```http
POST /api/v1/problems
Authorization: Bearer {{userToken}}
Content-Type: application/json
```

Use the same request structure. A submission requires:

- an active category with `scope=PROBLEM`
- a 10–180 character title
- a description of at least 30 characters
- at least one technology

The resulting status is `PENDING_APPROVAL`.

## Update and submit a draft

```http
PATCH /api/v1/problems/{{problemId}}
Authorization: Bearer {{userToken}}
Content-Type: application/json
```

Only supplied fields are changed. If `technologies`, `tagIds`, or `tags` are
supplied, that association set is replaced transactionally.

```http
POST /api/v1/problems/{{problemId}}/submit
Authorization: Bearer {{userToken}}
```

### Editing a problem that is still in the queue

`PATCH` also works on a problem sitting at `PENDING_APPROVAL`. The problem
stays pending and keeps its place in the queue, but the edit has to clear the
same bar a submission does, and the corrected version is what the automatic
check and the moderator then read.

That is the intended way out of an automatic hold: the author is told what the
check made of their post, fixes it, and saves — rather than waiting to be
rejected for a version they have already corrected.

## Why is my post still pending?

```http
GET /api/v1/me/auto-reviews/{{target}}/{{contentId}}
GET /api/v1/me/auto-reviews?target=PROBLEM&approved=false
Authorization: Bearer {{userToken}}
```

`target` is `PROBLEM` or `SHOWCASE`. Author-scoped: a verdict on somebody
else's post answers 404, and so does a post the check never ran on.

```json
{
  "target": "PROBLEM",
  "contentId": "…",
  "title": "Why does my refresh token fail after deployment?",
  "status": "HELD",
  "hold": "UNCLEAR",
  "reason": "Too little detail to tell what the post is about",
  "checkedAt": "2026-09-05T10:14:02",
  "message": "\"Why does my refresh token fail after deployment?\" did not have enough detail for the automatic check to place it, so a moderator will read it instead. A fuller description is usually all the check needs. The check's own words: \"Too little detail to tell what the post is about\""
}
```

`status` is `APPROVED`, `HELD`, or `NOT_CHECKED`. `NOT_CHECKED` means the
automation never ran — switched off, no model configured, out of quota — so
nothing in the response is a judgement of the writing. `message` is the whole
thing in one paragraph, already addressed to the author; a client that does not
want to branch on the fields above can render only that.

A `HELD` verdict also arrives as a notification when it happens. `NOT_CHECKED`
does not: it is an operational detail, and interrupting every author with it
whenever the check is switched off is noise. It is recorded here either way.

## Moderation

```http
GET /api/v1/admin/problems?status=PENDING_APPROVAL
Authorization: Bearer {{adminToken}}
```

```http
PATCH /api/v1/admin/problems/{{problemId}}/moderation
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

```json
{
  "status": "PUBLISHED"
}
```

The only accepted moderation outcomes are `PUBLISHED` and `REJECTED`.

## Public reads

```http
GET /api/v1/problems?page=0&size=20&sdlcPhase=TESTING&tag=java&technology=Spring%20Boot
GET /api/v1/problems/{{problemId}}
```

An authenticated author can list their own work:

```http
GET /api/v1/problems/mine
Authorization: Bearer {{userToken}}
```

## Related problems

Suggestions for somebody drafting a problem — published work that resembles
what they are typing, so they can find the answer instead of writing the
question. Public, and cheap enough to call between keystrokes.

```http
GET /api/v1/problems/related?q=flyway+checksum+mismatch&limit=5
GET /api/v1/problems/related?q=flyway+checksum+mismatch&excludeId={{problemId}}
```

`q` is the draft title, or whichever field the author is editing. Pass
`excludeId` when editing an existing problem so it cannot suggest itself.
`limit` defaults to 5 and is capped at 20.

Matching is trigram similarity, not substring search, so a paraphrase or a
typo still finds the older problem. Text under four characters returns an
empty list rather than matching most of the platform. Solved problems sort
first — the response carries `solved` so the panel can badge them — followed
by match quality, then view count.

The frontend owns the pacing: debounce until typing stops, and abort the
in-flight request before firing the next, or a slow response for an early
prefix lands after a fast one for a later prefix and replaces good
suggestions with stale ones.

## Attachments

```http
POST /api/v1/problems/{{problemId}}/attachments
Authorization: Bearer {{userToken}}
Content-Type: multipart/form-data

file={{binaryFile}}
```

Files are limited to 10 MiB. Supported types are PDF, DOC, DOCX, PNG, JPG,
JPEG, WEBP, sanitized TXT, and sanitized LOG. Response metadata contains a
controlled download endpoint, never the internal MinIO key.

```http
GET /api/v1/problems/{{problemId}}/attachments/{{attachmentId}}/download
DELETE /api/v1/problems/{{problemId}}/attachments/{{attachmentId}}
Authorization: Bearer {{userToken}}
```

The download endpoint checks problem visibility and redirects to a signed
five-minute object-storage URL.

## Delete and views

```http
DELETE /api/v1/problems/{{problemId}}
Authorization: Bearer {{userToken}}

POST /api/v1/problems/{{problemId}}/views
```

Delete is soft deletion. View increments are performed by one atomic database
update and are accepted only for the published lifecycle.
