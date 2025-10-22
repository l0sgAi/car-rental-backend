package com.losgai.sys.mapper;

import com.losgai.sys.dto.BookingSlot;
import com.losgai.sys.entity.carRental.RentalOrder;
import com.losgai.sys.vo.ShowOrderVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
* @author miesme
* @description 针对表【rental_order(订单信息表)】的数据库操作Mapper
* @createDate 2025-10-14 12:52:33
* @Entity generator.domain.RentalOrder
*/
@Mapper
public interface RentalOrderMapper {

    int deleteByPrimaryKey(Long id);

    int insert(RentalOrder record);

    int insertSelective(RentalOrder record);

    RentalOrder selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(RentalOrder record);

    int updateByPrimaryKey(RentalOrder record);

    /**
     * 根据车辆ID，查询未来60天内所有有效的、已占用的预订时间段。
     * “有效”指订单状态为“已支付”或“租赁中”。
     * @param carId 车辆ID
     * @return 预订时间段列表
     */
    List<BookingSlot> findFutureBookingsByCarId(@Param("carId") Long carId);

    List<ShowOrderVo> query(String keyWord,
                            Date startDate,
                            Date endDate,
                            Integer status);

    List<ShowOrderVo> userQuery(String keyWord,
                                Date start,
                                Date end,
                                Integer status,
                                Long userId);

    void cancelOrder(Long id);

    List<RentalOrder> selectAllOrders();

    void updateStatus(Long orderId, Integer status);

    List<RentalOrder> selectOrdersByStatus(Long ...ids);

    void updateStatusBatch(List<Long> ids, Integer status);
}
