package com.tutorcraft.core.shared.web;

import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
class WebConfig implements WebMvcConfigurer {

    /** По одному файлу сообщений на модуль, чтобы модули не правили общий файл (NFR-I18N-01). */
    private static final String[] MESSAGE_BUNDLES = {
        "i18n/shared", "i18n/identity", "i18n/org", "i18n/access", "i18n/audit", "i18n/files", "i18n/courses",
        "i18n/enrollment", "i18n/assessment", "i18n/gradebook", "i18n/progress", "i18n/communication",
        "i18n/dashboard", "i18n/billing", "i18n/integrations", "i18n/activity"
    };
    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("ru");
    private static final List<Locale> SUPPORTED_LOCALES = List.of(DEFAULT_LOCALE, Locale.ENGLISH, Locale.forLanguageTag("uz"));

    private final RequestContextInterceptor requestContextInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    WebConfig(RequestContextInterceptor requestContextInterceptor, RateLimitInterceptor rateLimitInterceptor) {
        this.requestContextInterceptor = requestContextInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestContextInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
    }

    @Bean
    MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames(MESSAGE_BUNDLES);
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    @Bean
    LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(DEFAULT_LOCALE);
        resolver.setSupportedLocales(SUPPORTED_LOCALES);
        return resolver;
    }
}
