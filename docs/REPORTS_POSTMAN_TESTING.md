# DevSolve Reports API — Postman Testing Guide

This guide tests the Reports feature against a running DevSolve backend. It
covers authentication, private report access, company triage, automatic
severity agreement, the reporter's answer to a downgrade, administrator
rulings, rewards, disclosure status, and role restrictions.

## 1. Prerequisites

Before opening Postman, make sure the following are available:

- The DevSolve API is running at `http://localhost:8999`.
- PostgreSQL contains the finalized DevSolve schema.
- Keycloak is running and the API accepts its access tokens.
- Every Keycloak test user's `sub` value exists as an ID in `user_profiles`.
- An approved, active organization exists.
- An approved, active program exists for that organization.
- The program has at least one in-scope asset.
- Use a program with `offersBounties = true` for the reward test.

Use these test accounts:

| Account | Realm role | Organization role | Purpose |
|---|---|---|---|
| Hacker A | `USER` | None | Creates and reads their reports |
| Hacker B | `USER` | None | Verifies private-report isolation |
| Company Owner | `COMPANY` | Owner | Reads, triages, and rewards reports |
| Company Viewer | `COMPANY` | Viewer | Reads reports but cannot change them |
| Admin | `ADMIN` | None | Reads all private reports |

Run the automated tests before manual API testing:

```powershell
.\gradlew.bat test --tests '*ReportServiceImplTest'
.\gradlew.bat test
```

Both commands should finish with `BUILD SUCCESSFUL`.

## 2. Verify the database trigger

Run this query in PostgreSQL:

```sql
SELECT tgname
FROM pg_trigger
WHERE tgrelid = 'public.reports'::regclass
  AND NOT tgisinternal;
```

The result should include:

```text
trg_reconcile_report_severity
```

If it is missing, start the API once against the existing schema so that
`src/main/resources/schema.sql` can install it.

`trg_open_report_severity_dispute` must **not** be listed. It used to open a
dispute from the database on any severity mismatch, straight into the
administrators' queue, which got there before the application could ask the
reporter anything. Dispute creation belongs to triage now, and `schema.sql`
drops the trigger on startup. If the query still returns it, the API has not
been restarted against the current schema.

## 3. Create a Postman environment

In Postman:

1. Open **Environments**.
2. Select **Create environment**.
3. Name it `DevSolve Local`.
4. Add the variables below.
5. Store passwords, client secrets, and tokens only in **Current value**.
6. Do not export or commit an environment containing real credentials.

| Variable | Example initial value |
|---|---|
| `baseUrl` | `http://localhost:8999/api/v1` |
| `keycloakUrl` | `http://localhost:8080` |
| `realm` | `devsolve` |
| `clientId` | Keycloak client ID |
| `clientSecret` | Keycloak client secret, if required |
| `hackerUsername` | Hacker A username |
| `hackerPassword` | Hacker A password |
| `otherHackerUsername` | Hacker B username |
| `otherHackerPassword` | Hacker B password |
| `companyUsername` | Company owner username |
| `companyPassword` | Company owner password |
| `viewerUsername` | Company viewer username |
| `viewerPassword` | Company viewer password |
| `adminUsername` | Admin username |
| `adminPassword` | Admin password |
| `hackerToken` | Leave empty |
| `otherHackerToken` | Leave empty |
| `companyToken` | Leave empty |
| `viewerToken` | Leave empty |
| `adminToken` | Leave empty |
| `programId` | Leave empty |
| `assetId` | Leave empty |
| `weaknessId` | Optional; leave empty |
| `reportId` | Leave empty |
| `mismatchReportId` | Leave empty |
| `upgradeReportId` | Leave empty |
| `disputeId` | Leave empty |

Select `DevSolve Local` as the active environment.

## 4. Create the Postman collection

Create a collection named `DevSolve Reports`.

Recommended folders:

```text
DevSolve Reports
├── 00 - Authentication
├── 01 - Test Data
├── 02 - Agreement Flow
├── 03 - Disagreement Flow
├── 04 - Privacy and Roles
└── 05 - Validation Errors
```

Unless a request says otherwise, set:

```text
Content-Type: application/json
```

Use **Bearer Token** authentication and select the token variable required by
each request, such as `{{hackerToken}}` or `{{companyToken}}`.

## 5. Obtain Keycloak tokens

This method requires **Direct Access Grants** to be enabled for the Keycloak
client. If your frontend login handles OAuth through Better Auth, sign in
through the frontend and paste the forwarded Keycloak access token into the
corresponding Postman environment variable instead.

### 5.1 Hacker A token

Create this request:

```text
POST {{keycloakUrl}}/realms/{{realm}}/protocol/openid-connect/token
```

Select **Body → x-www-form-urlencoded**:

| Key | Value |
|---|---|
| `grant_type` | `password` |
| `client_id` | `{{clientId}}` |
| `client_secret` | `{{clientSecret}}` |
| `username` | `{{hackerUsername}}` |
| `password` | `{{hackerPassword}}` |

If the Keycloak client is public, remove `client_secret`.

Add this script under **Scripts → Post-response**:

```javascript
pm.test("Hacker token returned", function () {
    pm.response.to.have.status(200);
    const json = pm.response.json();
    pm.expect(json.access_token).to.be.a("string");
    pm.environment.set("hackerToken", json.access_token);
});
```

### 5.2 Other tokens

Duplicate the request four times and change the username, password, and saved
token variable:

| Request | Username/password variables | Saved token |
|---|---|---|
| Hacker B Login | `otherHackerUsername`, `otherHackerPassword` | `otherHackerToken` |
| Company Login | `companyUsername`, `companyPassword` | `companyToken` |
| Viewer Login | `viewerUsername`, `viewerPassword` | `viewerToken` |
| Admin Login | `adminUsername`, `adminPassword` | `adminToken` |

For example, the Company post-response script should end with:

```javascript
pm.environment.set("companyToken", json.access_token);
```

## 6. Discover an active program and asset

Create a request named `Find Test Program`:

```text
GET {{baseUrl}}/programs?page=0&size=20
```

This endpoint is public and does not require a token.

Add this post-response script:

```javascript
pm.test("Public programs returned", function () {
    pm.response.to.have.status(200);
    const json = pm.response.json();
    pm.expect(json.content).to.be.an("array").that.is.not.empty;

    const program = json.content.find(item =>
        item.offersBounties === true &&
        Array.isArray(item.assets) &&
        item.assets.some(asset => asset.isInScope === true)
    );

    pm.expect(
        program,
        "Bounty program with an in-scope asset"
    ).to.exist;

    const asset = program.assets.find(item => item.isInScope === true);
    pm.environment.set("programId", program.id);
    pm.environment.set("assetId", asset.id);
});
```

If no public program is returned, use PostgreSQL to find the IDs:

```sql
SELECT id, organization_id, name, state, submission_state
FROM programs
WHERE state = 'active'
  AND submission_state = 'approved';

SELECT id, program_id, identifier
FROM program_assets
WHERE program_id = 'PROGRAM_UUID'
  AND is_in_scope = true;
```

Set `programId` and `assetId` manually in the Postman environment.

## 7. Agreement flow

### 7.1 Submit a report as Hacker A

Create a request named `Create Agreement Report`:

```text
POST {{baseUrl}}/programs/{{programId}}/reports
Authorization: Bearer {{hackerToken}}
```

Use **Body → raw → JSON**:

```json
{
  "title": "Broken access control exposes another user account",
  "vulnerabilityInformation": "The account endpoint authorises on the session but reads the identifier straight from the path, so any authenticated user can read any account.",
  "impact": "An attacker can access private account information belonging to other users.",
  "stepsToReproduce": "1. Log in as User A.\n2. Note User B's identifier.\n3. GET /api/v1/users/{userB}.\n4. The response body is User B's private profile.",
  "proofOfConcept": "curl -H \"Authorization: Bearer $USER_A\" https://app.example.test/api/v1/users/8f2c...",
  "remediationRecommendation": "Compare the path identifier against the authenticated principal before loading the profile.",
  "targetEndpoint": "https://app.example.test/api/v1/users/{id}",
  "environment": "PRODUCTION",
  "discoveredAt": "2026-08-14T09:30:00",
  "referenceLinks": [
    "https://owasp.org/Top10/A01_2021-Broken_Access_Control/"
  ],
  "reportedSeverity": "HIGH",
  "cvssVector": "CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:N/A:N",
  "cvssScore": 7.7,
  "assetId": "{{assetId}}"
}
```

Only `title`, `vulnerabilityInformation`, and `reportedSeverity` are required;
everything else is optional detail that saves triage a round trip.

- `targetEndpoint` is where the finding lives *inside* the asset. `assetId`
  still says which in-scope target it belongs to.
- `environment` is one of `PRODUCTION`, `STAGING`, `DEVELOPMENT`, `TESTING`,
  `LOCAL`.
- `weaknessId` is the CWE category and is optional. With an active weakness,
  add `"weaknessId": "{{weaknessId}}"` beside `assetId`.
- `cvssVector` must be a CVSS v3.0 or v3.1 **base** vector, and `cvssScore`
  must fall in the band its `reportedSeverity` claims — `0.1–3.9` LOW,
  `4.0–6.9` MEDIUM, `7.0–8.9` HIGH, `9.0–10.0` CRITICAL. A score that
  contradicts the claim is rejected with `400`, so the two can never disagree
  in the record. Send either, both, or neither.

Screenshots and other evidence are uploaded separately once the report exists,
via `POST {{baseUrl}}/reports/{{reportId}}/attachments`.

Add this post-response script:

```javascript
pm.test("Report created", function () {
    pm.response.to.have.status(201);
    const json = pm.response.json();

    pm.expect(json.state).to.eql("NEW");
    pm.expect(json.reportedSeverity).to.eql("HIGH");
    pm.expect(json.triageSeverity).to.eql(null);
    pm.expect(json.severity).to.eql(null);
    pm.expect(json.disclosureStatus).to.eql("NOT_DISCLOSED");

    pm.environment.set("reportId", json.id);
});
```

### 7.2 Read the report as its Hacker

```text
GET {{baseUrl}}/reports/{{reportId}}
Authorization: Bearer {{hackerToken}}
```

Post-response script:

```javascript
pm.test("Reporter can read private report", function () {
    pm.response.to.have.status(200);
    const json = pm.response.json();
    pm.expect(json.id).to.eql(pm.environment.get("reportId"));
});
```

### 7.3 List Hacker A's reports

```text
GET {{baseUrl}}/reports/mine?page=0&size=20
Authorization: Bearer {{hackerToken}}
```

Post-response script:

```javascript
pm.test("Hacker's report is listed", function () {
    pm.response.to.have.status(200);
    const json = pm.response.json();
    const reportId = pm.environment.get("reportId");
    pm.expect(json.content.some(item => item.id === reportId)).to.eql(true);
});
```

### 7.4 List reports as the Company

```text
GET {{baseUrl}}/programs/{{programId}}/reports?page=0&size=20
Authorization: Bearer {{companyToken}}
```

Expected status: `200 OK`.

### 7.5 Triage with matching severity

```text
PATCH {{baseUrl}}/reports/{{reportId}}/triage
Authorization: Bearer {{companyToken}}
```

Body:

```json
{
  "triageSeverity": "HIGH",
  "state": "VALID_CONFIRMED",
  "duplicateOfId": null
}
```

Post-response script:

```javascript
pm.test("Matching severity is finalized automatically", function () {
    pm.response.to.have.status(200);
    const json = pm.response.json();

    pm.expect(json.reportedSeverity).to.eql("HIGH");
    pm.expect(json.triageSeverity).to.eql("HIGH");
    pm.expect(json.severity).to.eql("HIGH");
    pm.expect(json.state).to.eql("VALID_CONFIRMED");
    pm.expect(json.dispute).to.eql(null);
});
```

### 7.6 Record an off-platform reward

```text
POST {{baseUrl}}/reports/{{reportId}}/rewards
Authorization: Bearer {{companyToken}}
```

Body:

```json
{
  "amount": 250.00,
  "points": 100,
  "note": "Reward confirmed as paid outside DevSolve."
}
```

Post-response script:

```javascript
pm.test("Reward record created", function () {
    pm.response.to.have.status(201);
    const json = pm.response.json();

    pm.expect(json.rewards).to.be.an("array").that.is.not.empty;
    const reward = json.rewards[json.rewards.length - 1];
    pm.expect(reward.amount).to.eql(250.00);
    pm.expect(reward.points).to.eql(100);
});
```

This endpoint records the result of an off-platform payment. DevSolve does not
transfer money.

### 7.7 Mark the report resolved

```text
PATCH {{baseUrl}}/reports/{{reportId}}/triage
Authorization: Bearer {{companyToken}}
```

Body:

```json
{
  "triageSeverity": "HIGH",
  "state": "RESOLVED",
  "duplicateOfId": null
}
```

Post-response script:

```javascript
pm.test("Confirmed report resolved", function () {
    pm.response.to.have.status(200);
    const json = pm.response.json();
    pm.expect(json.state).to.eql("RESOLVED");
    pm.expect(json.resolvedAt).to.be.a("string");
});
```

### 7.8 Update disclosure metadata

```text
PATCH {{baseUrl}}/reports/{{reportId}}/disclosure-status
Authorization: Bearer {{companyToken}}
```

Body:

```json
{
  "disclosureStatus": "DISCLOSED"
}
```

Post-response script:

```javascript
pm.test("Disclosure status updated", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().disclosureStatus).to.eql("DISCLOSED");
});
```

This status does not make the report endpoint public. The report remains
accessible only to its Hacker, the Company team, and Admins.

## 8. Disagreement flow

### 8.1 Create a second report

Duplicate `Create Agreement Report`, rename it `Create Disagreement Report`,
and change the body:

```json
{
  "title": "Remote command execution through file processing",
  "vulnerabilityInformation": "A crafted upload causes the backend process to execute attacker-controlled commands.",
  "impact": "An attacker could take control of the application server.",
  "reportedSeverity": "CRITICAL",
  "assetId": "{{assetId}}"
}
```

Use this post-response script:

```javascript
pm.test("Disagreement test report created", function () {
    pm.response.to.have.status(201);
    const json = pm.response.json();
    pm.expect(json.reportedSeverity).to.eql("CRITICAL");
    pm.environment.set("mismatchReportId", json.id);
});
```

### 8.2 Triage below the reported severity

Only a downgrade is disputed. Triage agreeing, or rating the finding *above*
what was reported, settles the severity on the spot — see section 8.9.

```text
PATCH {{baseUrl}}/reports/{{mismatchReportId}}/triage
Authorization: Bearer {{companyToken}}
```

Body:

```json
{
  "triageSeverity": "MEDIUM",
  "state": "VALID_CONFIRMED",
  "duplicateOfId": null
}
```

Post-response script:

```javascript
pm.test("A downgrade is put to the reporter", function () {
    pm.response.to.have.status(200);
    const json = pm.response.json();

    pm.expect(json.reportedSeverity).to.eql("CRITICAL");
    pm.expect(json.triageSeverity).to.eql("MEDIUM");
    pm.expect(json.severity).to.eql(null);
    pm.expect(json.dispute).to.be.an("object");
    // Not OPEN: the reporter is asked before an administrator is.
    pm.expect(json.dispute.status).to.eql("AWAITING_REPORTER");
    pm.expect(json.dispute.respondBy).to.not.eql(null);
});
```

### 8.3 Verify retriage is blocked

Send the same triage request again.

Post-response script:

```javascript
pm.test("Company cannot bypass a severity awaiting the reporter", function () {
    pm.response.to.have.status(409);
    const json = pm.response.json();
    pm.expect(json.message).to.include("reporter");
});
```

### 8.4 Verify rewards are blocked

```text
POST {{baseUrl}}/reports/{{mismatchReportId}}/rewards
Authorization: Bearer {{companyToken}}
```

Body:

```json
{
  "amount": 500.00,
  "points": 200,
  "note": "This must be rejected while the dispute is open."
}
```

Expected status: `409 Conflict`.

Nothing about this report can move until the severity is agreed. The reporter
answers first; only if they refuse does an Admin get involved.

### 8.5 The reporter refuses the triage severity

An Admin never sees this dispute until the reporter says they will not accept
it. Accepting instead — `POST /reports/{{mismatchReportId}}/severity/accept`,
no body — settles the report at `MEDIUM` and ends section 8 here, which is the
path most disagreements take.

```text
POST {{baseUrl}}/reports/{{mismatchReportId}}/severity/reject
Authorization: Bearer {{hackerToken}}
```

Body:

```json
{
  "reason": "A crafted upload reaches command execution unauthenticated, which is not a MEDIUM."
}
```

Post-response script:

```javascript
pm.test("Refusing escalates the dispute to an administrator", function () {
    pm.response.to.have.status(200);
    const json = pm.response.json();

    pm.expect(json.severity).to.eql(null);
    pm.expect(json.dispute.status).to.eql("OPEN");
    pm.expect(json.dispute.respondBy).to.eql(null);
});
```

Leaving it unanswered instead works too: silence past `respondBy` settles the
report at the triage severity, so an unresponsive researcher cannot freeze a
program's report for good.

### 8.6 Read the Admin dispute queue

```text
GET {{baseUrl}}/admin/disputes
Authorization: Bearer {{adminToken}}
```

With no query string this returns every dispute still awaiting a decision,
oldest first. Add `?pendingOnly=false` to browse settled ones, and
`?status=RESOLVED`, `?programId=`, or `?reportId=` to narrow further.

Post-response script:

```javascript
pm.test("Dispute is waiting in the Admin queue", function () {
    pm.response.to.have.status(200);
    const json = pm.response.json();

    const dispute = json.content.find(
        (candidate) => candidate.reportId === pm.environment.get("mismatchReportId")
    );
    pm.expect(dispute, "dispute for the mismatched report").to.be.an("object");
    pm.expect(dispute.status).to.eql("OPEN");
    pm.expect(dispute.reportedSeverity).to.eql("CRITICAL");
    pm.expect(dispute.triageSeverity).to.eql("MEDIUM");

    pm.environment.set("disputeId", dispute.id);
});
```

### 8.7 Claim the dispute for review

Optional, but it is what tells both sides somebody has picked the case up.

```text
PATCH {{baseUrl}}/admin/disputes/{{disputeId}}
Authorization: Bearer {{adminToken}}
```

Body:

```json
{
  "status": "UNDER_REVIEW",
  "resolutionNotes": "Reproducing the upload chain before deciding."
}
```

The report stays frozen. Sending `finalSeverity` here is rejected with `400`.

### 8.8 Rule on the severity

The Admin is not limited to the two severities on the table — the point of
mediation is that a third number can be the correct one.

```text
PATCH {{baseUrl}}/admin/disputes/{{disputeId}}
Authorization: Bearer {{adminToken}}
```

Body:

```json
{
  "status": "RESOLVED",
  "finalSeverity": "HIGH",
  "resolutionNotes": "Command execution is real, but it needs an authenticated session, so this is not CRITICAL."
}
```

Post-response script:

```javascript
pm.test("Admin ruling settles the report", function () {
    pm.response.to.have.status(200);
    const json = pm.response.json();

    pm.expect(json.status).to.eql("RESOLVED");
    pm.expect(json.resolvedSeverity).to.eql("HIGH");
    pm.expect(json.resolvedBy).to.not.eql(null);
    pm.expect(json.resolvedAt).to.not.eql(null);
});
```

`resolutionNotes` are required for a ruling: the decision overrides both the
Hacker and the Company, and neither can see why unless it is written down.
Omitting them returns `400`. To let the Company's assessment stand instead,
send `{"status": "DISMISSED", "resolutionNotes": "..."}` with no
`finalSeverity` — the report then settles on the triage severity.

### 8.9 Confirm the report has unfrozen

```text
GET {{baseUrl}}/reports/{{mismatchReportId}}
Authorization: Bearer {{companyToken}}
```

Post-response script:

```javascript
pm.test("Ruled severity is now the report's severity", function () {
    pm.response.to.have.status(200);
    const json = pm.response.json();

    // Neither side's claim — the Admin's.
    pm.expect(json.reportedSeverity).to.eql("CRITICAL");
    pm.expect(json.triageSeverity).to.eql("MEDIUM");
    pm.expect(json.severity).to.eql("HIGH");
    pm.expect(json.dispute.status).to.eql("RESOLVED");
});
```

The reward request from 8.4 now succeeds, and the Company can move the report
to `RESOLVED`. Re-triaging at yet another severity no longer reopens the
argument: the ruling stands, and a report gets one severity dispute in its
life.

A dispute that has already been settled cannot be decided twice — repeating
8.8 returns `409`.

### 8.10 Triage above the reported severity settles on its own

The other direction is not a disagreement worth anybody's time. File a third
report at `LOW`, then triage it as `HIGH`:

```text
PATCH {{baseUrl}}/reports/{{upgradeReportId}}/triage
Authorization: Bearer {{companyToken}}
```

```json
{
  "triageSeverity": "HIGH",
  "state": "VALID_CONFIRMED",
  "duplicateOfId": null
}
```

Post-response script:

```javascript
pm.test("An upgrade needs no dispute", function () {
    pm.response.to.have.status(200);
    const json = pm.response.json();

    pm.expect(json.reportedSeverity).to.eql("LOW");
    pm.expect(json.triageSeverity).to.eql("HIGH");
    // Triage's number, and agreed immediately: nobody is shortchanged by a
    // rating above their own claim, so there is nothing to put to the reporter.
    pm.expect(json.severity).to.eql("HIGH");
    pm.expect(json.dispute).to.eql(null);
});
```

The report is resolvable and rewardable straight away, and resolving it pays
the reporter the standing for a `HIGH` rather than the `LOW` they claimed.

## 9. Privacy and role tests

### 9.1 Unrelated Hacker cannot read a report

```text
GET {{baseUrl}}/reports/{{reportId}}
Authorization: Bearer {{otherHackerToken}}
```

Post-response script:

```javascript
pm.test("Private report is hidden from unrelated Hacker", function () {
    pm.response.to.have.status(404);
});
```

A `404` is intentional. It prevents unrelated users from discovering that a
private report exists.

### 9.2 Viewer can read organization reports

```text
GET {{baseUrl}}/reports/{{reportId}}
Authorization: Bearer {{viewerToken}}
```

Expected status: `200 OK`.

The Viewer must belong to the organization that owns the report's program.

### 9.3 Viewer cannot triage

```text
PATCH {{baseUrl}}/reports/{{reportId}}/triage
Authorization: Bearer {{viewerToken}}
```

Body:

```json
{
  "triageSeverity": "HIGH",
  "state": "VALID_CONFIRMED",
  "duplicateOfId": null
}
```

Expected status: `403 Forbidden`.

### 9.4 Admin can read all reports

```text
GET {{baseUrl}}/reports?page=0&size=20
Authorization: Bearer {{adminToken}}
```

Expected status: `200 OK`.

### 9.5 Admin cannot perform Company triage

```text
PATCH {{baseUrl}}/reports/{{mismatchReportId}}/triage
Authorization: Bearer {{adminToken}}
```

Expected status: `403 Forbidden`.

Admin resolves severity through the separate dispute workflow rather than
using the Company's triage endpoint.

## 10. Validation and negative tests

### Missing access token

```text
GET {{baseUrl}}/reports/{{reportId}}
```

Expected status: `401 Unauthorized`.

### Company attempts to submit a Hacker report

```text
POST {{baseUrl}}/programs/{{programId}}/reports
Authorization: Bearer {{companyToken}}
```

Expected status: `403 Forbidden`.

### Hacker attempts to triage

```text
PATCH {{baseUrl}}/reports/{{reportId}}/triage
Authorization: Bearer {{hackerToken}}
```

Expected status: `403 Forbidden`.

### Invalid reported severity

Create a report with:

```json
{
  "title": "Invalid severity",
  "vulnerabilityInformation": "Testing invalid report severity.",
  "reportedSeverity": "NONE",
  "assetId": "{{assetId}}"
}
```

Expected status: `400 Bad Request`.

### Invalid enum text

Use:

```json
{
  "triageSeverity": "VERY_HIGH",
  "state": "VALID_CONFIRMED"
}
```

Expected status: `400 Bad Request`.

### Reward without amount or points

```json
{
  "note": "No actual reward value."
}
```

Expected status: `400 Bad Request`.

### Disclose an unresolved report

```json
{
  "disclosureStatus": "DISCLOSED"
}
```

Expected status: `409 Conflict`.

### Duplicate without an original report

```json
{
  "triageSeverity": "NONE",
  "state": "DUPLICATE",
  "duplicateOfId": null
}
```

Expected status: `400 Bad Request`.

## 11. Useful database checks

Inspect the severity values:

```sql
SELECT
    id,
    program_id,
    reporter_id,
    reported_severity,
    triage_severity,
    severity,
    state,
    disclosure_status
FROM reports
WHERE id IN ('AGREEMENT_REPORT_UUID', 'DISAGREEMENT_REPORT_UUID');
```

Inspect automatically created disputes:

```sql
SELECT
    id,
    report_id,
    raised_by,
    reason,
    status,
    resolved_severity,
    resolved_by,
    created_at,
    resolved_at
FROM disputes
WHERE report_id = 'DISAGREEMENT_REPORT_UUID';
```

Inspect recorded rewards:

```sql
SELECT
    id,
    report_id,
    amount,
    points,
    awarded_by,
    awarded_at,
    note
FROM report_rewards
WHERE report_id = 'AGREEMENT_REPORT_UUID';
```

## 12. Expected status-code summary

| Status | Meaning in report tests |
|---|---|
| `200 OK` | Read or update succeeded |
| `201 Created` | Report or reward record created |
| `400 Bad Request` | Invalid body, enum, transition, or reward |
| `401 Unauthorized` | Missing or invalid Keycloak token |
| `403 Forbidden` | Valid user lacks the required role or organization permission |
| `404 Not Found` | Resource is absent or a private report is intentionally hidden |
| `409 Conflict` | Current workflow state prevents the requested action |

## 13. Final acceptance checklist

- [ ] Automated report tests pass.
- [ ] Complete project tests pass.
- [ ] Hacker can submit a report to an active approved program.
- [ ] Hacker can read only their own reports.
- [ ] Unrelated Hacker receives `404`.
- [ ] Company team can read its program reports.
- [ ] Viewer can read but cannot triage.
- [ ] Matching severities populate the final severity.
- [ ] Mismatching severities leave the final severity empty.
- [ ] Severity mismatch creates exactly one open dispute.
- [ ] Open dispute blocks company retriage.
- [ ] Open dispute blocks reward recording.
- [ ] Valid report accepts an off-platform reward record.
- [ ] Only resolved reports can be marked disclosed.
- [ ] Admin can read all private reports.
