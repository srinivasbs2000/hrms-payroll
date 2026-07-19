\set ON_ERROR_STOP on

DO $$
DECLARE violation text;
BEGIN
  SELECT string_agg(format('%I.%I', n.nspname, c.relname), ', ' ORDER BY n.nspname, c.relname)
    INTO violation
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped
   WHERE c.relkind = 'r' AND (NOT c.relrowsecurity OR NOT c.relforcerowsecurity);
  IF violation IS NOT NULL THEN RAISE EXCEPTION 'tenant tables missing ENABLE/FORCE RLS: %', violation; END IF;

  SELECT string_agg(format('%I.%I', n.nspname, c.relname), ', ' ORDER BY n.nspname, c.relname)
    INTO violation
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped
   WHERE c.relkind = 'r' AND NOT EXISTS (
     SELECT 1 FROM pg_policy p WHERE p.polrelid = c.oid AND p.polname = 'tenant_isolation'
       AND pg_get_expr(p.polqual, p.polrelid) LIKE '%platform.current_tenant_id()%'
       AND pg_get_expr(p.polwithcheck, p.polrelid) LIKE '%platform.current_tenant_id()%');
  IF violation IS NOT NULL THEN RAISE EXCEPTION 'tenant tables missing tenant_isolation policy: %', violation; END IF;
END $$;

DO $$
DECLARE violation text;
BEGIN
  WITH tenant_fks AS (
    SELECT con.conrelid, con.confrelid, con.conname,
      ARRAY(SELECT att.attname FROM unnest(con.conkey) WITH ORDINALITY k(attnum, ord)
            JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = k.attnum ORDER BY k.ord) child_columns,
      ARRAY(SELECT att.attname FROM unnest(con.confkey) WITH ORDINALITY k(attnum, ord)
            JOIN pg_attribute att ON att.attrelid = con.confrelid AND att.attnum = k.attnum ORDER BY k.ord) parent_columns
    FROM pg_constraint con WHERE con.contype = 'f'
      AND EXISTS (SELECT 1 FROM pg_attribute a WHERE a.attrelid = con.conrelid AND a.attname = 'tenant_id' AND NOT a.attisdropped)
      AND EXISTS (SELECT 1 FROM pg_attribute a WHERE a.attrelid = con.confrelid AND a.attname = 'tenant_id' AND NOT a.attisdropped)
  )
  SELECT string_agg(format('%s.%I', conrelid::regclass, conname), ', ' ORDER BY conrelid::regclass::text, conname)
    INTO violation FROM tenant_fks
   WHERE NOT ('tenant_id' = ANY(child_columns) AND 'tenant_id' = ANY(parent_columns))
      OR array_position(child_columns, 'tenant_id') <> array_position(parent_columns, 'tenant_id');
  IF violation IS NOT NULL THEN RAISE EXCEPTION 'tenant-unsafe foreign keys: %', violation; END IF;
END $$;

DO $$
DECLARE violation text; immutable regclass; tenant_table record; mutable_expected boolean;
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payroll_app' AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolreplication OR rolbypassrls))
    THEN RAISE EXCEPTION 'payroll_app has an administrative or BYPASSRLS role attribute'; END IF;
  IF pg_has_role('payroll_app', 'payroll_owner', 'MEMBER') THEN RAISE EXCEPTION 'payroll_app can assume payroll_owner'; END IF;
  IF has_database_privilege('payroll_app', current_database(), 'CREATE') THEN RAISE EXCEPTION 'payroll_app can create database objects'; END IF;
  SELECT string_agg(nspname, ', ' ORDER BY nspname) INTO violation FROM pg_namespace
   WHERE nspname IN ('public','platform','security','audit','organisation','compensation','employee_payroll','payroll_ops','payroll_calc','documents','integration')
     AND has_schema_privilege('payroll_app', oid, 'CREATE');
  IF violation IS NOT NULL THEN RAISE EXCEPTION 'payroll_app has CREATE on schemas: %', violation; END IF;
  IF EXISTS (SELECT 1 FROM pg_class WHERE relowner = 'payroll_app'::regrole)
     OR EXISTS (SELECT 1 FROM pg_namespace WHERE nspowner = 'payroll_app'::regrole)
    THEN RAISE EXCEPTION 'payroll_app owns a schema or relation'; END IF;
  IF NOT has_function_privilege('payroll_app', 'platform.current_tenant_id()', 'EXECUTE') THEN
    RAISE EXCEPTION 'payroll_app cannot execute platform.current_tenant_id()';
  END IF;

  FOR tenant_table IN
    SELECT c.oid::regclass AS relation, n.nspname, c.relname
      FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
      JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped
     WHERE c.relkind = 'r'
  LOOP
    IF NOT has_table_privilege('payroll_app', tenant_table.relation, 'SELECT')
       OR NOT has_table_privilege('payroll_app', tenant_table.relation, 'INSERT') THEN
      RAISE EXCEPTION 'payroll_app lacks SELECT/INSERT runtime grants on %', tenant_table.relation;
    END IF;
    mutable_expected := tenant_table.nspname NOT IN ('audit', 'payroll_calc')
      AND NOT (tenant_table.nspname = 'payroll_ops' AND tenant_table.relname = 'input_snapshot')
      AND NOT (tenant_table.nspname = 'documents' AND tenant_table.relname = 'draft_payslip');
    IF has_table_privilege('payroll_app', tenant_table.relation, 'UPDATE') <> mutable_expected
       OR has_table_privilege('payroll_app', tenant_table.relation, 'DELETE') <> mutable_expected THEN
      RAISE EXCEPTION 'payroll_app UPDATE/DELETE grants do not match baseline for %', tenant_table.relation;
    END IF;
  END LOOP;

  FOREACH immutable IN ARRAY ARRAY['audit.audit_event'::regclass, 'payroll_ops.input_snapshot'::regclass,
    'payroll_calc.payroll_result'::regclass, 'payroll_calc.component_result'::regclass,
    'payroll_calc.calculation_trace'::regclass, 'documents.draft_payslip'::regclass] LOOP
    IF has_table_privilege('payroll_app', immutable, 'UPDATE') OR has_table_privilege('payroll_app', immutable, 'DELETE')
      THEN RAISE EXCEPTION 'payroll_app can mutate immutable relation %', immutable; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgrelid = immutable AND NOT tgisinternal
                   AND tgfoid = 'platform.reject_mutation()'::regprocedure)
      THEN RAISE EXCEPTION 'immutable relation % lacks reject_mutation trigger', immutable; END IF;
  END LOOP;
END $$;

SELECT n.nspname AS schema_name, c.relname AS table_name, c.relrowsecurity AS rls_enabled,
       c.relforcerowsecurity AS rls_forced, p.polname AS policy_name
  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
  JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped
  LEFT JOIN pg_policy p ON p.polrelid = c.oid AND p.polname = 'tenant_isolation'
 WHERE c.relkind = 'r' ORDER BY n.nspname, c.relname;

SELECT con.conrelid::regclass AS child_table, con.conname, pg_get_constraintdef(con.oid) AS tenant_safe_definition
  FROM pg_constraint con WHERE con.contype = 'f' AND pg_get_constraintdef(con.oid) LIKE '%tenant_id%'
 ORDER BY con.conrelid::regclass::text, con.conname;

SELECT rolname, rolsuper, rolcreaterole, rolcreatedb, rolbypassrls
  FROM pg_roles WHERE rolname IN ('payroll_owner', 'payroll_migrator', 'payroll_app') ORDER BY rolname;
