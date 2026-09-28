package com.tutorcraft.core.progress.application;

import com.tutorcraft.core.progress.domain.ConditionGroup.Operator;
import com.tutorcraft.core.progress.domain.Evaluation;
import com.tutorcraft.core.progress.domain.Reason;
import com.tutorcraft.core.shared.i18n.Messages;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Локализованные пояснения недоступности (UX-07, AC-6): «Откроется, когда: оценка за „Задание 1“ не ниже 60% (сейчас 50%)».
 * Условия, которые ещё могут выполниться, объединяются в одну фразу («; » для all, « или » для any);
 * истёкшее окно доступа — отдельной фразой.
 */
@Component
public class ReasonTexts {

    private static final String PREFIX = "progress.reason.";
    private static final int MAX_FRACTION_DIGITS = 1;

    private final Messages messages;

    public ReasonTexts(Messages messages) {
        this.messages = messages;
    }

    public List<String> format(Evaluation evaluation, Map<UUID, String> itemTitles, ZoneId zone, Locale locale) {
        if (evaluation.available()) {
            return List.of();
        }
        Context context = new Context(itemTitles, zone, locale);
        List<String> opening = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (Reason reason : evaluation.reasons()) {
            if (reason.code() == Reason.Code.DATE_UNTIL) {
                texts.add(messages.get(locale, PREFIX + "closed", date(reason.at(), context)));
            } else {
                opening.add(describe(reason, context));
            }
        }
        if (!opening.isEmpty()) {
            String separator = messages.get(locale, evaluation.operator() == Operator.ANY
                    ? "progress.separator.any" : "progress.separator.all");
            texts.add(0, messages.get(locale, "progress.locked", String.join(separator, opening)));
        }
        return List.copyOf(texts);
    }

    private String describe(Reason reason, Context context) {
        Locale locale = context.locale();
        return switch (reason.code()) {
            case DATE_FROM -> messages.get(locale, PREFIX + "date_from", date(reason.at(), context));
            case COMPLETION_COMPLETE -> messages.get(locale, PREFIX + "completion_complete", title(reason.itemId(), context));
            case COMPLETION_INCOMPLETE -> messages.get(locale, PREFIX + "completion_incomplete", title(reason.itemId(), context));
            case GRADE_MIN -> messages.get(locale, PREFIX + "grade_min", title(reason.itemId(), context),
                    percent(reason.min(), locale), current(reason.current(), locale));
            case GRADE_MAX -> messages.get(locale, PREFIX + "grade_max", title(reason.itemId(), context),
                    percent(reason.max(), locale), current(reason.current(), locale));
            case GRADE_RANGE -> messages.get(locale, PREFIX + "grade_range", title(reason.itemId(), context),
                    percent(reason.min(), locale), percent(reason.max(), locale), current(reason.current(), locale));
            case GROUP -> messages.get(locale, PREFIX + "group");
            case DATE_UNTIL -> messages.get(locale, PREFIX + "closed", date(reason.at(), context));
        };
    }

    private String title(UUID itemId, Context context) {
        String title = context.itemTitles().get(itemId);
        return title != null ? title : messages.get(context.locale(), PREFIX + "unknown_item");
    }

    private String current(Double current, Locale locale) {
        return current == null ? messages.get(locale, PREFIX + "no_grade") : messages.get(locale, PREFIX + "current", percent(current, locale));
    }

    static String percent(Double value, Locale locale) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(MAX_FRACTION_DIGITS);
        format.setGroupingUsed(false);
        return format.format(value);
    }

    private static String date(Instant at, Context context) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(context.locale()).withZone(context.zone()).format(at);
    }

    private record Context(Map<UUID, String> itemTitles, ZoneId zone, Locale locale) {
    }
}
