package com.tutorcraft.core.integrations.application;

import java.net.URI;

/** Порт HTTP-отправки вебхука. Не бросает исключений: ошибка сети возвращается в {@link Result#error()}. */
public interface WebhookSender {

    Result send(URI url, String body, String signature);

    /** statusCode — null, если ответа не было (таймаут, отказ соединения). */
    record Result(Integer statusCode, String error) {

        private static final int SUCCESS_MIN = 200;
        private static final int SUCCESS_MAX = 299;

        public boolean successful() {
            return statusCode != null && statusCode >= SUCCESS_MIN && statusCode <= SUCCESS_MAX;
        }
    }
}
