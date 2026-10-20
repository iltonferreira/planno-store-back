CREATE TABLE user_email_2fa_challenges (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    code_hmac VARCHAR(64) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts BETWEEN 0 AND 5),
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT user_email_2fa_challenge_expiry_check CHECK (expires_at > issued_at)
);

CREATE INDEX user_email_2fa_challenges_expiry_idx ON user_email_2fa_challenges(expires_at);
