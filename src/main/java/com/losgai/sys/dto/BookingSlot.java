package com.losgai.sys.dto;

import lombok.Data;

import java.util.Date;

@Data
public class BookingSlot {
    private Date startRentalTime;
    private Date endRentalTime;
}