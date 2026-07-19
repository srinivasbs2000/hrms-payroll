CREATE FUNCTION platform.current_tenant_id() RETURNS uuid LANGUAGE sql STABLE AS $$ SELECT nullif(current_setting('app.tenant_id',true),'')::uuid $$;
DO $$
DECLARE r record;
BEGIN
 FOR r IN SELECT schemaname, tablename FROM pg_tables
          WHERE schemaname IN ('security','audit','organisation','compensation','employee_payroll','payroll_ops','payroll_calc','documents','integration')
 LOOP
  EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY',r.schemaname,r.tablename);
  EXECUTE format('ALTER TABLE %I.%I FORCE ROW LEVEL SECURITY',r.schemaname,r.tablename);
  EXECUTE format('CREATE POLICY tenant_isolation ON %I.%I USING (tenant_id=platform.current_tenant_id()) WITH CHECK (tenant_id=platform.current_tenant_id())',r.schemaname,r.tablename);
 END LOOP;
END $$;
