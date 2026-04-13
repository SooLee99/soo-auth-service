package io.soo.springboot.storage.db.core.authmethod

import io.soo.springboot.core.enums.AuthMethod
import io.soo.springboot.storage.db.core.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "auth_method_config")
class AuthMethodConfigEntity(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    var method: AuthMethod,

    @Column(nullable = false)
    var enabled: Boolean,
) : BaseEntity()
