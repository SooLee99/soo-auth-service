package io.soo.springboot.storage.db.core.sms

import io.soo.springboot.storage.db.core.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "sms_log",
    indexes = [
        Index(name = "idx_sms_log_created_at", columnList = "createdAt"),
        Index(name = "idx_sms_log_ok", columnList = "ok"),
    ],
)
class SmsLogEntity(
    @Column(nullable = false, length = 30)
    var smsTo: String,

    @Column(nullable = false, length = 30)
    var smsFrom: String,

    @Column(nullable = false, length = 1000)
    var smsText: String,

    @Column(nullable = false)
    var ok: Boolean,

    @Column(nullable = false, length = 30)
    var provider: String,

    @Column(length = 100)
    var code: String? = null,

    @Column(length = 500)
    var msg: String? = null,
) : BaseEntity()
