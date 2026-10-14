package com.campus.lostfound.dto;

import com.campus.lostfound.entity.ReportImage;

/**
 * 详情中返回的图片元数据：库中只存相对访问路径与原始信息。
 */
public record ReportImageInfo(
        Long id,
        String path,
        String originalName,
        String mime,
        long sizeBytes,
        int sortOrder
) {

    public static ReportImageInfo from(ReportImage image) {
        return new ReportImageInfo(
                image.getId(),
                image.getPath(),
                image.getOriginalName(),
                image.getMime(),
                image.getSizeBytes(),
                image.getSortOrder()
        );
    }
}
