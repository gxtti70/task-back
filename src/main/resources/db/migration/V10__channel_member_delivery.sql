ALTER TABLE channel_members ADD COLUMN IF NOT EXISTS last_delivered_at TIMESTAMP;

-- Razonable por defecto en el despliegue: quien ya leyó un mensaje, lo recibió antes.
UPDATE channel_members
SET last_delivered_at = last_read_at
WHERE last_delivered_at IS NULL
  AND last_read_at IS NOT NULL;
