ALTER TABLE daily_menu_item ADD COLUMN allergen_ids_override_json JSON, ADD COLUMN ingredients_override_json JSON, ADD COLUMN allergen_data_complete BOOLEAN;
ALTER TABLE recommendation_event ADD COLUMN event_date DATE, ADD COLUMN config_version INT;
CREATE UNIQUE INDEX uk_daily_exposure ON recommendation_event(elder_id,menu_item_id,event_date);
