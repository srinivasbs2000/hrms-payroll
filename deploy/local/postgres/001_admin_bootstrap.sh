#!/usr/bin/env bash
set -Eeuo pipefail

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=payroll_app_password="$PAYROLL_APP_PASSWORD" \
  --set=payroll_migrator_password="$PAYROLL_MIGRATOR_PASSWORD" \
  --file=/bootstrap/001_admin_bootstrap.sql
