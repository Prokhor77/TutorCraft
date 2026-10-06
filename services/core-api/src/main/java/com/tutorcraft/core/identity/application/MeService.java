package com.tutorcraft.core.identity.application;

import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.files.FilesApi;
import com.tutorcraft.core.files.FilesApi.FileRef;
import com.tutorcraft.core.identity.application.UserRepository.ProfileUpdate;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.shared.domain.NotFoundException;
import com.tutorcraft.core.shared.domain.ValidationException;
import com.tutorcraft.core.shared.domain.Validator;
import com.tutorcraft.core.shared.security.CurrentUser;
import com.tutorcraft.core.shared.security.CurrentUserProvider;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Профиль текущего пользователя (/me). */
@Service
public class MeService {

    public static final String AVATAR_OWNER_TYPE = "user";
    private static final Set<String> LOCALES = Set.of("ru", "en", "uz");
    private static final String IMAGE_MIME_PREFIX = "image/";
    private static final String AVATAR_FIELD = "avatarFileId";
    private static final int MAX_NAME_LENGTH = 100;

    private final UserRepository users;
    private final CurrentUserProvider currentUser;
    private final MeViewAssembler assembler;
    private final FilesApi files;
    private final AuditLog audit;

    public MeService(UserRepository users, CurrentUserProvider currentUser, MeViewAssembler assembler, FilesApi files,
                     AuditLog audit) {
        this.users = users;
        this.currentUser = currentUser;
        this.assembler = assembler;
        this.files = files;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public MeView me() {
        return assembler.assemble(requireCurrent());
    }

    @Transactional
    public MeView update(ProfilePatch patch) {
        validate(patch);
        UserAccount user = requireCurrent();
        UUID avatarId = patch.avatarFileId() == null ? user.avatarFileId() : attachAvatar(user, patch.avatarFileId());
        users.updateProfile(user.tenantId(), user.id(), new ProfileUpdate(
                trimOr(patch.firstName(), user.firstName()), trimOr(patch.lastName(), user.lastName()),
                Objects.requireNonNullElse(patch.timezone(), user.timezone()),
                Objects.requireNonNullElse(patch.locale(), user.locale()), avatarId));
        audit.record(AuditRecord.of(user.tenantId(), user.id(), "user.profile_updated", "user", user.id().toString()));
        return assembler.assemble(requireCurrent());
    }

    private UUID attachAvatar(UserAccount user, UUID fileId) {
        if (fileId.equals(user.avatarFileId())) {
            return fileId;
        }
        FileRef file = files.requireReady(user.tenantId(), fileId, AVATAR_FIELD);
        if (file.mime() == null || !file.mime().startsWith(IMAGE_MIME_PREFIX)) {
            throw ValidationException.single(AVATAR_FIELD, "not_image", "Avatar must be an image");
        }
        files.link(user.tenantId(), fileId, AVATAR_OWNER_TYPE, user.id());
        return fileId;
    }

    private UserAccount requireCurrent() {
        CurrentUser current = currentUser.require();
        return users.findById(current.tenantId(), current.userId())
                .orElseThrow(() -> new NotFoundException(IdentityErrors.USER_NOT_FOUND, "User not found"));
    }

    private static void validate(ProfilePatch patch) {
        new Validator()
            .check(patch.firstName() == null || !patch.firstName().isBlank(), "firstName", "required", "Field is required")
            .maxLength(patch.firstName(), MAX_NAME_LENGTH, "firstName")
            .check(patch.lastName() == null || !patch.lastName().isBlank(), "lastName", "required", "Field is required")
            .maxLength(patch.lastName(), MAX_NAME_LENGTH, "lastName")
            .check(patch.timezone() == null || isZone(patch.timezone()), "timezone", "invalid", "Unknown time zone")
            .check(patch.locale() == null || LOCALES.contains(patch.locale()), "locale", "invalid", "Supported: ru, en, uz")
            .throwIfInvalid();
    }

    private static boolean isZone(String zone) {
        try {
            ZoneId.of(zone);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    private static String trimOr(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    public record ProfilePatch(String firstName, String lastName, String timezone, String locale, UUID avatarFileId) {
    }
}
