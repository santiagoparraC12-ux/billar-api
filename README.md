# Billar API (Spring Boot 3 + Java 17)

Backend del sistema de control para billar. Roles: DUEÑO (OWNER) y EMPLEADO (EMPLOYEE).

## Ejecutar en IntelliJ
1. File > Open > carpeta `billar-api` (IntelliJ importa Maven solo; elige JDK 17 o superior).
2. Activa **Lombok**: Settings > Build > Compiler > Annotation Processors > *Enable annotation processing*.
3. Ejecuta `BillarApplication`. Arranca en http://localhost:8080 con base H2 local (carpeta `data/`).
4. Primer usuario: `admin` / `Admin12345`. **Cámbiala** (variables BILLAR_OWNER_USER y BILLAR_OWNER_PASSWORD antes del primer arranque, o POST /api/users y desactiva el admin).

## Pruebas
`mvn test` corre `BillingServiceTest` (regla de cobro: 38 min a $6.000/h = $3.800).

## Producción
- Base PostgreSQL: `docker compose up -d`
- Variables obligatorias: DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET (32+ caracteres), CORS_ORIGINS, BILLAR_OWNER_USER, BILLAR_OWNER_PASSWORD
- `java -jar billar-api.jar --spring.profiles.active=prod`
- Poner detrás de HTTPS (Nginx/Caddy). Nunca expongas el puerto 8080 sin HTTPS.

## Endpoints (todos bajo /api, con encabezado `Authorization: Bearer <token>`)
| Método y ruta | Quién |
|---|---|
| POST /auth/login | público |
| GET /tables | todos |
| POST/PUT/DELETE /tables | dueño |
| GET /products | todos (el costo solo lo ve el dueño) |
| POST/PUT/DELETE /products, POST /products/{id}/purchase | dueño |
| POST /inventory/count | todos |
| GET /sessions, POST /sessions, POST /sessions/{id}/items, DELETE /sessions/{id}/items/{productId}, POST /sessions/{id}/charge | todos |
| POST /sales/quick | todos |
| GET /sales, POST /sales/{id}/void, POST /sales/{id}/pay | dueño |
| GET /cash/shift, POST /cash/close, POST /expenses | todos (el empleado no ve el esperado ni la diferencia) |
| GET /reports/summary, GET /audit | dueño |
| GET /settings | todos; PUT /settings dueño |
| GET/POST /users, PATCH /users/{id}/active, PUT /users/{id}/password | dueño |

Fechas en reportes: `?from=2026-10-20&to=2026-10-20`.
