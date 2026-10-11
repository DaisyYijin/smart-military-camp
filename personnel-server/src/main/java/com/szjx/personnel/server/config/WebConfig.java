package com.szjx.personnel.server.config;

import com.szjx.personnel.server.auth.AuthInterceptor;
import com.szjx.personnel.server.auth.OpLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置:注册认证拦截器;开启定时调度(自动备份)
 */
@Configuration
@EnableScheduling
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final OpLogInterceptor opLogInterceptor;

    public WebConfig(AuthInterceptor authInterceptor, OpLogInterceptor opLogInterceptor) {
        this.authInterceptor = authInterceptor;
        this.opLogInterceptor = opLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/ping");
        registry.addInterceptor(opLogInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/ping", "/api/log");
    }
}
