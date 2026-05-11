package com.mythosforge.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InternalApiWebConfig implements WebMvcConfigurer {

    private final InternalApiTokenInterceptor internalApiTokenInterceptor;

    public InternalApiWebConfig(InternalApiTokenInterceptor internalApiTokenInterceptor) {
        this.internalApiTokenInterceptor = internalApiTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalApiTokenInterceptor).addPathPatterns("/api/internal/**");
    }
}
