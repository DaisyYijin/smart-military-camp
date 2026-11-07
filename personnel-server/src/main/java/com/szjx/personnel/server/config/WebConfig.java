package com.szjx.personnel.server.config;

import com.szjx.personnel.server.auth.AuthInterceptor;
import com.szjx.personnel.server.auth.OpLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Web 配置:注册认证拦截器;开启定时调度(自动备份);安全响应头
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
        registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader("X-Frame-Options", "DENY");
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("Referrer-Policy", "same-origin");
                response.setHeader("Content-Security-Policy",
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self'");
                return true;
            }
        }).addPathPatterns("/*");
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login", "/api/ping", "/api/setting/public");
        registry.addInterceptor(opLogInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/ping", "/api/log");
    }
}
