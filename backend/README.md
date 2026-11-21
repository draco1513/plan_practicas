# ESFX TaskFlow — Backend

Java 21 · Spring Boot 3.3 · Maven · PostgreSQL 16 (Flyway) · Redis · JWT.

## Abrir en IntelliJ IDEA Community
1. *File > Open* y elige la carpeta `backend/` (el `pom.xml`). Espera a que descargue las dependencias.
2. *Project Structure > SDK*: JDK 21.
3. **No necesitas definir variables**: el backend lee `docker/.env` solo (mientras IntelliJ ejecute desde la carpeta `backend/`). Si quieres sobreescribir algo, en *Modify Run Configuration > Environment variables*:

```
DB_PASSWORD=<la de docker/.env>;REDIS_PASSWORD=<la de docker/.env>;JWT_SECRET=<mínimo 32 caracteres>;APP_ADMIN_EMAIL=admin@esfx.local;APP_ADMIN_PASSWORD=<mínimo 8 caracteres>
```

Los valores por defecto (puertos 5433 y 6380, base `taskflow`, usuario `taskflow`) ya coinciden con `docker/docker-compose.yml`.

## Variables de entorno

| Variable | Por defecto | Uso |
|---|---|---|
| SERVER_PORT | 8085 | puerto del backend |
| FLYWAY_DEV_LOCATION | vacío | `,classpath:db/dev` carga los datos de prueba |
| DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD | localhost / 5433 / taskflow / taskflow / — | PostgreSQL |
| REDIS_HOST / REDIS_PORT / REDIS_PASSWORD | localhost / 6380 / — | cola de correos |
| JWT_SECRET | — | firma de tokens (≥ 32 caracteres) |
| JWT_EXPIRATION_SECONDS | 3600 | duración del token |
| APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD | admin@esfx.local / — | primer administrador (solo si no hay usuarios) |
| MAIL_HOST / MAIL_PORT / MAIL_USERNAME / MAIL_PASSWORD | vacío | SMTP. Vacío = los correos solo se escriben en el log |
| FRONTEND_URL | http://localhost:5173 | enlace del correo de recuperación |
| STORAGE_PATH | ./storage | carpeta de adjuntos |

**Gmail**: `MAIL_HOST=smtp.gmail.com`, `MAIL_USERNAME=tucuenta@gmail.com`, `MAIL_PASSWORD=` contraseña de aplicación (requiere verificación en dos pasos).

## Comandos
```
mvn test                 # pruebas unitarias
mvn spring-boot:run      # ejecutar
mvn package              # genera target/taskflow-0.1.0.jar
```

## Estructura (un paquete por módulo)
`common` (errores, seguridad compartida, contratos entre módulos) · `access` · `projects` · `calendar` · `notifications` · `reports` · `timetracking`.
La API sigue `docs/api/openapi.yaml`; Swagger UI en `/swagger-ui.html`.

## Notas
- Un correo de recuperación en modo simulado aparece en la consola (busca `[CORREO SIMULADO]`).
- Recordatorios: un job cada minuto crea avisos de reuniones y tareas por vencer o vencidas, según las preferencias de cada usuario.
