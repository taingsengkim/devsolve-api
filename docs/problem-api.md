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
