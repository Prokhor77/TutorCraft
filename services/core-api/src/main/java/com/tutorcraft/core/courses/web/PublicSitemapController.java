package com.tutorcraft.core.courses.web;

import com.tutorcraft.core.courses.application.PublicSitemapService;
import com.tutorcraft.core.courses.application.PublicSitemapView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Индекс публичной витрины для sitemap.xml веб-клиента (SEO), без авторизации (/api/v1/public/** в SecurityConfig). */
@RestController
class PublicSitemapController {

    private final PublicSitemapService sitemap;

    PublicSitemapController(PublicSitemapService sitemap) {
        this.sitemap = sitemap;
    }

    @GetMapping("/api/v1/public/sitemap")
    List<PublicSitemapView> sitemap() {
        return sitemap.sitemap();
    }
}
