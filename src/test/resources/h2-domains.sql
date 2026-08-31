-- The PostgreSQL enum types the entities name through columnDefinition.
--
-- H2 has no CREATE TYPE ... AS ENUM, and Hibernate's create-drop does not fail
-- loudly when a column names a type it cannot resolve: it skips the whole table
-- and carries on. A repository test then dies with "Table X not found", which
-- reads like a bug in the query under test rather than a missing type. Every
-- columnDefinition ending in _enum needs a line here.
--
-- Run through the datasource URL's INIT rather than spring.sql.init because
-- this profile is shared with the Testcontainers tests, which point at real
-- PostgreSQL and run the production schema.sql. A URL only applies to the
-- datasource that uses it; spring.sql.init would be executed against
-- PostgreSQL too, where these are already real enum types.
--
-- IF NOT EXISTS throughout: INIT runs on every pooled connection, not once.

CREATE DOMAIN IF NOT EXISTS membership_status_enum AS VARCHAR(50);
CREATE DOMAIN IF NOT EXISTS category_scope_enum AS VARCHAR(50);
CREATE DOMAIN IF NOT EXISTS researcher_access_status_enum AS VARCHAR(50);
CREATE DOMAIN IF NOT EXISTS severity_enum AS VARCHAR(50);
CREATE DOMAIN IF NOT EXISTS engagement_type_enum AS VARCHAR(50);
CREATE DOMAIN IF NOT EXISTS program_state_enum AS VARCHAR(50);
CREATE DOMAIN IF NOT EXISTS submission_state_enum AS VARCHAR(50);
CREATE DOMAIN IF NOT EXISTS visibility_enum AS VARCHAR(50);
CREATE DOMAIN IF NOT EXISTS asset_type_enum AS VARCHAR(50);
CREATE DOMAIN IF NOT EXISTS report_environment_enum AS VARCHAR(50);
CREATE DOMAIN IF NOT EXISTS report_state_enum AS VARCHAR(50);
CREATE DOMAIN IF NOT EXISTS disclosure_status_enum AS VARCHAR(50);
CREATE DOMAIN IF NOT EXISTS dispute_status_enum AS VARCHAR(50);
CREATE DOMAIN IF NOT EXISTS industry_enum AS VARCHAR(50);
