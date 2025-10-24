package com.losgai.sys.common.sys;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// PageResult.java - 分页响应结果
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ESPageResult<T> {
    
    /**
     * 数据列表
     */
    private List<T> records;
    
    /**
     * 下一页的search_after值（最后一条记录的sort值）
     * 如果为null表示没有下一页
     */
    private List<String> searchAfter;
    
    /**
     * 是否有下一页
     */
    private Boolean hasNext;
    
    /**
     * 当前页数据量
     */
    private Integer size;
}