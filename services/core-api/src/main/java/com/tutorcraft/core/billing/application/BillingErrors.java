package com.tutorcraft.core.billing.application;

/** Коды ошибок биллинга (тексты — i18n/billing*.properties). */
public final class BillingErrors {

    public static final String ORDER_NOT_FOUND = "billing.order_not_found";
    public static final String NOT_FOR_SALE = "billing.course_not_for_sale";
    public static final String ALREADY_ENROLLED = "billing.already_enrolled";
    public static final String PROVIDER_NOT_FOUND = "billing.provider_not_found";
    public static final String PROVIDER_UNAVAILABLE = "billing.provider_unavailable";
    public static final String WEBHOOK_INVALID = "billing.webhook_invalid";
    public static final String FAKE_DISABLED = "billing.fake_disabled";

    private BillingErrors() {
    }
}
