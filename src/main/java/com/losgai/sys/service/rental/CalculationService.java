package com.losgai.sys.service.rental;

import java.util.Date;
import java.util.TreeMap;

public interface CalculationService {
    TreeMap<Date, Date> getCarBookingsAsTreeMap(Long carId);
}
