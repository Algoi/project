ALTER TABLE subsidy_entitlement ADD valid_from DATE NULL, ADD valid_to DATE NULL;
CREATE TABLE binding_history (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,binding_id BIGINT NOT NULL,action VARCHAR(30) NOT NULL,status VARCHAR(20) NOT NULL,note VARCHAR(500),operator_user_id BIGINT,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(binding_id) REFERENCES family_binding(id));
INSERT INTO binding_history(binding_id,action,status,note,created_at) SELECT id,'IMPORTED',status,'迁移前的绑定状态',COALESCE(reviewed_at,created_at) FROM family_binding;
CREATE TABLE subsidy_history (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,elder_id BIGINT NOT NULL,application_id BIGINT NULL,action VARCHAR(30) NOT NULL,note VARCHAR(500) NOT NULL,valid_from DATE NULL,valid_to DATE NULL,operator_user_id BIGINT,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(elder_id) REFERENCES elder(id));
INSERT INTO subsidy_history(elder_id,application_id,action,note,created_at) SELECT elder_id,source_application_id,'IMPORTED','迁移前的补贴资格',COALESCE(activated_at,UTC_TIMESTAMP()) FROM subsidy_entitlement;
CREATE TABLE menu_template (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,site_id BIGINT NOT NULL,name VARCHAR(100) NOT NULL,entries_json JSON NOT NULL,created_by BIGINT NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(site_id) REFERENCES service_site(id));
