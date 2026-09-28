package com.tutorcraft.core.billing.infrastructure;

import com.tutorcraft.core.billing.application.OrderRepository;
import com.tutorcraft.core.billing.domain.Order;
import com.tutorcraft.core.billing.domain.OrderStatus;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.domain.Money;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOrderRepository implements OrderRepository {

    private static final String FIELDS = """
            id, tenant_id, course_id, buyer_id, amount_minor, currency, status, provider, provider_payment_id,
            confirmation_url, created_at, paid_at, version
            """;

    private final JdbcClient jdbc;
    private final JsonCodec json;

    JdbcOrderRepository(JdbcClient jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insert(Order order) {
        jdbc.sql("""
                INSERT INTO orders (id, tenant_id, course_id, buyer_id, amount_minor, currency, status, provider,
                                    created_at, updated_at, version)
                VALUES (:id, :tenantId, :courseId, :buyerId, :amount, :currency, :status, :provider, :now, :now, 0)
                """)
            .param("id", order.id()).param("tenantId", order.tenantId()).param("courseId", order.courseId())
            .param("buyerId", order.buyerId()).param("amount", order.amount().amountMinor())
            .param("currency", order.amount().currency()).param("status", order.status().key())
            .param("provider", order.provider()).param("now", Timestamps.of(order.createdAt()))
            .update();
    }

    @Override
    public void attachPayment(UUID tenantId, UUID orderId, String providerPaymentId, String confirmationUrl) {
        jdbc.sql("""
                UPDATE orders SET provider_payment_id = :paymentId, confirmation_url = :url, version = version + 1
                WHERE tenant_id = :tenantId AND id = :id
                """)
            .param("paymentId", providerPaymentId).param("url", confirmationUrl).param("tenantId", tenantId).param("id", orderId)
            .update();
    }

    @Override
    public Optional<Order> find(UUID tenantId, UUID orderId) {
        return jdbc.sql("SELECT " + FIELDS + " FROM orders WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId).param("id", orderId)
                .query((rs, n) -> toOrder(rs)).optional();
    }

    @Override
    public Optional<Order> findByProviderPayment(String provider, String providerPaymentId) {
        return jdbc.sql("SELECT " + FIELDS + " FROM orders WHERE provider = :provider AND provider_payment_id = :paymentId")
                .param("provider", provider).param("paymentId", providerPaymentId)
                .query((rs, n) -> toOrder(rs)).optional();
    }

    @Override
    public boolean transition(UUID tenantId, UUID orderId, OrderStatus from, OrderStatus to, Instant paidAt, Instant now) {
        return jdbc.sql("""
                UPDATE orders SET status = :to, paid_at = COALESCE(CAST(:paidAt AS timestamptz), paid_at), updated_at = :now, version = version + 1
                WHERE tenant_id = :tenantId AND id = :id AND status = :from
                """)
            .param("to", to.key()).param("paidAt", Timestamps.of(paidAt)).param("now", Timestamps.of(now))
            .param("tenantId", tenantId).param("id", orderId).param("from", from.key())
            .update() == 1;
    }

    @Override
    public List<Order> list(UUID tenantId, UUID courseId, PageQuery page) {
        StringBuilder sql = new StringBuilder("SELECT " + FIELDS + " FROM orders WHERE tenant_id = :tenantId"
                + " AND (CAST(:courseId AS uuid) IS NULL OR course_id = :courseId)");
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", tenantId);
        params.put("courseId", courseId);
        params.put("limit", page.fetchSize());
        page.after().ifPresent(after -> {
            sql.append(" AND (created_at, id) < (:afterAt, :afterId)");
            params.put("afterAt", Timestamps.of(after.sortKey()));
            params.put("afterId", after.id());
        });
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :limit");
        return jdbc.sql(sql.toString()).params(params).query((rs, n) -> toOrder(rs)).list();
    }

    @Override
    public boolean recordPaymentEvent(String provider, String eventId, UUID orderId, Map<String, Object> summary, Instant now) {
        return jdbc.sql("""
                INSERT INTO payment_events (provider, provider_event_id, order_id, payload, received_at)
                VALUES (:provider, :eventId, :orderId, :payload, :now)
                ON CONFLICT DO NOTHING
                """)
            .param("provider", provider).param("eventId", eventId).param("orderId", orderId)
            .param("payload", json.toJsonb(summary)).param("now", Timestamps.of(now))
            .update() == 1;
    }

    private static Order toOrder(ResultSet rs) throws SQLException {
        return new Order(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("course_id", UUID.class), rs.getObject("buyer_id", UUID.class),
                new Money(rs.getLong("amount_minor"), rs.getString("currency")), OrderStatus.fromKey(rs.getString("status")),
                rs.getString("provider"), rs.getString("provider_payment_id"), rs.getString("confirmation_url"),
                Timestamps.read(rs, "created_at"), Timestamps.read(rs, "paid_at"), rs.getLong("version"));
    }
}
