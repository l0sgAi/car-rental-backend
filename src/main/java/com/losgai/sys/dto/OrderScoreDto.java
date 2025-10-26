package com.losgai.sys.dto;

import lombok.Data;

@Data
public class OrderScoreDto {
    private Long carId;
    private Integer score;

    public OrderScoreDto(Long key, Double value) {
        this.carId = key;
        this.score = value.intValue();
    }
}
