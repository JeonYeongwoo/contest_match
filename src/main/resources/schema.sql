-- MatchUp schema: sources -> raw_documents -> contests (+ embeddings) -> profiles/feedback
-- Requires PostgreSQL with the pgvector extension available (use image: pgvector/pgvector:pg16).

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS sources (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    base_url VARCHAR(1000) NOT NULL,
    type VARCHAR(20) NOT NULL, -- RSS | HTML | API
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    robots_allowed BOOLEAN,
    robots_checked_at TIMESTAMP,
    terms_note VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS raw_documents (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES sources(id),
    source_url VARCHAR(2000) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    raw_title VARCHAR(1000),
    raw_content TEXT,
    fetched_at TIMESTAMP NOT NULL DEFAULT now(),
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMP,
    UNIQUE (source_id, content_hash)
);

CREATE TABLE IF NOT EXISTS contests (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    organizer VARCHAR(300),
    categories VARCHAR(500), -- comma-separated tags
    description TEXT,
    eligibility TEXT,
    eligibility_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    individual_or_team VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN', -- INDIVIDUAL | TEAM | BOTH | UNKNOWN
    application_start DATE,
    deadline DATE,
    deadline_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    prize_description VARCHAR(1000),
    prize_amount_krw BIGINT,
    region VARCHAR(100),
    online_offline VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN', -- ONLINE | OFFLINE | HYBRID | UNKNOWN
    submission_items TEXT,
    official_url VARCHAR(2000),
    confidence VARCHAR(10) NOT NULL DEFAULT 'LOW', -- LOW | MEDIUM | HIGH
    status VARCHAR(20) NOT NULL DEFAULT 'NEEDS_REVIEW', -- NEEDS_REVIEW | VERIFIED
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS contest_sources (
    id BIGSERIAL PRIMARY KEY,
    contest_id BIGINT NOT NULL REFERENCES contests(id) ON DELETE CASCADE,
    source_id BIGINT REFERENCES sources(id),
    source_url VARCHAR(2000) NOT NULL,
    collected_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (contest_id, source_url)
);

-- Kept separate from `contests` so JPA/Hibernate never has to understand the `vector` type;
-- all reads/writes to this table go through native SQL (VectorSearchRepository).
CREATE TABLE IF NOT EXISTS contest_embeddings (
    contest_id BIGINT PRIMARY KEY REFERENCES contests(id) ON DELETE CASCADE,
    embedding vector(768) NOT NULL
);

CREATE TABLE IF NOT EXISTS app_users (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL UNIQUE, -- anonymous id from X-User-Id header, no PII required
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES app_users(id) ON DELETE CASCADE,
    interests VARCHAR(500), -- comma-separated
    major_or_job VARCHAR(200),
    region VARCHAR(100),
    age INTEGER,
    eligibility_note VARCHAR(500),
    team_preference VARCHAR(20) NOT NULL DEFAULT 'ANY', -- INDIVIDUAL | TEAM | ANY
    prize_preference VARCHAR(20) NOT NULL DEFAULT 'ANY', -- LOW | MEDIUM | HIGH | ANY
    deadline_preference_days INTEGER,
    online_preference VARCHAR(20) NOT NULL DEFAULT 'ANY', -- ONLINE | OFFLINE | ANY
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS feedback (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    contest_id BIGINT NOT NULL REFERENCES contests(id) ON DELETE CASCADE,
    feedback_type VARCHAR(20) NOT NULL, -- NOT_INTERESTED | SAVED | PLAN_TO_APPLY | APPLIED
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (user_id, contest_id)
);

CREATE INDEX IF NOT EXISTS idx_contests_deadline ON contests(deadline);
CREATE INDEX IF NOT EXISTS idx_contests_region ON contests(region);
CREATE INDEX IF NOT EXISTS idx_raw_documents_processed ON raw_documents(processed);
