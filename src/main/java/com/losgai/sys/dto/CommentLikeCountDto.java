package com.losgai.sys.dto;

import lombok.Data;

@Data
public class CommentLikeCountDto {
    private Long commentId;
    private Integer count;
}