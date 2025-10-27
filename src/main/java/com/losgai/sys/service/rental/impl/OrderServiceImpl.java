package com.losgai.sys.service.rental.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.losgai.sys.config.RabbitMQMessageConfig;
import com.losgai.sys.dto.*;
import com.losgai.sys.entity.carRental.Car;
import com.losgai.sys.entity.carRental.RentalOrder;
import com.losgai.sys.entity.sys.User;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.mapper.CarMapper;
import com.losgai.sys.mapper.RentalOrderMapper;
import com.losgai.sys.mq.sender.Sender;
import com.losgai.sys.service.rental.AlipayService;
import com.losgai.sys.service.rental.CalculationService;
import com.losgai.sys.service.rental.OrderService;
import com.losgai.sys.service.sys.UserService;
import com.losgai.sys.util.OrderUtils;
import com.losgai.sys.vo.OrderVo;
import com.losgai.sys.vo.ShowOrderVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Description;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final RentalOrderMapper rentalOrderMapper;

    private final CarMapper carMapper;

    private final CalculationService calculationService;

    private final UserService userService;

    private final AlipayService alipayService;

    private final RedissonClient redissonClient;

    private final Sender sender;

    public static final String LOCK_KEY = "lock:car:";
    public static final String DATE_CACHE_PREFIX = "carBookingsDateCache::";

    @Override
    @Description("获取准备下单信息，包括封装可租时间段")
    public OrderVo getStartOrderVo(Long carId) {
        OrderVo orderVo = new OrderVo();
        Car car = carMapper.selectByPrimaryKey(carId);
        // 不可租或删除的情况
        if (car == null || car.getStatus() == 1) {
            return null;
        }
        orderVo.setCar(car);
        // 一个treeMap，用于存储车辆可租时间段集合，key为开始时间，value为结束时间
        TreeMap<Date, Date> carBookingsAsTreeMap = OrderUtils.
                isBookingTimeAvailable(calculationService.getCarBookingsAsTreeMap(carId));
        orderVo.setRentTime(carBookingsAsTreeMap);
        return orderVo;
    }

    @Override
    @Description("生成订单,前端提交租车起始日期、车辆id")
    public Long create(BookingDto bookingDto) {
        User userInfo = userService.getUserInfo();
        // 检查用户信息
        if (userInfo == null || userInfo.getBirthdate() == null) {
            return -1L;
        }
        // 计算年龄，需>=18岁
        LocalDate localDate = userInfo.getBirthdate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate now = LocalDate.now();
        if (localDate.until(now, ChronoUnit.YEARS) < 18) {
            return -1L;
        }
        // 缺少驾驶证或身份证将无法租车
        if(StrUtil.isBlank(userInfo.getIdNumber()) || StrUtil.isBlank(userInfo.getLicenseNumber())){
            return -1L;
        }

        RentalOrder order = new RentalOrder();

        // 基本信息
        order.setUserId(StpUtil.getLoginIdAsLong());
        order.setCarId(bookingDto.getCarId());
        order.setStatus(0);
        order.setDeleted(0);
        order.setAddress(bookingDto.getAddress());

        // 计算订单租车日期
        Date startRentalTime = bookingDto.getStartRentalTime();
        Date endRentalTime = bookingDto.getEndRentalTime();
        if (startRentalTime.after(endRentalTime)) {
            return -1L;
        }

        // 计算租赁天数
        // 使用 LocalDate，忽略时分秒，按天计算
        LocalDate startDate = startRentalTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate endDate = endRentalTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        long days = ChronoUnit.DAYS.between(startDate, endDate);
        if (days < 1) { // 至少租一天
            days = 1;
        } else if (days > 61) { // 最多租61天(包括今天)
            return -1L;
        }

        // 获取车辆信息
        Car car = carMapper.selectByPrimaryKey(bookingDto.getCarId());
        // 不可租或删除的情况
        if (car == null || car.getStatus() == 1) {
            return -1L;
        }

        // 检查日期冲突
        if (isCarCanRent(bookingDto.getCarId(), startRentalTime, endRentalTime)) {
            return -1L;
        }

        // 检查是否小于最小租赁天数
        if (days + 1 < car.getMinRentalDays()) {
            return -1L;
        }

        // 赋值租赁起止日期
        order.setStartRentalTime(startRentalTime);
        order.setEndRentalTime(endRentalTime);

        // 计算订单价格
        order.setPrice(car.getDailyRent().multiply(new BigDecimal(days)));

        // 创建和更新时间
        order.setCreateTime(Date.from(Instant.now()));
        order.setUpdateTime(Date.from(Instant.now()));

        // 插入数据库
        rentalOrderMapper.insert(order);
        // 发送到消息队列延迟更新订单状态
        sender.sendOrder(RabbitMQMessageConfig.EXCHANGE_NAME,
                RabbitMQMessageConfig.ROUTING_KEY_ORDER_DELAY,
                order);
        return order.getId();

    }

    @Override
    @Description("支付订单")
    @Transactional
    public String pay(Long orderId) {
        RentalOrder rentalOrder = rentalOrderMapper.selectByPrimaryKey(orderId);
        Long carId = rentalOrder.getCarId();
        // 分布式锁解决超卖问题
        String lockKey = LOCK_KEY + carId;
        RLock carLock = redissonClient.getLock(lockKey);

        boolean isLocked = false;

        try {
            // 1. 尝试获取锁
            isLocked = carLock.tryLock(10, 60, TimeUnit.SECONDS);

            if (isLocked) {
                // 2. 获取锁成功，执行核心业务逻辑
                log.info("线程 {} 获取车辆 {} 的锁成功", Thread.currentThread().threadId(), carId);
                /**
                 * 1.检查订单状态 如果为0才进行下一步
                 * 2.再次检查车辆状态，包括是否被删除或日期重合
                 * 3.请求支付，生成支付表单
                 * 4.通过支付宝的回调状态来更新订单
                 * */
                rentalOrder = rentalOrderMapper.selectByPrimaryKey(orderId);
                if (rentalOrder.getStatus() == 0) {
                    // 获取车辆信息
                    Car car = carMapper.selectByPrimaryKey(carId);
                    // 不可租或删除的情况
                    if (car == null || car.getStatus() == 1) {
                        return ResultCodeEnum.NO_SUCH_CAR.getMessage();
                    }
                    // 检查车辆日期冲突
                    if (!OrderDateRangeCheck(rentalOrder)) {
                        return ResultCodeEnum.DATE_ERROR.getMessage();
                    }

                    // 支付逻辑
                    // 调用支付服务创建支付订单
                    String subject = "租车订单-" + carId + " "
                            + rentalOrder.getStartRentalTime()
                            + "~" + rentalOrder.getEndRentalTime();

                    String paymentForm = alipayService.createPayment(
                            orderId,
                            rentalOrder.getPrice(),
                            subject
                    );

                    if (paymentForm != null) {
                        // 支付订单创建成功
                        // 待支付宝回调成功后再更新数据库和缓存
                        log.info("订单 {} 支付表单创建成功", orderId);
                        // 需要返回一个包含支付表单的对象
                        return paymentForm;
                    } else {
                        // 支付订单创建失败
                        log.error("订单 {} 支付表单创建失败", orderId);
                        return ResultCodeEnum.PAYMENT_FAILED.getMessage();
                    }
                }

            } else {
                // 3. 获取锁失败，说明有其他请求正在处理此车辆的订单
                log.warn("线程 {} 获取车辆 {} 的锁失败，系统繁忙", Thread.currentThread().threadId(), carId);
                return ResultCodeEnum.SYSTEM_BUSY.getMessage();
            }
        } catch (InterruptedException e) {
            log.error("获取分布式锁时被中断", e);
            Thread.currentThread().interrupt();
            return ResultCodeEnum.SYSTEM_ERROR.getMessage();
        } finally {
            // 4. 无论如何，最后都要释放锁
            if (isLocked && carLock.isHeldByCurrentThread()) {
                carLock.unlock();
                log.info("线程 {} 释放车辆 {} 的锁", Thread.currentThread().threadId(), carId);
            }
        }
        return ResultCodeEnum.SYSTEM_ERROR.getMessage();
    }

    @Description("取消订单，需要加分布式锁")
    @Transactional
    public ResultCodeEnum cancel(Long orderId) {
        RentalOrder rentalOrder = rentalOrderMapper.selectByPrimaryKey(orderId);

        if (rentalOrder.getStatus() != 0 && rentalOrder.getStatus() != 1) {
            return ResultCodeEnum.ORDER_STATUS_ERROR;
        }

        Long carId = rentalOrder.getCarId();
        // 分布式锁解决数据一致性问题
        String lockKey = LOCK_KEY + carId;
        RLock carLock = redissonClient.getLock(lockKey);

        boolean isLocked = false;

        try {
            // 1. 尝试获取锁
            isLocked = carLock.tryLock(10, 60, TimeUnit.SECONDS);

            if (isLocked) {
                // 2. 获取锁成功，执行核心业务逻辑
                log.info("线程 {} 获取车辆 {} 的锁成功", Thread.currentThread().threadId(), carId);
                // 已支付的情况，需要退款
                if (rentalOrder.getStatus() == 1) {
                    RefundDto refundDto = new RefundDto();
                    refundDto.setOrderId(rentalOrder.getId());
                    refundDto.setRefundReason("用户取消订单");
                    // 发送退款消息
                    sender.sendOrderRefund(
                            RabbitMQMessageConfig.EXCHANGE_NAME,
                            RabbitMQMessageConfig.ROUTING_KEY_REFUND,
                            refundDto
                    );
                } else {
                    // 未支付的情况，不需要退款
                    rentalOrderMapper.updateStatus(orderId, 4);
                }
            } else {
                // 3. 获取锁失败，说明有其他请求正在处理此车辆的订单
                log.warn("线程 {} 获取车辆 {} 的锁失败，系统繁忙", Thread.currentThread().threadId(), carId);
                return ResultCodeEnum.SYSTEM_BUSY;
            }
        } catch (InterruptedException e) {
            log.error("获取分布式锁时被中断", e);
            Thread.currentThread().interrupt();
            return ResultCodeEnum.SYSTEM_ERROR;
        } finally {
            // 4. 无论如何，最后都要释放锁
            if (isLocked && carLock.isHeldByCurrentThread()) {
                carLock.unlock();
                log.info("线程 {} 释放车辆 {} 的锁", Thread.currentThread().threadId(), carId);
            }
        }
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    @Description("用户查询自己的订单")
    public List<ShowOrderVo> userQuery(String keyWord, Date start, Date end, Integer status) {
        return rentalOrderMapper.userQuery(keyWord, start, end, status, StpUtil.getLoginIdAsLong());
    }

    @Override
    @Description("用户给订单打分")
    public ResultCodeEnum ranking(Long orderId, Integer score) {
        if (score < 0 || score > 10) {
            return ResultCodeEnum.DATA_ERROR;
        }
        RentalOrder rentalOrder = rentalOrderMapper.selectByPrimaryKey(orderId);
        if (rentalOrder == null) {
            return ResultCodeEnum.DATA_ERROR;
        }
        if (rentalOrder.getScore() != null) {
            return ResultCodeEnum.DUPLICATED;
        }
        // 状态3是已完成
        if (rentalOrder.getStatus() != 3) {
            return ResultCodeEnum.ORDER_STATUS_ERROR;
        }
        if (rentalOrder.getUserId().equals(StpUtil.getLoginIdAsLong())) {
            rentalOrderMapper.updateScore(orderId, score);
        }
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    public void exportOrdersToExcel(OutputStream outputStream) throws IOException {
        // 1. 查询所有订单（未删除的）
        List<RentalOrder> orders = rentalOrderMapper.selectAllOrders();

        // 2. 转换为导出DTO
        List<OrderDetailExportDTO> detailList = convertToDetailDTO(orders);

        // 3. 计算统计信息
        List<OrderStatisticsExportDTO> statisticsList = calculateStatistics(orders);

        // 4. 使用 EasyExcel 写入多个 Sheet
        ExcelWriter excelWriter = null;
        try {
            excelWriter = EasyExcel.write(outputStream)
                    .registerWriteHandler(createCellStyleStrategy())
                    .build();

            // Sheet1: 订单明细
            WriteSheet detailSheet = EasyExcel.writerSheet(0, "订单明细")
                    .head(OrderDetailExportDTO.class)
                    .build();
            excelWriter.write(detailList, detailSheet);

            // Sheet2: 统计信息
            WriteSheet statisticsSheet = EasyExcel.writerSheet(1, "统计信息")
                    .head(OrderStatisticsExportDTO.class)
                    .build();
            excelWriter.write(statisticsList, statisticsSheet);

        } finally {
            if (excelWriter != null) {
                excelWriter.finish();
            }
        }
    }


    @Override
    @Description("管理员查询所有订单")
    public List<ShowOrderVo> query(String keyWord, Date startDate, Date endDate, Integer status) {
        return rentalOrderMapper.query(keyWord, startDate, endDate, status);
    }

    @Description("检查车辆时间段是否可租")
    private boolean isCarCanRent(Long carId, Date startRentalTime, Date endRentalTime) {
        // 查看不可租日期
        TreeMap<Date, Date> carBookingsAsTreeMap = OrderUtils.
                isBookingTimeAvailable(calculationService.getCarBookingsAsTreeMap(carId));
        // 检查日期冲突
        for (Date start : carBookingsAsTreeMap.keySet()) {
            Date end = carBookingsAsTreeMap.get(start);
            // 只要新订单的开始时间在已预订结束时间之前，
            // 并且新订单的结束时间在已预订开始时间之后，就意味着有重叠。
            if (startRentalTime.before(end) && endRentalTime.after(start)) {
                log.warn("时间冲突：预定时间 {} - {} 与已有预定 {} - {} 冲突", startRentalTime, endRentalTime, start, end);
                return true;
            }
        }
        return false;
    }

    /**
     * 转换订单实体为导出DTO
     */
    private List<OrderDetailExportDTO> convertToDetailDTO(List<RentalOrder> orders) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        List<OrderDetailExportDTO> result = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            RentalOrder order = orders.get(i);
            OrderDetailExportDTO dto = new OrderDetailExportDTO();

            dto.setSerialNumber(i + 1);
            dto.setOrderId(order.getId());
            dto.setUserId(order.getUserId());
            dto.setCarId(order.getCarId());
            dto.setStartRentalTime(dateFormat.format(order.getStartRentalTime()));
            dto.setEndRentalTime(dateFormat.format(order.getEndRentalTime()));
            dto.setAddress(order.getAddress());
            dto.setPrice(order.getPrice());
            dto.setStatus(convertStatus(order.getStatus()));
            dto.setScore(order.getScore() != null ? order.getScore().toString() : "-");
            dto.setCreateTime(dateTimeFormat.format(order.getCreateTime()));
            dto.setUpdateTime(dateTimeFormat.format(order.getUpdateTime()));

            result.add(dto);
        }

        return result;
    }

    /**
     * 状态转义
     */
    private String convertStatus(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "已支付";
            case 2 -> "租赁中";
            case 3 -> "已完成";
            case 4 -> "已取消";
            case 5 -> "待退款";
            case 6 -> "已退款";
            default -> "未知";
        };
    }

    /**
     * 计算统计信息
     */
    private List<OrderStatisticsExportDTO> calculateStatistics(List<RentalOrder> orders) {
        List<OrderStatisticsExportDTO> result = new ArrayList<>();

        // 订单总数
        int totalCount = orders.size();
        result.add(new OrderStatisticsExportDTO("订单总数", String.valueOf(totalCount)));

        // 各状态订单数
        Map<Integer, Long> statusCountMap = orders.stream()
                .collect(Collectors.groupingBy(RentalOrder::getStatus, Collectors.counting()));

        result.add(new OrderStatisticsExportDTO("已支付订单数",
                String.valueOf(statusCountMap.getOrDefault(1, 0L))));
        result.add(new OrderStatisticsExportDTO("租赁中订单数",
                String.valueOf(statusCountMap.getOrDefault(2, 0L))));
        result.add(new OrderStatisticsExportDTO("已完成订单数",
                String.valueOf(statusCountMap.getOrDefault(3, 0L))));
        result.add(new OrderStatisticsExportDTO("已取消订单数",
                String.valueOf(statusCountMap.getOrDefault(4, 0L))));

        // 总金额
        BigDecimal totalAmount = orders.stream()
                .map(RentalOrder::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        result.add(new OrderStatisticsExportDTO("总金额",
                "¥" + String.format("%,.2f", totalAmount)));

        // 完成率
        long completedCount = statusCountMap.getOrDefault(3, 0L);
        double completionRate = totalCount > 0 ? (completedCount * 100.0 / totalCount) : 0;
        result.add(new OrderStatisticsExportDTO("完成率",
                String.format("%.1f%%", completionRate)));

        // 取消率
        long cancelledCount = statusCountMap.getOrDefault(4, 0L);
        double cancellationRate = totalCount > 0 ? (cancelledCount * 100.0 / totalCount) : 0;
        result.add(new OrderStatisticsExportDTO("取消率",
                String.format("%.1f%%", cancellationRate)));

        // 平均评分
        double avgScore = orders.stream()
                .filter(o -> o.getScore() != null)
                .mapToInt(RentalOrder::getScore)
                .average()
                .orElse(0.0);
        result.add(new OrderStatisticsExportDTO("平均评分",
                String.format("%.1f", avgScore)));

        return result;
    }

    /**
     * 创建单元格样式策略
     */
    private HorizontalCellStyleStrategy createCellStyleStrategy() {
        // 头部样式
        WriteCellStyle headWriteCellStyle = new WriteCellStyle();
        headWriteCellStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);

        // 内容样式
        WriteCellStyle contentWriteCellStyle = new WriteCellStyle();
        contentWriteCellStyle.setHorizontalAlignment(HorizontalAlignment.LEFT);

        return new HorizontalCellStyleStrategy(headWriteCellStyle, contentWriteCellStyle);
    }

    /**
     * 检查车辆时间段是否可租
     *
     * @param rentalOrder
     * @return true/false 是否合法
     */
    private boolean OrderDateRangeCheck(RentalOrder rentalOrder) {
        // 检查车辆日期冲突
        TreeMap<Date, Date> carBookingsAsTreeMap = OrderUtils.
                isBookingTimeAvailable(calculationService.getCarBookingsAsTreeMap(rentalOrder.getCarId()));
        // 如果当前订单的起止时间与缓存中的时间段有冲突，则返回错误码
        Date rentalStartTime = rentalOrder.getStartRentalTime();
        Date rentalEndTime = rentalOrder.getEndRentalTime();

        // 找到小于等于当前起始时间的最近一段
        Map.Entry<Date, Date> floorEntry = carBookingsAsTreeMap.floorEntry(rentalStartTime);
        // 找到大于等于当前起始时间的时间段
        Map.Entry<Date, Date> ceilingEntry = carBookingsAsTreeMap.ceilingEntry(rentalStartTime);

        // 检查与 floorEntry 冲突
        if (floorEntry != null) {
            Date existEnd = floorEntry.getValue();
            if (rentalStartTime.before(existEnd)) {  // 当前订单开始时间 < 已有区间结束时间
                return false;
            }
        }

        // 检查与 ceilingEntry 冲突
        if (ceilingEntry != null) {
            Date existStart = ceilingEntry.getKey();
            // 当前订单结束时间 > 下个区间开始时间
            return !rentalEndTime.after(existStart);
        }

        return true;
    }

    /**
     * 定时任务:每天凌晨2点更新：
     * 1.如果现在的日期离租赁开始日期<=1天，把状态为1=已付款的订单修改为2=租赁中
     * 2.如果现在的日期离租赁结束日期<=1天，把状态为2=租赁中的订单修改为3=已完成
     * 3.统计每个订单的评分，并更新车辆评分
     */
    @Transactional
    @Scheduled(cron = "0 45 2 * * ?")
    public void upDateOrderStatus() {
        List<RentalOrder> orders = rentalOrderMapper.selectOrdersByStatus(1L, 2L);
        List<Long> toRenting = new ArrayList<>();
        List<Long> toComplete = new ArrayList<>();

        for (RentalOrder rentalOrder : orders) {
            Date startRentalTime = rentalOrder.getStartRentalTime();
            Date endRentalTime = rentalOrder.getEndRentalTime();
            // 先处理需要完成的订单
            if (OrderUtils.isFinished(endRentalTime)) {
                toComplete.add(rentalOrder.getId());
            } else if (OrderUtils.isRenting(startRentalTime)) {
                // 处理正在租赁的订单
                toRenting.add(rentalOrder.getId());
            }
        }

        if(CollUtil.isNotEmpty(toRenting))
            rentalOrderMapper.updateStatusBatch(toRenting, 2);
        if(CollUtil.isNotEmpty(toComplete))
            rentalOrderMapper.updateStatusBatch(toComplete, 3);
        log.info("更新订单状态:正在租赁的订单数={},已完成的订单数={}", toRenting.size(), toComplete.size());

        // 更新车辆评分
        carMapper.calculateCarAvgScoreBatch();
        log.info("更新车辆评分完成");
    }
}
