package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.access.AccessService;
import com.tutorcraft.core.access.domain.AccessContext;
import com.tutorcraft.core.access.domain.Permission;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.files.FilesApi;
import com.tutorcraft.core.files.FilesApi.LinkedFileSize;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Место, которое занимают материалы курсов школы (обложка, описание, файлы и видео элементов) — для главного
 * администратора платформы (право platform.manage). Файл считается в курсе один раз, даже если он упомянут в нескольких
 * элементах; файл, общий для нескольких курсов (после копирования), считается в каждом. Курсы и элементы в корзине
 * учитываются — их файлы занимают место до очистки корзины. Загрузки студентов сюда не входят.
 */
@Service
public class CourseStorageService {

    private final FilesApi files;
    private final ItemRepository items;
    private final CourseRepository courses;
    private final AccessService access;

    CourseStorageService(FilesApi files, ItemRepository items, CourseRepository courses, AccessService access) {
        this.files = files;
        this.items = items;
        this.courses = courses;
        this.access = access;
    }

    /** Курсы школы с файлами, по убыванию занятого места. */
    @Transactional(readOnly = true)
    public List<CourseStorageView> byCourse(UUID tenantId) {
        access.require(Permission.PLATFORM_MANAGE, AccessContext.tenant());
        Map<UUID, Map<UUID, LinkedFileSize>> filesByCourse = new HashMap<>();
        files.linkedFileSizes(tenantId, CourseContentFiles.OWNER_COURSE)
                .forEach(link -> add(filesByCourse, link.ownerId(), link));
        List<LinkedFileSize> itemLinks = files.linkedFileSizes(tenantId, CourseContentFiles.OWNER_ITEM);
        Map<UUID, UUID> courseOfItem = items.courseIdsIncludingDeleted(tenantId,
                itemLinks.stream().map(LinkedFileSize::ownerId).distinct().toList());
        itemLinks.forEach(link -> Optional.ofNullable(courseOfItem.get(link.ownerId()))
                .ifPresent(courseId -> add(filesByCourse, courseId, link)));

        Map<UUID, Course> live = courses.findAll(tenantId, filesByCourse.keySet());
        List<CourseStorageView> result = new ArrayList<>();
        filesByCourse.forEach((courseId, courseFiles) -> Optional.ofNullable(live.get(courseId))
                .or(() -> courses.findIncludingDeleted(tenantId, courseId))
                .ifPresent(course -> result.add(toView(course, courseFiles.values()))));
        result.sort(Comparator.comparingLong(CourseStorageView::bytes).reversed());
        return result;
    }

    private static void add(Map<UUID, Map<UUID, LinkedFileSize>> filesByCourse, UUID courseId, LinkedFileSize link) {
        filesByCourse.computeIfAbsent(courseId, id -> new HashMap<>()).putIfAbsent(link.fileId(), link);
    }

    private static CourseStorageView toView(Course course, Collection<LinkedFileSize> courseFiles) {
        long bytes = courseFiles.stream().mapToLong(LinkedFileSize::sizeBytes).sum();
        long hls = courseFiles.stream().mapToLong(LinkedFileSize::hlsBytes).sum();
        return new CourseStorageView(course.id(), course.title(), course.deletedAt() != null, bytes, hls,
                courseFiles.size());
    }

    public record CourseStorageView(UUID courseId, String title, boolean inTrash, long bytes, long hlsBytes,
                                    long filesCount) {
    }
}
