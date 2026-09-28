package com.tutorcraft.core.identity.application;

/** Коды ошибок модуля identity (тексты — i18n/identity*.properties). */
public final class IdentityErrors {

    public static final String INVALID_CREDENTIALS = "auth.invalid_credentials";
    public static final String TENANT_REQUIRED = "auth.tenant_required";
    public static final String ACCOUNT_SUSPENDED = "auth.account_suspended";
    public static final String TOO_MANY_ATTEMPTS = "auth.too_many_attempts";
    public static final String REFRESH_INVALID = "auth.refresh_invalid";
    public static final String ORIGIN_MISMATCH = "auth.origin_mismatch";
    public static final String PROVIDER_DISABLED = "auth.provider_disabled";
    public static final String OAUTH_INVALID = "auth.oauth_invalid";
    public static final String OAUTH_EMAIL_UNVERIFIED = "auth.oauth_email_unverified";
    public static final String TOKEN_INVALID = "auth.token_invalid";
    public static final String TENANT_NOT_FOUND = "auth.tenant_not_found";
    public static final String USER_NOT_FOUND = "user.not_found";
    public static final String EMAIL_TAKEN = "user.email_taken";
    public static final String NOT_INVITED = "user.not_invited";
    public static final String CANNOT_SUSPEND_SELF = "user.cannot_suspend_self";
    public static final String CANNOT_DEMOTE_SELF = "user.cannot_demote_self";
    public static final String IMPORT_PREVIEW_NOT_FOUND = "user.import_preview_not_found";
    public static final String IMPORT_FILE_TOO_LARGE = "user.import_file_too_large";
    public static final String IMPORT_TOO_MANY_ROWS = "user.import_too_many_rows";
    public static final String IMPORT_BAD_HEADER = "user.import_bad_header";
    public static final String IMPORT_UNREADABLE = "user.import_unreadable";

    private IdentityErrors() {
    }
}
