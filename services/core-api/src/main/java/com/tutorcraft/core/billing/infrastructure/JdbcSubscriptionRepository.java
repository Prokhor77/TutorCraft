package com.tutorcraft.core.billing.infrastructure;

import com.tutorcraft.core.billing.application.SubscriptionRepository;
import com.tutorcraft.core.billing.domain.SubscriptionPayment;
import com.tutorcraft.core.billing.domain.SubscriptionTerm;
import com.tutorcraft.core.billing.domain.TenantSubscription;
import com.tutorcraft.core.shared.domain.Money;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSubscriptionRepository implements SubscriptionRepository {

    private final JdbcClient jdbc;

    JdbcSubscriptionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TenantSubscription findOrCreate(TenantSubscription trial, Instant now) {
        jdbc.sql("""
                INSERT INTO tenant_subscriptions (tenant_id, trial_ends_at, paid_until, version, created_at, updated_at)
                VALUES (:tenantId, :trialEndsAt, NULL, 0, :now, :now)
                ON CONFLICT (tenant_id) DO NOTHING
                """)
            .param("tenantId", trial.tenantId()).param("trialEndsAt", Timestamps.of(trial.trialEndsAt()))
            .param("now", Timestamps.of(now))
            .update();
        return jdbc.sql("""
                SELECT tenant_id, trial_ends_at, paid_until, version FROM tenant_subscriptions
                WHERE tenant_id = :tenantId
                """)
            .param("tenantId", trial.tenantId())
            .query(JdbcSubscriptionRepository::mapSubscription)
            .single();
    }

    @Override
    public boolean updatePaidUntil(UUID tenantId, Instant paidUntil, long expectedVersion, Instant now) {
        return jdbc.sql("""
                UPDATE tenant_subscriptions SET paid_until = :paidUntil, version = version + 1, updated_at = :now
                WHERE tenant_id = :tenantId AND version = :expected
                """)
            .param("paidUntil", Timestamps.of(paidUntil)).param("now", Timestamps.of(now))
            .param("tenantId", tenantId).param("expected", expectedVersion)
            .update() == 1;
    }

    @Override
    public void insertPayment(SubscriptionPayment payment) {
        jdbc.sql("""
                INSERT INTO subscription_payments (id, tenant_id, term, amount_minor, currency, provider,
                                                   period_start, period_end, paid_by, created_at)
                VALUES (:id, :tenantId, :term, :amount, :currency, :provider, :start, :end, :paidBy, :createdAt)
                """)
            .param("id", payment.id()).param("tenantId", payment.tenantId()).param("term", payment.term().key())
            .param("amount", payment.amount().amountMinor()).param("currency", payment.amount().currency())
            .param("provider", payment.provider()).param("start", Timestamps.of(payment.periodStart()))
            .param("end", Timestamps.of(payment.periodEnd())).param("paidBy", payment.paidBy())
            .param("createdAt", Timestamps.of(payment.createdAt()))
            .update();
    }

    @Override
    public List<SubscriptionPayment> recentPayments(UUID tenantId, int limit) {
        return jdbc.sql("""
                SELECT id, tenant_id, term, amount_minor, currency, provider, period_start, period_end, paid_by,
                       created_at
                FROM subscription_payments WHERE tenant_id = :tenantId
                ORDER BY created_at DESC, id DESC LIMIT :limit
                """)
            .param("tenantId", tenantId).param("limit", limit)
            .query(JdbcSubscriptionRepository::mapPayment)
            .list();
    }

    private static TenantSubscription mapSubscription(ResultSet rs, int row) throws SQLException {
        return new TenantSubscription(rs.getObject("tenant_id", UUID.class), Timestamps.read(rs, "trial_ends_at"),
                Timestamps.read(rs, "paid_until"), rs.getLong("version"));
    }

    private static SubscriptionPayment mapPayment(ResultSet rs, int row) throws SQLException {
        return new SubscriptionPayment(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                SubscriptionTerm.fromKey(rs.getString("term")),
                new Money(rs.getLong("amount_minor"), rs.getString("currency")), rs.getString("provider"),
                Timestamps.read(rs, "period_start"), Timestamps.read(rs, "period_end"),
                rs.getObject("paid_by", UUID.class), Timestamps.read(rs, "created_at"));
    }
}
