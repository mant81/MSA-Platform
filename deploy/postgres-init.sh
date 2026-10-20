#!/usr/bin/env bash
set -euo pipefail
# Fresh volume only. Passwords are read by psql through secret files; never echo them.
for service in auth member trace config hr; do
  service_password="$(cat "/run/secrets/db/$service/DB_PASSWORD")"
  psql --username "$POSTGRES_USER" --dbname postgres --set=ON_ERROR_STOP=1 --set=service="$service" --set=password="$service_password" <<'SQL'
CREATE USER :"service" PASSWORD :'password';
SELECT format('CREATE DATABASE %I OWNER %I', :'service' || 'db', :'service') \gexec
SQL
  unset service_password
done
