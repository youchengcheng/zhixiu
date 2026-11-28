CREATE TABLE IF NOT EXISTS ai_concierge_session (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_code VARCHAR(16) NOT NULL,
    title VARCHAR(80) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_concierge_session_owner (user_id, role_code, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_concierge_message (
    id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    request_id CHAR(36) NOT NULL,
    user_content VARCHAR(1000) NOT NULL,
    context_order_id BIGINT DEFAULT NULL,
    answer TEXT DEFAULT NULL,
    citations JSON DEFAULT NULL,
    query_spec JSON DEFAULT NULL,
    state VARCHAR(16) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_concierge_message_request (session_id, request_id),
    KEY idx_concierge_message_history (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
