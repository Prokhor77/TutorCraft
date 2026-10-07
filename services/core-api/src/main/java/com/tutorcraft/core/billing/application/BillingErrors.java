package com.tutorcraft.core.billing.application;

/** Коды ошибок подписки (тексты — i18n/billing*.properties). */
public final class BillingErrors {

    public static final String SUBSCRIPTION_INACTIVE = "billing.subscription_inactive";
    public static final String SUBSCRIPTION_CHECKOUT_UNAVAILABLE = "billing.subscription_checkout_unavailable";
    public static final String TRIAL_END_OUT_OF_RANGE = "billing.trial_end_out_of_range";

    private BillingErrors() {
    }
}
