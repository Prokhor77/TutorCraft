package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.courses.application.CourseRepository.PublishedCourseRef;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.org.OrgApi.TenantInfo;
import com.tutorcraft.core.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Индекс публичной витрины для sitemap.xml (FR-COURSE-HYB-01, SEO): активные школы, у которых есть опубликованные
 * курсы, и сами курсы. Отдаёт только то, что уже доступно анонимно через {@link PublicCatalogService}.
 */
@Service
public class PublicSitemapService {

    /** Предел одного sitemap-файла — 50 000 URL; оставляем запас под статические страницы и витрины школ. */
    static final int MAX_COURSES = 45_000;

    private final OrgApi org;
    private final CourseRepository courses;
    private final Clock clock;

    public PublicSitemapService(OrgApi org, CourseRepository courses, Clock clock) {
        this.org = org;
        this.courses = courses;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PublicSitemapView> sitemap() {
        Map<UUID, List<PublishedCourseRef>> byTenant = courses.publishedEverywhere(clock.instant(), MAX_COURSES).stream()
                .collect(Collectors.groupingBy(PublishedCourseRef::tenantId, LinkedHashMap::new, Collectors.toList()));
        return byTenant.entrySet().stream()
                .flatMap(entry -> publicTenant(entry.getKey()).map(tenant -> toView(tenant, entry.getValue())).stream())
                .toList();
    }

    private Optional<TenantInfo> publicTenant(UUID tenantId) {
        try {
            return Optional.of(org.require(tenantId))
                    .filter(TenantInfo::active)
                    .filter(tenant -> !OrgApi.PLATFORM_TENANT_SLUG.equals(tenant.slug()));
        } catch (NotFoundException e) {
            return Optional.empty();
        }
    }

    private static PublicSitemapView toView(TenantInfo tenant, List<PublishedCourseRef> refs) {
        List<PublicSitemapView.Course> list = refs.stream()
                .map(ref -> new PublicSitemapView.Course(ref.slug(), ref.updatedAt()))
                .toList();
        Instant lastModified = list.stream().map(PublicSitemapView.Course::lastModified)
                .max(Comparator.naturalOrder()).orElse(null);
        return new PublicSitemapView(tenant.slug(), lastModified, list);
    }
}
