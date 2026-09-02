# Meilisearch integration

`GET /api/v1/search` searches everything public on the platform — programs,
showcases, problems, organizations and researchers — out of Meilisearch. Five
indexes are kept level with PostgreSQL by a background pass; nothing else in the
API depends on them.

## Configuration

```properties
MEILISEARCH_ENABLED=true
MEILISEARCH_URL=http://localhost:7700
MEILISEARCH_API_KEY=<master key, or a key that can write documents and settings>
MEILISEARCH_INDEX_PREFIX=
MEILISEARCH_SYNC_INTERVAL=30s
```

Off by default. Every document is derived from a row in PostgreSQL and
rebuildable at any time, so an instance with no Meilisearch behind it is one
where the search endpoint answers `503` — not a broken one.

`docker compose up meilisearch` brings one up on `127.0.0.1:7700` with the
master key from `devsole-docker/.env`. Its data is on a volume, unlike Redis:
everything in it can be rebuilt, but rebuilding means reading five tables end to
end and a container restart should not cost that.

Set `MEILISEARCH_INDEX_PREFIX` when one Meilisearch serves more than one
environment. Without it, staging and production reindex over each other.

## What is in the indexes

| Index | Holds | Extra searchable text |
| --- | --- | --- |
| `programs` | active, approved, public programs of active organizations | organization name, in-scope asset identifiers |
| `showcases` | approved showcases | author name, tags, category |
| `problems` | published, resolved and closed problems | author name, tags, error message |
| `organizations` | approved organizations | country |
| `users` | active profiles | username, biography, country |

Nothing reaches an index that is not already served by a public endpoint. Two
places where that needed care: a profile's email is on the public profile
response but only for a viewer who shares an organization with that person, and
an index has no viewer — so it is not indexed. An organization's
`joiningReason` and `rejectionReason` are review correspondence and are not
indexed either.

Every document carries the same envelope — `id`, `type`, `title`, `subtitle`,
`body`, `imageUrl`, `slug` — plus whatever else its index defines. That is what
lets one endpoint search five different things and hand back one list.

There is no `url`. The web app owns its routes, this service has never been told
them, and a guessed one would render as a link and lead nowhere. Build the route
from `type` and `slug`: a program handle, an organization slug, a username, or
the id for showcases and problems, which have no name of their own.

## How the indexes stay current

There is no hook on the write path. `SearchIndexSynchronizer` polls
`updated_at` every `MEILISEARCH_SYNC_INTERVAL`, turns each changed row into a
document, and writes it — or deletes it, when the row is no longer something the
public may see.

That is a deliberate trade. A program becomes findable or unfindable through
create, update, publish, pause, submit, approve, reject, soft-delete, and
indirectly through its organization being suspended. An indexer wired into all
of those would be one missed call away from a permanently wrong index, with
nothing to signal it. Polling has one code path, covers every case including the
ones nobody enumerated, and repairs itself — whatever went wrong, the next pass
fixes it.

What it costs is freshness: **a change is searchable within one sync interval,
not immediately.** Endpoints that must be right now — fetching a program by
handle, the paged listings — still read PostgreSQL and are unaffected.

Three details worth knowing:

- **The watermark is in memory**, seeded at startup from the newest document in
  each index. A restart resumes rather than rebuilds, and an index wiped
  underneath a running API refills on its own.
- **Each pass re-reads the last 30 seconds** (`MEILISEARCH_SYNC_OVERLAP`). A
  transaction can stamp a row and commit after a pass has already read past that
  timestamp; without the overlap that row would never be indexed. Re-reading is
  free because writes are keyed by document id.
- **A hard delete is the one thing polling cannot see**, since the row is gone
  and has no timestamp left to notice. The single path that does one — an author
  deleting their own showcase — publishes an event, and
  `SearchDocumentRemovalListener` takes the document out. Everything else here
  is a soft delete and arrives through the ordinary pass.

## Operating it

```
GET  /api/v1/admin/search           whether it is on, whether it answers, document counts
POST /api/v1/admin/search/reindex   rebuild every index from PostgreSQL
```

Both need `ROLE_ADMIN`. The rebuild answers `202` as soon as the work is on a
background thread — it reads every row of five tables and would outlive the
request — so watch the document counts to see it finish. It answers `409` while
a pass is already running.

A rebuild does not empty the indexes first, because documents are keyed by row
id and clearing would only add a window where search returns nothing. If an
index has picked up a document whose row no longer exists at all, delete the
index in Meilisearch and then rebuild.

## Querying

```
GET /api/v1/search?q=payments
GET /api/v1/search?q=payments&type=programs&page=0&size=20
GET /api/v1/search/types
```

Anonymous, on the same terms as the listings it searches.

With no `type`, every index is searched in one round trip and `groups` holds a
short list from each — the shape a search box wants while somebody is still
typing. Indexes that matched nothing are left out. With a `type`, `groups` holds
one entry and the paging fields describe it. A blank `q` matches everything,
which is how to browse an index rather than search it.

`page` is zero-based here, as it is everywhere else in this API, and is
translated on the way to Meilisearch, which counts from one.

Each hit carries a `snippet`: the matching stretch of the body, cropped around
the match, with `<mark>` around each matched word. It is the only field with
markup in it — `title` and `subtitle` are plain, so a caller that does not
render HTML can use them as they are. The full indexed document comes back
alongside, so rendering a program's bounty range or a showcase's tags does not
need a second request.

`503` means search is switched off or Meilisearch is unreachable. There is no
fallback to a `LIKE` over PostgreSQL on purpose: results ranked differently and
matched differently would still arrive looking like search results.

## Checking it works

```bash
curl "$MEILISEARCH_URL/health"                      # {"status":"available"}
curl "$API/api/v1/search?q=test" | jq '.groups[].type'
```

If `/api/v1/admin/search` reports `enabled: true, reachable: false`, the API
cannot get to Meilisearch — check `MEILISEARCH_URL` (`localhost` from the host,
`devsolve-meilisearch` from inside the compose network). If it reports both true
with every count at zero, no pass has completed yet; give it a sync interval, or
look for a `Search index ... could not be synced` warning in the log.
