\if :{?payroll_app_password}
\else
  \echo 'payroll_app_password psql variable is required'
  \quit 3
\endif
\if :{?payroll_migrator_password}
\else
  \echo 'payroll_migrator_password psql variable is required'
  \quit 3
\endif

SELECT 'CREATE ROLE payroll_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS'
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payroll_owner')
\gexec
SELECT format('CREATE ROLE payroll_migrator LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS', :'payroll_migrator_password')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payroll_migrator')
\gexec
SELECT format('CREATE ROLE payroll_app LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS', :'payroll_app_password')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payroll_app')
\gexec

SELECT format('ALTER ROLE payroll_migrator PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT NOREPLICATION NOBYPASSRLS', :'payroll_migrator_password')
\gexec
SELECT format('ALTER ROLE payroll_app PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS', :'payroll_app_password')
\gexec

GRANT payroll_owner TO payroll_migrator;
ALTER ROLE payroll_migrator SET ROLE payroll_owner;
GRANT USAGE, CREATE ON SCHEMA public TO payroll_owner;
SELECT format('GRANT CREATE ON DATABASE %I TO payroll_owner', current_database())
\gexec
SELECT format('REVOKE CREATE ON DATABASE %I FROM payroll_app', current_database())
\gexec
REVOKE CREATE ON SCHEMA public FROM payroll_app;
