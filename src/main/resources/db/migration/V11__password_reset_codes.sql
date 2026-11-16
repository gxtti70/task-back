-- Recuperación de contraseña por código de 6 dígitos.
--
-- El flujo anterior usaba un token UUID en un enlace. Se reemplaza por un código
-- numérico corto que se entrega por correo. El código nunca se almacena en texto
-- plano: solo su HMAC-SHA256 con el pepper del servidor.
--
-- La tabla password_reset_tokens se elimina. Solo contenía enlaces de
-- restablecimiento caducos, por lo que no hay pérdida de datos de usuario.

DROP TABLE IF EXISTS password_reset_tokens;

CREATE TABLE IF NOT EXISTS password_reset_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS password_reset_codes_user_idx ON password_reset_codes(user_id);
CREATE INDEX IF NOT EXISTS password_reset_codes_hash_idx ON password_reset_codes(code_hash);
