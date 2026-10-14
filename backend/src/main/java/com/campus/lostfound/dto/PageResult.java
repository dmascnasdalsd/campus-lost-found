package com.campus.lostfound.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 分页响应结构：{ items, page, size, total }。
 */
public record PageResult<T>(List<T> items, int page, int size, long total) {

    public static <T> PageResult<T> of(Page<T> source) {
        return new PageResult<>(source.getContent(), source.getNumber(), source.getSize(), source.getTotalElements());
    }
}
