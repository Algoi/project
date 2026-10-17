CREATE TABLE sys_user_permission (
 user_id BIGINT NOT NULL, permission_code VARCHAR(80) NOT NULL,
 PRIMARY KEY(user_id,permission_code), FOREIGN KEY(user_id) REFERENCES sys_user(id)
);
ALTER TABLE dish ADD COLUMN allergen_data_complete BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_user ADD COLUMN preferences_json JSON;
ALTER TABLE service_site ADD COLUMN lunch_time TIME NOT NULL DEFAULT '11:30:00',
 ADD COLUMN dinner_time TIME NOT NULL DEFAULT '17:30:00', ADD COLUMN enabled_meals_json JSON;
