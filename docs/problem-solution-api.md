# Problem and solution API

## Create a problem

`POST /api/v1/problems` submits a problem for moderation. `POST
/api/v1/problems/drafts` stores it as a draft. Both requests require
`categoryId`, `title`, `description`, and `problemType`; `sdlcPhase` is
optional.

```json
{
  "title": "Spring Boot returns 403 after JWT refresh",
  "description": "After refreshing an expired access token, protected requests fail.",
  "problemType": "BUG",
  "categoryId": "00000000-0000-0000-0000-000000000000",
  "sdlcPhase": "DEVELOPMENT",
  "severity": "HIGH",
  "expectedBehavior": "The refreshed token authorizes requests.",
  "actualBehavior": "The API returns HTTP 403.",
  "reproductionSteps": [
    "Log in",
    "Wait for the token to expire",
    "Refresh it and call a protected endpoint"
  ],
  "environment": [
    {"technology": "Java", "version": "21"},
    {"technology": "Spring Boot", "version": "3.5"}
  ],
  "tagIds": [],
  "newTagNames": ["jwt"]
}
```

`BUG` submissions require `expectedBehavior`, `actualBehavior`, and at least
one reproduction step. Updates use `PATCH /api/v1/problems/{id}` and require
the numeric ETag returned by `GET /api/v1/problems/{id}`:

```http
If-Match: "7"
```

## Create and revise a solution

`POST /api/v1/problems/{problemId}/solutions` creates a permanent solution
identity and a pending revision.

```json
{
  "summary": "Refresh the security context after JWT rotation",
  "bodyMarkdown": "## Cause\nThe refreshed token was valid, but the context was stale.",
  "approachType": "FIX",
  "verificationSteps": [
    {
      "instruction": "Refresh the token and repeat the request",
      "expectedResult": "The API returns 200"
    }
  ],
  "testedWith": [
    {"technology": "Java", "version": "21"}
  ],
  "tradeoffs": "Previously issued refresh tokens may need revocation.",
  "resources": [
    {
      "type": "DOCUMENTATION",
      "label": "Spring Security documentation",
      "url": "https://example.com/documentation"
    }
  ]
}
```

Editing uses `PATCH /api/v1/solutions/{id}` with `If-Match`. Every edit makes
a new `PENDING` revision. If the solution was already public, its prior
approved revision remains visible until the new revision is approved.
Moderation data is present for the author and admins and omitted from public
solution responses. Attachments use the dedicated multipart endpoints under
`/api/v1/solutions/{id}/attachments` and require the same `If-Match` header.
An attachment-only change to an approved solution automatically opens a new
pending revision, leaving the published attachment set untouched.

## Acceptance

Acceptance is stored on the problem and never changes revision moderation:

```http
PUT /api/v1/problems/{problemId}/accepted-solution
Content-Type: application/json

{"solutionId":"00000000-0000-0000-0000-000000000000"}
```

Only an approved solution belonging to that problem can be accepted. The
problem owner or an admin may replace it atomically. Use `DELETE
/api/v1/problems/{problemId}/accepted-solution` to remove acceptance and move
a resolved problem back to `PUBLISHED`.
