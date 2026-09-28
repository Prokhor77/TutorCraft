package com.tutorcraft.core.identity.web;

import com.tutorcraft.core.identity.application.IssuedSession;
import com.tutorcraft.core.identity.application.MeService;
import com.tutorcraft.core.identity.application.MeService.ProfilePatch;
import com.tutorcraft.core.identity.application.MeView;
import com.tutorcraft.core.identity.application.PasswordService;
import com.tutorcraft.core.identity.application.TelegramLinkService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Профиль текущего пользователя (контракт §2: /me, /me/password, /me/telegram/link). */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

    private static final int MAX_PASSWORD = 128;
    private static final int MAX_NAME = 100;
    private static final int MAX_ZONE = 64;

    private final MeService me;
    private final PasswordService passwords;
    private final TelegramLinkService telegramLinks;
    private final RefreshCookies cookies;

    MeController(MeService me, PasswordService passwords, TelegramLinkService telegramLinks, RefreshCookies cookies) {
        this.me = me;
        this.passwords = passwords;
        this.telegramLinks = telegramLinks;
        this.cookies = cookies;
    }

    @GetMapping
    MeView get() {
        return me.me();
    }

    @PatchMapping
    MeView update(@Valid @RequestBody PatchMeRequest request) {
        return me.update(new ProfilePatch(request.firstName(), request.lastName(), request.timezone(), request.locale(),
                request.avatarFileId()));
    }

    /** Все сессии отзываются; текущий клиент получает новую refresh-cookie и остаётся в системе. */
    @PostMapping("/password")
    ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        IssuedSession session = passwords.change(request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(session.refreshToken(), session.refreshTtl()))
                .build();
    }

    @PostMapping("/telegram/link")
    TelegramLinkResponse telegramLink() {
        return new TelegramLinkResponse(telegramLinks.createDeepLink());
    }

    record PatchMeRequest(@Size(max = MAX_NAME) String firstName, @Size(max = MAX_NAME) String lastName,
                          @Size(max = MAX_ZONE) String timezone, String locale, UUID avatarFileId) {
    }

    record ChangePasswordRequest(@Size(max = MAX_PASSWORD) String currentPassword,
                                 @NotBlank @Size(max = MAX_PASSWORD) String newPassword) {

        @Override
        public String toString() {
            return "ChangePasswordRequest[***]";
        }
    }

    record TelegramLinkResponse(String deepLink) {
    }
}
