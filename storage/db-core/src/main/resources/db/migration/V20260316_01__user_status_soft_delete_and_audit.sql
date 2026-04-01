-- 가정:
-- 1) DB naming strategy는 snake_case를 사용한다.
-- 2) 기존 user_entity 테이블이 이미 존재한다.
-- 3) 운영 환경에서는 flyway/liquibase가 없을 수 있으므로 수동 적용 가능한 DDL을 제공한다.

ALTER TABLE user_entity
    ADD COLUMN user_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN blocked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN blocked_reason VARCHAR(500) NULL,
    ADD COLUMN blocked_at TIMESTAMP NULL,
    ADD COLUMN blocked_by_admin_id BIGINT NULL,
    ADD COLUMN unblocked_at TIMESTAMP NULL,
    ADD COLUMN unblocked_by_admin_id BIGINT NULL,
    ADD COLUMN deleted_at TIMESTAMP NULL,
    ADD COLUMN deletion_reason VARCHAR(500) NULL,
    ADD COLUMN retention_until TIMESTAMP NULL;

CREATE INDEX ix_user_status ON user_entity(user_status);
CREATE INDEX ix_user_deleted_at ON user_entity(deleted_at);
CREATE INDEX ix_user_retention_until ON user_entity(retention_until);

CREATE TABLE user_status_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    entity_status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    target_user_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NULL,
    action_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_user_status_audit_log_user_id ON user_status_audit_log(target_user_id);
CREATE INDEX ix_user_status_audit_log_created_at ON user_status_audit_log(action_at);
