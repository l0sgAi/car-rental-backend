package com.losgai.sys.service.rental;

import com.losgai.sys.dto.RefundDto;

public interface RefundService {

    /**
     * 发起退款
     *
     * @param refundDto 订单ID/退款原因
     */
    void refund(RefundDto refundDto);
}
