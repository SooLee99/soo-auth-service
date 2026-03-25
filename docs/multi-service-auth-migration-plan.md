# Multi-Service Auth Migration Plan (Draft)

## Scope
- Goal: one auth server supports multiple frontend services.
- Strategy: global user account + service-specific membership.
- Trust model: `serviceCode` is received from frontend, but server validates against DB + client metadata.

## Data Model

### 1) `service_entity`
- purpose: registry of onboarded services (tenant-like)
- key fields:
  - `id` (PK)
  - `service_code` (unique, immutable, e.g. `SHOP`, `CRM`)
  - `service_name`
  - `service_status` (`ACTIVE`, `INACTIVE`)
  - base audit columns (`created_at`, `updated_at`, `entity_status`, `version`)

### 2) `service_membership`
- purpose: user join/state per service
- key fields:
  - `id` (PK)
  - `service_id` (FK -> `service_entity.id`)
  - `user_id` (FK -> `user_entity.id`)
  - `membership_status` (`ACTIVE`, `WITHDRAWN`, `BLOCKED`)
  - `membership_role` (`USER`, `ADMIN`) - optional but recommended
  - `joined_at`, `withdrawn_at`, `withdraw_reason`
  - base audit columns
- constraints:
  - unique `(service_id, user_id)`

### 3) `refresh_token_entity` extension
- add `service_id` (FK -> `service_entity.id`)
- token lifecycle becomes service-bound.

## SQL Draft (Manual Draft, Do Not Auto-Run)

```sql
-- 1) service registry
CREATE TABLE service_entity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    entity_status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    service_code VARCHAR(64) NOT NULL,
    service_name VARCHAR(120) NOT NULL,
    service_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT uk_service_code UNIQUE (service_code)
);

CREATE INDEX ix_service_status ON service_entity(service_status);

-- 2) user <-> service membership
CREATE TABLE service_membership (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    entity_status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    service_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    membership_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    membership_role VARCHAR(20) NOT NULL DEFAULT 'USER',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    withdrawn_at TIMESTAMP NULL,
    withdraw_reason VARCHAR(500) NULL,

    CONSTRAINT fk_membership_service FOREIGN KEY (service_id) REFERENCES service_entity(id),
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES user_entity(id),
    CONSTRAINT uk_membership_service_user UNIQUE (service_id, user_id)
);

CREATE INDEX ix_membership_user_status ON service_membership(user_id, membership_status);
CREATE INDEX ix_membership_service_status ON service_membership(service_id, membership_status);

-- 3) refresh token is service-scoped
ALTER TABLE refresh_token_entity
    ADD COLUMN service_id BIGINT NULL;

ALTER TABLE refresh_token_entity
    ADD CONSTRAINT fk_refresh_token_service
    FOREIGN KEY (service_id) REFERENCES service_entity(id);

CREATE INDEX ix_refresh_token_user_service_device
    ON refresh_token_entity(user_id, service_id, device_id);

-- 4) bootstrap default service + backfill memberships
INSERT INTO service_entity(service_code, service_name, service_status)
VALUES ('DEFAULT', 'Default Service', 'ACTIVE');

INSERT INTO service_membership(service_id, user_id, membership_status, membership_role, joined_at)
SELECT s.id, u.id, 'ACTIVE', 'USER', NOW()
FROM user_entity u
JOIN service_entity s ON s.service_code = 'DEFAULT'
LEFT JOIN service_membership m ON m.service_id = s.id AND m.user_id = u.id
WHERE m.id IS NULL;

-- 5) harden refresh_token_entity after backfill
UPDATE refresh_token_entity rt
JOIN service_entity s ON s.service_code = 'DEFAULT'
SET rt.service_id = s.id
WHERE rt.service_id IS NULL;

ALTER TABLE refresh_token_entity
    MODIFY COLUMN service_id BIGINT NOT NULL;
```

## API Contract Draft

### Local Auth
- `POST /api/v1/services/{serviceCode}/auth/local/signup`
- `POST /api/v1/services/{serviceCode}/auth/local/login`
- `POST /api/v1/services/{serviceCode}/auth/local/token/refresh`
- `POST /api/v1/services/{serviceCode}/auth/local/logout`
- `POST /api/v1/services/{serviceCode}/auth/local/withdraw`

### OAuth2 Auth
- `GET /api/v1/services/{serviceCode}/auth/oauth2/{provider}/authorize-url`

## Token Claim Draft
- `uid`: user id (existing)
- `sid`: service id (new)
- `scd`: service code (new)
- `roles`: authorities (existing)

## File-by-File TODO

### DB Core (`storage/db-core`)
1. Add new model/entity/repository
- `Service.kt`, `ServiceEntity.kt`, `ServiceJpaRepository.kt`, `ServiceRepository.kt`, `ServiceRepositoryImpl.kt`
- `ServiceMembership.kt`, `ServiceMembershipEntity.kt`, `ServiceMembershipJpaRepository.kt`, `ServiceMembershipRepository.kt`, `ServiceMembershipRepositoryImpl.kt`

2. Extend refresh token model with `serviceId`
- Update:
  - `RefreshToken.kt`
  - `RefreshTokenEntity.kt`
  - `RefreshTokenJpaRepository.kt`
  - `RefreshTokenRepository.kt`
  - `RefreshTokenRepositoryImpl.kt`

3. Add migration file (real flyway versioned SQL)
- create new migration after `V20260316_01__...`

### Core API (`core/core-api`)
1. Service resolver/validator
- Add `ServiceContextResolver`:
  - resolves `{serviceCode}`
  - verifies service exists and is `ACTIVE`
  - (optional) validates origin/client binding

2. Local auth domain changes
- Update `LocalSignUpCommand` with `serviceId/serviceCode`
- Update `LocalAccountService.signUp`:
  - create/find global user
  - create `service_membership` as `ACTIVE`
- Add service-scoped withdraw option (membership withdraw), and keep global withdraw separate.

3. Local login flow
- `LocalJsonLoginFilter`: include `serviceCode` path variable in request attribute
- `LocalLoginSuccessHandler`: pass `serviceId` to token issue
- `UserDetailsServiceImpl`: after credential validation, verify membership is ACTIVE for requested service

4. Token manager changes
- `AuthTokenManager.issue/refresh`: include `serviceId`
- `AccessTokenIssuer.issue`: include `sid`, `scd` claims
- `RefreshTokenManager.issue/rotate/revoke*`: service-bound operations

5. Controller path migration
- Replace/duplicate:
  - `LocalAccountController`
  - `OAuth2AccountController`
- New base path: `/api/v1/services/{serviceCode}/...`
- Keep legacy routes temporarily with deprecation response header.

### Security & Authorization
1. Add service claim validation for protected endpoints.
2. Reject requests where token service claim and path service mismatch.

### Tests
1. Signup/login with same email across multiple services.
2. Service A withdraw does not block Service B.
3. Refresh token from Service A cannot refresh under Service B path.
4. Legacy route compatibility tests (if migration window needed).

## Rollout Plan (No-Downtime)
1. Deploy DB migration (tables + nullable `refresh_token_entity.service_id`).
2. Backfill default service + memberships.
3. Deploy application that writes/reads `service_id`.
4. Enforce `service_id NOT NULL` on `refresh_token_entity`.
5. Switch frontend to `/api/v1/services/{serviceCode}/...`.
6. Remove legacy routes after cutoff date.

## Cutover Checklist
- [ ] Migration applied in staging + data backfill verified.
- [ ] New endpoints enabled and documented.
- [ ] Tokens include `sid/scd`.
- [ ] Refresh/logouts verified service-scoped.
- [ ] Monitoring dashboards split by service code.
- [ ] Legacy endpoints announce deprecation.

