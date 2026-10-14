package com.campus.lostfound.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 把本地上传目录挂到 /uploads/**，供前端读取启事图片。
 * 访问放行由 SecurityConfig 负责（GET /uploads/** 已 permitAll）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String uploadLocation;

    public WebMvcConfig(@Value("${app.upload.dir}") String uploadDir) {
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        String uri = dir.toUri().toString();
        this.uploadLocation = uri.endsWith("/") ? uri : uri + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**").addResourceLocations(uploadLocation);
    }
}
