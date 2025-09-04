-- Create games table for game sessions
CREATE TABLE games (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    game_mode VARCHAR(20) NOT NULL CHECK (game_mode IN ('QUICK_PLAY', 'TIMED_CHALLENGE', 'SURVIVAL', 'CUSTOM')),
    status VARCHAR(15) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED', 'PAUSED')) DEFAULT 'IN_PROGRESS',
    questions_answered INTEGER DEFAULT 0,
    correct_answers INTEGER DEFAULT 0,
    score INTEGER DEFAULT 0,
    duration_seconds INTEGER,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT fk_games_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_games_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
);

-- Create game_questions join table for tracking individual question attempts
CREATE TABLE game_questions (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    question_order INTEGER DEFAULT 1 NOT NULL,
    user_answer VARCHAR(500),
    is_correct BOOLEAN DEFAULT FALSE NOT NULL,
    time_taken_seconds INTEGER,
    points_earned INTEGER DEFAULT 0 NOT NULL,
    answered_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_game_questions_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
    CONSTRAINT fk_game_questions_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    CONSTRAINT uq_game_questions_game_question UNIQUE (game_id, question_id),
    CONSTRAINT uq_game_questions_game_order UNIQUE (game_id, question_order)
);

-- Create indexes for better performance
CREATE INDEX idx_games_user_id ON games(user_id);
CREATE INDEX idx_games_category_id ON games(category_id);
CREATE INDEX idx_games_status ON games(status);
CREATE INDEX idx_games_started_at ON games(started_at);
CREATE INDEX idx_games_user_status ON games(user_id, status);

CREATE INDEX idx_game_questions_game_id ON game_questions(game_id);
CREATE INDEX idx_game_questions_question_id ON game_questions(question_id);
CREATE INDEX idx_game_questions_is_correct ON game_questions(is_correct);
CREATE INDEX idx_game_questions_answered_at ON game_questions(answered_at);