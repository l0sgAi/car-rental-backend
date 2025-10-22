package com.losgai.sys.service.rental;

import com.losgai.sys.dto.BookingSlot;

import java.util.List;

public interface CalculationService {
    List<BookingSlot> getCarBookingsAsTreeMap(Long carId);
}
