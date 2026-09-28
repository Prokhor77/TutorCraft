package com.tutorcraft.core.integrations.infrastructure;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PAT-фильтр работает только внутри цепочки Spring Security (SecurityConfig). Отключаем его автоматическую
 * регистрацию как обычного servlet-фильтра, иначе он выполнится до SecurityContextHolderFilter и будет пропущен
 * внутри цепочки (OncePerRequestFilter).
 */
@Configuration
class PatFilterRegistrationConfig {

    @Bean
    FilterRegistrationBean<PatAuthenticationFilter> patFilterServletRegistration(PatAuthenticationFilter filter) {
        FilterRegistrationBean<PatAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
