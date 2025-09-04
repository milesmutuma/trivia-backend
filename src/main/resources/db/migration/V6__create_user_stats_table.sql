-- Create user_stats table for tracking user statistics per category
CREATE TABLE user_stats (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    total_games_played INTEGER DEFAULT 0 NOT NULL,
    games_completed INTEGER DEFAULT 0 NOT NULL,
    total_questions_answered INTEGER DEFAULT 0 NOT NULL,
    total_correct_answers INTEGER DEFAULT 0 NOT NULL,
    total_score BIGINT DEFAULT 0 NOT NULL,
    highest_score INTEGER DEFAULT 0 NOT NULL,
    current_streak INTEGER DEFAULT 0 NOT NULL,
    longest_streak INTEGER DEFAULT 0 NOT NULL,
    average_score DOUBLE PRECISION DEFAULT 0.0 NOT NULL,
    accuracy_percentage DOUBLE PRECISION DEFAULT 0.0 NOT NULL,
    fastest_completion_seconds INTEGER,
    last_played_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_stats_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_stats_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_stats_user_category UNIQUE (user_id, category_id)
);

-- Create indexes for better performance
CREATE INDEX idx_user_stats_user_id ON user_stats(user_id);
CREATE INDEX idx_user_stats_category_id ON user_stats(category_id);
CREATE INDEX idx_user_stats_total_score ON user_stats(total_score DESC);
CREATE INDEX idx_user_stats_longest_streak ON user_stats(longest_streak DESC);
CREATE INDEX idx_user_stats_last_played_at ON user_stats(last_played_at);
CREATE INDEX idx_user_stats_user_category ON user_stats(user_id, category_id);