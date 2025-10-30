package com.losgai.sys.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

// PageParam.java - 分页请求参数
@EqualsAndHashCode(callSuper = true)
@Data
public class CarSearchPageParam extends CarSearchParam {
    
    /**
     * 每页大小，默认12
     */
    private Integer pageSize = 12;

    /**
     * search_after参数，用于深度分页
     * 存储为String列表，在构建查询时转换为FieldValue
     */
    private List<String> searchAfter;
}