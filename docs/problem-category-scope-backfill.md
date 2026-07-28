# Problem category scope backfill

Migration `V20260728_01` adds `categories.scope` but intentionally leaves
existing rows as `NULL`. The application will allow an unscoped category on a
draft, but will not submit or publish that problem until the category has been
mapped explicitly.

Review the current rows:

```sql
SELECT id, name, slug, scope
FROM categories
ORDER BY name;
```

Map each row from confirmed product ownership:

```sql
UPDATE categories
SET scope = 'problem'
WHERE id IN (
    -- verified problem category UUIDs
);

UPDATE categories
SET scope = 'showcase'
WHERE id IN (
    -- verified showcase category UUIDs
);
```

Before enforcing `NOT NULL`, verify that no rows remain unmapped:

```sql
SELECT id, name, slug
FROM categories
WHERE scope IS NULL;
```

Only after that query returns no rows should a later migration run:

```sql
ALTER TABLE categories
ALTER COLUMN scope SET NOT NULL;
```

Do not infer scope from names or existing problem/showcase usage without
review. A category may intentionally be represented once in each scope.
