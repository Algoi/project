ALTER TABLE recommendation_event ADD COLUMN algorithm VARCHAR(80) NOT NULL DEFAULT 'weighted_score_v1';
