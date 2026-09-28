package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.domain.CourseCompletionRule;
import com.tutorcraft.core.courses.domain.Patch;
import com.tutorcraft.core.courses.domain.SelfEnrolSettings;
import com.tutorcraft.core.shared.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Команды use case курсов, модулей и элементов. Поля {@link Patch} отличают «не передано» от «очистить». */
public final class CourseCommands {

    private CourseCommands() {
    }

    /** POST /courses (UX-02: обязательно только title). */
    public record CreateCourse(String title, String shortName, UUID categoryId, Object description, Instant startsAt,
                               Instant endsAt, UUID coverFileId) {
    }

    /** PATCH /courses/{id}: visibility/publishAt/price требуют course.publish; selfEnrol — enrollment.manage. */
    public record CoursePatch(Patch<String> title, Patch<String> shortName, Patch<UUID> categoryId, Patch<Object> description,
                              Patch<UUID> coverFileId, Patch<Instant> startsAt, Patch<Instant> endsAt,
                              Patch<String> visibility, Patch<Instant> publishAt, Patch<SelfEnrolSettings> selfEnrol,
                              Patch<Money> price, Patch<CourseCompletionRule> completionRule, Patch<String> groupMode) {

        boolean touchesPublication() {
            return visibility.isPresent() || publishAt.isPresent() || price.isPresent();
        }

        /** Имена переданных полей — для аудита (без значений: описание может быть большим). */
        List<String> presentFields() {
            return present(Map.ofEntries(Map.entry("title", title), Map.entry("shortName", shortName),
                    Map.entry("categoryId", categoryId), Map.entry("description", description),
                    Map.entry("coverFileId", coverFileId), Map.entry("startsAt", startsAt), Map.entry("endsAt", endsAt),
                    Map.entry("visibility", visibility), Map.entry("publishAt", publishAt), Map.entry("selfEnrol", selfEnrol),
                    Map.entry("price", price), Map.entry("completionRule", completionRule), Map.entry("groupMode", groupMode)));
        }
    }

    /** PATCH /modules/{id}. */
    public record ModulePatch(Patch<String> title, Patch<String> visibility, Patch<Instant> publishAt,
                              Patch<Map<String, Object>> conditions) {
    }

    /** POST /modules/{id}/items. */
    public record CreateItem(String type, String title, Map<String, Object> settings) {
    }

    /** PATCH /items/{id}. */
    public record ItemPatch(Patch<String> title, Patch<String> visibility, Patch<Instant> publishAt,
                            Patch<Map<String, Object>> settings, Patch<Object> content,
                            Patch<Map<String, Object>> completionRule, Patch<Map<String, Object>> conditions) {

        List<String> presentFields() {
            return present(Map.of("title", title, "visibility", visibility, "publishAt", publishAt, "settings", settings,
                    "content", content, "completionRule", completionRule, "conditions", conditions));
        }
    }

    private static List<String> present(Map<String, Patch<?>> fields) {
        return fields.entrySet().stream().filter(entry -> entry.getValue().isPresent()).map(Map.Entry::getKey).sorted().toList();
    }
}
