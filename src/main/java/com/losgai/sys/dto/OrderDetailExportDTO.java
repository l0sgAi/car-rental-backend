package com.losgai.sys.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import com.alibaba.excel.enums.poi.HorizontalAlignmentEnum;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderDetailExportDTO {
    
    @ExcelProperty(value = "序号", index = 0)
    @ColumnWidth(8)
    private Integer serialNumber;
    
    @ExcelProperty(value = "订单ID", index = 1)
    @ColumnWidth(12)
    private Long orderId;
    
    @ExcelProperty(value = "用户ID", index = 2)
    @ColumnWidth(12)
    private Long userId;
    
    @ExcelProperty(value = "车辆ID", index = 3)
    @ColumnWidth(12)
    private Long carId;
    
    @ExcelProperty(value = "起租日期", index = 4)
    @ColumnWidth(15)
    private String startRentalTime;
    
    @ExcelProperty(value = "还车日期", index = 5)
    @ColumnWidth(15)
    private String endRentalTime;
    
    @ExcelProperty(value = "地址", index = 6)
    @ColumnWidth(30)
    private String address;
    
    @ExcelProperty(value = "订单金额(元)", index = 7)
    @NumberFormat("#,##0.00")
    @ColumnWidth(15)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.RIGHT)
    private BigDecimal price;
    
    @ExcelProperty(value = "状态", index = 8)
    @ColumnWidth(12)
    private String status;
    
    @ExcelProperty(value = "评分", index = 9)
    @ColumnWidth(10)
    @ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
    private String score;
    
    @ExcelProperty(value = "创建时间", index = 10)
    @ColumnWidth(20)
    private String createTime;
    
    @ExcelProperty(value = "更新时间", index = 11)
    @ColumnWidth(20)
    private String updateTime;
}