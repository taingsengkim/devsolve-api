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
VIRUSTOTAL_MAX_POLLS=6
VIRUSTOTAL_FAIL_OPEN=false
```

## How a file is judged

The file's SHA-256 is looked up first. VirusTotal answers for content it has
seen before immediately, from its stored analysis — one request, no upload, no
queue, a verdict in well under a second. That covers the overwhelming majority
of uploads and all content already known to be malware, so the ordinary upload
pays no waiting at all and uses a quarter of the quota it used to.

Only content VirusTotal has never seen is submitted and polled. That is the
case worth waiting for, and it is genuinely slow: a new file is dispatched to
around seventy engines and routinely takes minutes rather than seconds.

`VIRUSTOTAL_POLL_INTERVAL` is the ceiling on one wait, not a flat delay. The
first check happens at a quarter of it, the next at a half, the rest at the
full value. With the defaults above, an unknown file is waited on for about 95
seconds before the upload is refused.

`VIRUSTOTAL_FAIL_OPEN` decides what happens when VirusTotal returns no verdict
at all — unreachable, rate limited, or still analysing after the last poll.

**Leave it `false`.** Unscanned content is not stored, because "not scanned"
and "scanned and clean" are not the same file. With it on, the reliable way to
get any file past the guard is to upload something VirusTotal has never seen —
which is a description of novel malware, the exact category the guard exists
for. Turn it on only to keep uploads working through a VirusTotal outage, and
turn it back off afterwards. A returned MALICIOUS or SUSPICIOUS verdict rejects
with 422 either way.

## Alerting

A file that comes back MALICIOUS or SUSPICIOUS is refused with 422 and never
stored, and the refusal is reported before the response is sent:

- always written to the application log, with the verdict, the filename, the
  uploader, the analysis id and the engine counts;
- delivered as a `SECURITY` notification to every platform administrator, and,
  when the upload was headed for a company's report, to everyone on that
  organization with `TRIAGE_REPORTS`.

The alert is dispatched in its own transaction. The upload's transaction is
rolled back by the 422, so an ordinary after-commit notification would be
published into a transaction that never commits and would silently never
arrive. Nothing in the alerting path can turn a refusal into a 500: if the
notification fails, it is logged and the file is still refused.

## Incident history

Every refusal is also persisted to `security_incidents`, written with
`REQUIRES_NEW` for the same reason the notification is dispatched that way.
The file itself is never stored; `sha256_hash` is what identifies it, and is
the value to paste into VirusTotal or a threat feed to see what it was.

The uploader's handle and email and the organization's name are copied onto
the row rather than joined at read time. An incident has to stay readable
after the account that caused it is deleted or renamed — a foreign key with
`ON DELETE CASCADE` would erase the record of what somebody did by deleting
them, and one without it would block the deletion.

Two endpoints read it, both authenticated and neither public — a row names a
researcher, the company they were testing, and a file hash:

```text
GET /api/v1/admin/security/incidents
GET /api/v1/organizations/{orgId}/security/incidents
```

The first requires the platform `ADMIN` role. The second requires
`TRIAGE_REPORTS` on that organization, with platform admins exempt — somebody
who cannot see the finding has no business seeing what was uploaded to it.

Both accept `search` (free text over uploader handle and email, filename and
SHA-256), `verdict` (`MALICIOUS` or `SUSPICIOUS`; only refused uploads are
recorded, so `CLEAN` and `PENDING` never match), `page`, `size` (default 20,
capped at 100) and `sort` (`blockedAt`, `filename` or `verdict`, newest-first
by default; any other name is a 400). The admin endpoint also accepts
`organizationId` to narrow to one company.

The table is created by `schema.sql`. The VPS does not run
`ddl-auto=update`, so it would otherwise ship without its table.

Never expose `VIRUSTOTAL_API_KEY` to the frontend or commit it. A public
VirusTotal API key has strict quotas and usage restrictions. Files submitted to
the standard `/files` endpoint are shared with VirusTotal; use an appropriate
VirusTotal license and private-scanning API before sending confidential files.

When enabled, the normal problem, solution, and report attachment upload flows
wait for a completed VirusTotal verdict before storing a file. Report
`targetEndpoint` values that are HTTP(S) URLs and every report reference link
are checked the same way before the report is saved. A suspicious or malicious
verdict returns HTTP 422. An analysis that remains pending after the configured
polls is treated as no verdict, so it follows `VIRUSTOTAL_FAIL_OPEN` above —
HTTP 504 with the default. Image-only uploads (avatars, logos, covers, and
showcase images) are intentionally excluded from public VirusTotal file
submission because they can contain personal or confidential material.

Because the wait is synchronous, clients must allow for it. A known file
returns in well under a second; an unknown one can hold the request for the
full poll budget. Set the client timeout above `VIRUSTOTAL_MAX_POLLS ×
VIRUSTOTAL_POLL_INTERVAL`, and any reverse proxy in front of the API to match —
nginx's default `proxy_read_timeout` of 60s will cut off an unknown-file upload
that the server goes on to complete.

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
