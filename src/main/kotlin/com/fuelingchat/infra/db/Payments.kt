package com.fuelingchat.infra.db

import org.jetbrains.exposed.sql.Table

object Payments : Table("payments") {
    val paymentId = text("payment_id")
    val orderId = text("order_id")
    val userId = text("user_id")
    val externalPaymentId = text("external_payment_id").nullable()
    val status = text("status").nullable()
    val createdAt = long("created_at").nullable()
    val updatedAt = long("updated_at").nullable()
    val paymentSystem = varchar("payment_system", 64)
    val paymentMethod = varchar("payment_method", 64)
    val paymentType = text("payment_type")
    val purpose = text("purpose")
    val amount = decimal("amount", 10, 2).nullable()
    val actualAmount = decimal("actual_amount", 10, 2).nullable()
    val cardBindingId = text("card_binding_id").nullable()
    val cardBindingType = text("card_binding_type").nullable()
    val sbpSubscriptionId = text("sbp_subscription_id").nullable()

    override val primaryKey = PrimaryKey(paymentId)
}
