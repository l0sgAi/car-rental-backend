package com.losgai.sys.dto;

import lombok.Data;

@Data
public class ReviewDto {
    private Long indexId;

    private Long detailId;

    private String content;

    public ReviewDto(Long indexId, Long detailId, String content) {
        this.indexId = indexId;
        this.detailId = detailId;
        this.content = content;
    }
}
