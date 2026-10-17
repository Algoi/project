ALTER TABLE elder ADD COLUMN created_by BIGINT, ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE elder_health ADD COLUMN allergen_data_complete BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN health_data_complete BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_elder_site ON elder(site_id,status);
CREATE INDEX idx_binding_reviewer ON family_binding(status,elder_id);
