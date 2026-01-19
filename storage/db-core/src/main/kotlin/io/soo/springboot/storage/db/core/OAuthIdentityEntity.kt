package io.soo.springboot.storage.db.core

import io.soo.springboot.core.enums.AuthProvider
import jakarta.persistence.*

@Entity
@Table(
    name = "oauth_identity",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_oauth_identity_provider_user",
            columnNames = ["provider", "provider_user_id"],
        ),
    ],
    indexes = [Index(name = "idx_oauth_identity_user_id", columnList = "user_id")],
)
@AttributeOverride(
    name = "status",
    column = Column(name = "status", columnDefinition = "VARCHAR", nullable = false),
)
class OAuthIdentityEntity(

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    var provider: AuthProvider,

    @Column(name = "provider_user_id", nullable = false, length = 255)
    var providerUserId: String,

    // Kakao CI 등 “추가 식별자”
    @Column(name = "ci", length = 255)
    var ci: String? = null,

    @Lob
    @Column(name = "raw_attributes_json")
    var rawAttributesJson: String? = null,

    // (OIDC 쓸 때) id_token claims 저장(선택)
    @Lob
    @Column(name = "id_token_claims_json")
    var idTokenClaimsJson: String? = null,

    ) : BaseEntity()