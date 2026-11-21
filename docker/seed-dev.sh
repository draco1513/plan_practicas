#!/usr/bin/env bash
# Carga los datos de prueba a mano en el contenedor taskflow-db (solo desarrollo).
# Uso:  cd docker && ./seed-dev.sh
set -euo pipefail
cd "$(dirname "$0")"
set -a; [ -f .env ] && . ./.env; set +a
docker exec -i taskflow-db psql -v ON_ERROR_STOP=1 -U "${DB_USER:-taskflow}" -d "${DB_NAME:-taskflow}" \
  < ../backend/src/main/resources/db/dev/V100__dev_seed.sql
echo "Datos de prueba cargados. Usuarios: admin@, gerente@, jefe@, ana@, luis@ (@esfx.local) - clave: Esfx2026!"
