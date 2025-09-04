-- Add multiplayer fields to games table
ALTER TABLE games 
ADD COLUMN max_players INTEGER NOT NULL DEFAULT 1,
ADD COLUMN current_players INTEGER NOT NULL DEFAULT 1,
ADD COLUMN invite_code VARCHAR(8),
ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- Update status enum to include WAITING
ALTER TABLE games 
DROP CONSTRAINT games_status_check;

ALTER TABLE games 
ADD CONSTRAINT games_status_check CHECK (status IN ('WAITING', 'IN_PROGRESS', 'COMPLETED', 'ABANDONED', 'PAUSED'));

-- Update default status to WAITING
ALTER TABLE games ALTER COLUMN status SET DEFAULT 'WAITING';

-- Create game_players table for tracking players in games
CREATE TABLE game_players (
    id BIGSERIAL PRIMARY KEY,
    game_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    is_host BOOLEAN DEFAULT FALSE NOT NULL,
    is_ready BOOLEAN DEFAULT FALSE NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMP,
    CONSTRAINT fk_game_players_game FOREIGN KEY (game_id) REFERENCES games(id) ON DELETE CASCADE,
    CONSTRAINT fk_game_players_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_game_players_game_user UNIQUE (game_id, user_id)
);

-- Create indexes for better performance
CREATE INDEX idx_game_players_game_id ON game_players(game_id);
CREATE INDEX idx_game_players_user_id ON game_players(user_id);
CREATE INDEX idx_game_players_is_host ON game_players(is_host);
CREATE INDEX idx_games_invite_code ON games(invite_code) WHERE invite_code IS NOT NULL;
CREATE INDEX idx_games_status_multiplayer ON games(status, max_players) WHERE max_players > 1;