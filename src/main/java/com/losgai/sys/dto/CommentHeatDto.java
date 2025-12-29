package com.losgai.sys.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommentHeatDto {
    private Long commentId;
    private Integer delta;
}