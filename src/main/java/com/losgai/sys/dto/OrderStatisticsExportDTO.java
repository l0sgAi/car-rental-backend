package com.losgai.sys.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatisticsExportDTO {
    
    @ExcelProperty(value = "统计项", index = 0)
    @ColumnWidth(20)
    private String statisticsItem;
    
    @ExcelProperty(value = "数值", index = 1)
    @ColumnWidth(20)
    private String value;
}