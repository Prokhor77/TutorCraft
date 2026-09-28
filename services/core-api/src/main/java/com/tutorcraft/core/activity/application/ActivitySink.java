package com.tutorcraft.core.activity.application;

import com.tutorcraft.core.activity.domain.ActivityEntry;

/**
 * Порт записи журнала активности. Реализация не блокирует запрос и не бросает исключений: журнал — вспомогательная
 * функция и не должен ломать основной сценарий (NFR-REL-04).
 */
public interface ActivitySink {

    void submit(ActivityEntry entry);
}
