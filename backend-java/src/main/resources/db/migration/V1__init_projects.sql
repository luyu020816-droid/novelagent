CREATE TABLE projects (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    language VARCHAR(32) DEFAULT 'zh-CN',
    target_chapters INT DEFAULT 100,
    current_chapter INT DEFAULT 0,
    status VARCHAR(32) DEFAULT 'created',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
