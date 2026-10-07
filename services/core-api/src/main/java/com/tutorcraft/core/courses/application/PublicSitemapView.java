package com.tutorcraft.core.courses.application;

import java.time.Instant;
import java.util.List;

/** PublicSitemapSchool (контракт §13): витрина школы и её опубликованные курсы для sitemap.xml. */
public record PublicSitemapView(String tenantSlug, Instant lastModified, List<Course> courses) {

    public record Course(String slug, Instant lastModified) {
    }
}
