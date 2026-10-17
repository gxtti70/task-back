-- Evidencia de la autorización previa, expresa e informada del titular
-- (Ley 1581 de 2012 art. 9; Decreto 1074 de 2015 art. 2.2.2.25.2.1).
CREATE TABLE user_consents (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document VARCHAR(20) NOT NULL,
    version VARCHAR(20) NOT NULL,
    accepted_at TIMESTAMP NOT NULL,
    ip VARCHAR(64),
    user_agent VARCHAR(512)
);
CREATE INDEX user_consents_user_idx ON user_consents(user_id);
CREATE INDEX user_consents_doc_idx ON user_consents(user_id, document);
