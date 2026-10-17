# Baseline manual de una base pre-Flyway

La aplicación mantiene `spring.flyway.baseline-on-migrate=false`. Así, una base no vacía sin historial Flyway detiene el arranque en vez de marcarla automáticamente como versión 1.

## Base nueva

No ejecutes `baseline`. Una base vacía debe arrancar la aplicación y recibir V1, V2 y V3 en orden.

## Base existente que usaba el esquema anterior

Haz esto una sola vez durante una ventana de mantenimiento, primero sobre una restauración de respaldo:

1. Detén escrituras y crea un respaldo PostgreSQL. Restaura ese respaldo en una base aislada y confirma que la restauración funciona.
2. Compara la estructura de esa copia con el esquema representado por `V1__baseline_current_schema.sql`. Confirma que las tablas y columnas requeridas existen y que los datos e índices importantes están presentes. Si hay diferencias, no continúes con baseline: prepara una migración correctiva revisada.
3. Usa Flyway CLI compatible con la versión del proyecto y apunta exclusivamente a la copia restaurada. Configura `FLYWAY_URL`, `FLYWAY_USER`, `FLYWAY_PASSWORD`, `FLYWAY_BASELINE_VERSION=1` y `FLYWAY_LOCATIONS=filesystem:src/main/resources/db/migration` en el entorno del proceso.
4. Ejecuta `flyway info` y revisa que el destino y el estado sean los esperados. Luego ejecuta `flyway baseline` y vuelve a ejecutar `flyway info`; debe mostrar baseline 1.
5. Arranca la aplicación contra la copia. Flyway debe aplicar V2 y V3; Hibernate debe pasar `ddl-auto=validate`. Ejecuta una comprobación funcional y revisa los logs.
6. Repite el mismo procedimiento en producción con el respaldo verificado, el cambio detenido y el destino comprobado. Arranca una sola instancia de la aplicación y confirma salud antes de reabrir escrituras.

Nunca ejecutes `baseline` contra una base vacía: marcaría V1 como aplicada y dejaría sin crear las tablas iniciales. Nunca uses `baseline-on-migrate=true` como atajo para resolver incompatibilidades. El baseline no transforma ni valida el esquema existente; únicamente registra la versión inicial.

## Después del baseline

Las bases existentes reciben V2 (familias de refresh tokens) y V3 (consentimientos). Las bases nuevas reciben V1, V2 y V3. No edites migraciones que ya hayan sido aplicadas; crea una migración versionada nueva.
