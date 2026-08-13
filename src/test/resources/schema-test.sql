-- Postgres enum types the entities name through columnDefinition.
--
-- H2 has no CREATE TYPE ... AS ENUM, so Hibernate's create-drop silently
-- failed on every table using one and left it missing. That is why a
-- repository test touching user_profiles used to die with "table not found"
-- while the query itself was perfectly good — a confusing failure that looks
-- like a bug in the code under test.
--
-- Domains are the H2 equivalent that keeps the type name resolvable. They do
-- not enforce the value set, which is fine: what these tests check is that
-- queries translate and run, and the enum values are Hibernate's business.
--
-- Runs before Hibernate's DDL because defer-datasource-initialization is
-- false, which is the order this needs.
CREATE DOMAIN IF NOT EXISTS membership_status_enum AS VARCHAR(50)^^^

CREATE DOMAIN IF NOT EXISTS category_scope_enum AS VARCHAR(50)^^^
