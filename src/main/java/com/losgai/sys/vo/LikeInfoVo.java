package com.losgai.sys.vo;

import lombok.Data;

// 用于承载点赞结果
@Data
public class LikeInfoVo {
    private int count;
    private boolean isLiked;
}