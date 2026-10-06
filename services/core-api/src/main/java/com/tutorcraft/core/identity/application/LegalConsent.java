package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.audit.AuditRecord;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Согласие с публичной офертой, политикой обработки персональных данных и на трансграничную передачу данных (Закон РБ
 * № 99-З, ст. 5 и 9): оператор обязан зафиксировать дату и содержание согласия. Веб-клиент отправляет
 * {@code acceptTerms: true} только когда отмечены все три галочки; факт согласия пишется в журнал аудита вместе с
 * редакцией каждого документа.
 */
public final class LegalConsent {

    /** Редакция оферты, политики и согласия на трансграничную передачу; совпадает с {@code LEGAL_DOCUMENTS_VERSION} в apps/web/src/content/legal. */
    public static final String DOCUMENTS_VERSION = "2026-10-06";
    static final String FIELD = "acceptTerms";
    static final String REQUIRED_CODE = "consent_required";
    static final String REQUIRED_MESSAGE = "Offer, personal data policy and cross-border transfer consent must be accepted";
    private static final String ACTION = "legal.consent_accepted";

    private LegalConsent() {
    }

    /** Как было получено согласие: отметка в форме регистрации, приглашения или первый вход через OAuth. */
    public enum Method {
        REGISTER, INVITATION, OAUTH_GOOGLE, OAUTH_TELEGRAM;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    static AuditRecord record(UUID tenantId, UUID userId, Method method) {
        return AuditRecord.of(tenantId, userId, ACTION, "user", userId.toString())
            .withDiff(Map.of("offerVersion", DOCUMENTS_VERSION, "privacyVersion", DOCUMENTS_VERSION,
                    "crossBorderVersion", DOCUMENTS_VERSION, "method", method.key()));
    }
}
