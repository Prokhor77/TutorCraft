package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.identity.domain.PasswordRules;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.org.OrgApi.PasswordPolicy;
import com.tutorcraft.core.shared.domain.FieldViolation;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.i18n.Messages;
import com.tutorcraft.core.shared.security.PasswordHasher;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Проверка пароля по политике tenant и хеширование (FR-AUTH-01). */
@Component
public class PasswordPolicyEnforcer {

    private static final String MESSAGE_PREFIX = "identity.password.";

    private final OrgApi org;
    private final PasswordHasher hasher;
    private final Messages messages;

    public PasswordPolicyEnforcer(OrgApi org, PasswordHasher hasher, Messages messages) {
        this.org = org;
        this.hasher = hasher;
        this.messages = messages;
    }

    /** @return argon2id-хеш допустимого пароля; иначе ValidationException по полю {@code field} */
    public String validateAndHash(UUID tenantId, String password, String field) {
        PasswordRules rules = rulesOf(org.require(tenantId).passwordPolicy());
        List<FieldViolation> violations = rules.violations(password).stream()
                .map(code -> new FieldViolation(field, code, messages.get(MESSAGE_PREFIX + code, rules.minLength())))
                .toList();
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
        return hasher.hash(password);
    }

    private static PasswordRules rulesOf(PasswordPolicy policy) {
        if (policy == null) {
            return new PasswordRules(PasswordRules.ABSOLUTE_MIN_LENGTH, false, false);
        }
        return new PasswordRules(policy.minLength(), policy.requireDigit(), policy.requireLetter());
    }
}
