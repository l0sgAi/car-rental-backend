package com.losgai.sys.service.rental.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.losgai.sys.config.RabbitMQAiMessageConfig;
import com.losgai.sys.dto.BookingDto;
import com.losgai.sys.dto.RentalOrderDto;
import com.losgai.sys.entity.carRental.Car;
import com.losgai.sys.entity.carRental.RentalOrder;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.mapper.CarMapper;
import com.losgai.sys.mapper.RentalOrderMapper;
import com.losgai.sys.mq.sender.Sender;
import com.losgai.sys.service.rental.CalculationService;
import com.losgai.sys.service.rental.OrderService;
import com.losgai.sys.vo.OrderVo;
import com.losgai.sys.vo.ShowOrderVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Description;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final RentalOrderMapper rentalOrderMapper;

    private final CarMapper carMapper;

    private final CalculationService calculationService;

    private final RedissonClient redissonClient;

    private final RedisTemplate<String, Object> redisTemplate;

    private final Sender sender;

    public static final String LOCK_KEY = "lock:car:";
    private static final String DATE_CACHE_PREFIX = "carBookingsDateCache::";

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
        TreeMap<Date, Date> carBookingsAsTreeMap = calculationService.getCarBookingsAsTreeMap(carId);
        orderVo.setRentTime(carBookingsAsTreeMap);
        return orderVo;
    }

    @Override
    @Description("生成订单,前端提交租车起始日期、车辆id")
    public ResultCodeEnum create(BookingDto bookingDto) {
        RentalOrder order = new RentalOrder();

        // 基本信息
        order.setUserId(StpUtil.getLoginIdAsLong());
        order.setCarId(bookingDto.getCarId());
        order.setStatus(0);
        order.setDeleted(0);

        // 计算订单租车日期
        Date startRentalTime = bookingDto.getStartRentalTime();
        Date endRentalTime = bookingDto.getEndRentalTime();
        if (startRentalTime.after(endRentalTime)) {
            return ResultCodeEnum.DATE_ERROR;
        }

        // 计算租赁天数
        // 使用 LocalDate，忽略时分秒，按天计算
        LocalDate startDate = startRentalTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate endDate = endRentalTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        long days = ChronoUnit.DAYS.between(startDate, endDate);
        if (days < 1) { // 至少租一天
            days = 1;
        } else if (days > 61) { // 最多租61天(包括今天)
            return ResultCodeEnum.DATE_ERROR;
        }

        // 获取车辆信息
        Car car = carMapper.selectByPrimaryKey(bookingDto.getCarId());
        // 不可租或删除的情况
        if (car == null || car.getStatus() == 1) {
            return ResultCodeEnum.NO_SUCH_CAR;
        }

        // 检查日期冲突
        if (isCarCanRent(bookingDto.getCarId(), startRentalTime, endRentalTime)) {
            return ResultCodeEnum.DATE_ERROR;
        }

        // 检查是否小于最小租赁天数
        if (days + 1 < car.getMinRentalDays()) {
            return ResultCodeEnum.LESS_THAN_LIMIT;
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
        sender.sendOrder(RabbitMQAiMessageConfig.EXCHANGE_NAME,
                RabbitMQAiMessageConfig.ROUTING_KEY_ORDER_DELAY,
                order);
        return ResultCodeEnum.SUCCESS;

    }

    @Override
    @Description("支付订单")
    @Transactional
    public ResultCodeEnum pay(Long orderId) {
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
                // TODO: 处理核心业务逻辑
                /**
                 * 1.检查订单状态 如果为0才进行下一步
                 * 2.再次检查车辆状态，包括是否被删除或日期重合
                 * 3.请求支付，通过返回码来判断支付结果
                 * 4.如果成功，更新订单状态，失败返回状态码
                 * */

                /// 支付状态写入前删除被占时间缓存
                redisTemplate.delete(DATE_CACHE_PREFIX + carId);
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

    @Description("更新订单，订单状态为1=已支付或2=租赁中，需要加分布式锁")
    @Transactional
    public ResultCodeEnum update(RentalOrderDto rentalOrderDto) {
        RentalOrder rentalOrder = rentalOrderMapper.selectByPrimaryKey(rentalOrderDto.getOrderId());
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
                // TODO: 处理核心业务逻辑

                /// 更新写入前删除被占时间缓存
                redisTemplate.delete(DATE_CACHE_PREFIX + carId);

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
    @Description("管理员查询所有订单")
    public List<ShowOrderVo> query(String keyWord, Date startDate, Date endDate, Integer status) {
        return rentalOrderMapper.query(keyWord, startDate, endDate, status);
    }

    @Description("检查车辆时间段是否可租")
    private boolean isCarCanRent(Long carId, Date startRentalTime, Date endRentalTime) {
        // 查看不可租日期
        TreeMap<Date, Date> carBookingsAsTreeMap = calculationService.getCarBookingsAsTreeMap(carId);
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
}
