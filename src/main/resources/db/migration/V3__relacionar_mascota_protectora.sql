ALTER TABLE mascota ADD COLUMN protectora_id BIGINT;

ALTER TABLE mascota ADD CONSTRAINT fk_mascota_protectora
    FOREIGN KEY (protectora_id) REFERENCES protectora (id);

CREATE INDEX idx_mascota_protectora_id ON mascota (protectora_id);
