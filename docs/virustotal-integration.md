# VirusTotal integration

The API exposes authenticated endpoints for submitting an existing DevSolve
attachment type or an HTTP(S) URL to VirusTotal. VirusTotal analyses are
asynchronous: the submit endpoints return an analysis ID, and callers poll the
analysis endpoint until `status` is `completed`.

## Configuration

Set these only on the backend:

```properties
VIRUSTOTAL_ENABLED=true
VIRUSTOTAL_API_KEY=<rotated-api-key>
VIRUSTOTAL_BASE_URL=https://www.virustotal.com/api/v3
VIRUSTOTAL_POLL_INTERVAL=20s
VIRUSTOTAL_MAX_POLLS=3
```

Never expose `VIRUSTOTAL_API_KEY` to the frontend or commit it. A public
VirusTotal API key has strict quotas and usage restrictions. Files submitted to
the standard `/files` endpoint are shared with VirusTotal; use an appropriate
VirusTotal license and private-scanning API before sending confidential files.

When enabled, the normal problem, solution, and report attachment upload flows
automatically wait for a completed VirusTotal verdict before storing a file.
Report `targetEndpoint` values that are HTTP(S) URLs and every report reference
link are checked the same way before the report is saved. A suspicious or
malicious verdict returns HTTP 422; an analysis that remains pending after the
configured polls returns HTTP 504. Image-only uploads (avatars, logos, covers,
and showcase images) are intentionally excluded from public VirusTotal file
submission because they can contain personal or confidential material.

## API flow

All endpoints require the normal DevSolve bearer token.

Submit a file (`multipart/form-data`, field name `file`):

```text
POST /api/v1/virus-total/files
```

Submit a URL:

```http
POST /api/v1/virus-total/urls
Content-Type: application/json

{"url":"https://example.com/download"}
```

Both submit endpoints return HTTP `202 Accepted`:

```json
{
  "analysisId": "...",
  "status": "queued",
  "verdict": "PENDING",
  "stats": {}
}
```

Poll the analysis without a tight loop, especially with a public API key:

```text
GET /api/v1/virus-total/analyses/{analysisId}
```

Completed results are reduced to one of `CLEAN`, `SUSPICIOUS`, or `MALICIOUS`,
while the original VirusTotal category counts remain available in `stats`.
Only content with a completed `CLEAN` verdict should proceed to the existing
attachment or URL creation endpoint.
