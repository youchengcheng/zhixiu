-- 已有 MySQL 库执行一次。新库初始化脚本已包含此列。
ALTER TABLE ai_chat_message
    ADD COLUMN citations_verified TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '引用是否经过核验'
    AFTER citations;
