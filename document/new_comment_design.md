# 🤯新的评论表实现思路

## 实例-租车网站



### 1. 主题表 (这里是车辆信息)

> 评论主题表为评论区的主题聚合信息，即评论对应的实体。

```sql
-- 车辆信息表
CREATE TABLE `car`
(
    `id`               bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `brand_id`         bigint unsigned NOT NULL COMMENT '品牌ID',
    `name`             varchar(50)     NOT NULL COMMENT '车辆名',
    `number`           varchar(50)     NOT NULL COMMENT '车牌号',
    `min_rental_days`  int             NOT NULL COMMENT '最小租赁天数',
    `daily_rent`       decimal(10, 2)  NOT NULL COMMENT '日租金(人民币元)',

    `car_type`         varchar(50)              DEFAULT NULL COMMENT '车型',
    `power_type`       varchar(50)              DEFAULT NULL COMMENT '动力类型',
    `purchase_time`    date                     DEFAULT NULL COMMENT '车辆购买日期',
    `horsepower`       int                      DEFAULT NULL COMMENT '马力',
    `torque`           int                      DEFAULT NULL COMMENT '最大扭矩',
    `fuel_consumption` int                      DEFAULT NULL COMMENT '百公里油耗(L/100km)',
    `endurance`        int                      DEFAULT NULL COMMENT '理论续航km',
    `description`      varchar(1536)            DEFAULT NULL COMMENT '描述',
    `size`             varchar(50)              DEFAULT NULL COMMENT '尺寸，长×宽×高',
    `seat`             int                      DEFAULT NULL COMMENT '座位数',
    `weight`           int                      DEFAULT NULL COMMENT '车重(kg)',
    `volume`           int                      DEFAULT NULL COMMENT '储物容积(L)',
    `acceleration`     decimal(4, 1)            DEFAULT NULL COMMENT '百公里加速(s)',
    `images`           varchar(1536)            DEFAULT NULL COMMENT '图片url列表,逗号分隔，最多9张图片',

    `status`           tinyint         NOT NULL DEFAULT '0' COMMENT '车辆状态：0=正常，1=不可租',
    `hot_score`        int             NOT NULL DEFAULT '0' COMMENT '热度评分',
    `avg_score`        int             NOT NULL DEFAULT '0' COMMENT '车辆用户平均评分',

    `create_time`      datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          tinyint         NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常，1=已删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='车辆信息表';


```

---

### 2. 评论索引表

> 评论索引表只构建评论的结构索引，不储存实际的评论细节。

```sql
-- 评论索引表
CREATE TABLE `comment_index`
(
    `id`                bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id`           bigint unsigned NOT NULL COMMENT '对应用户ID',
    `car_id`            bigint unsigned NOT NULL COMMENT '对应车辆ID',
    `parent_comment_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '父级评论id,默认0即为顶级评论',
    `follow_comment_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '回复评论id,默认0即非回复评论',
    `hot_score`         int unsigned NOT NULL DEFAULT '0' COMMENT '热度-目前只用点赞数实现',

    `create_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常，1=已删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='评论索引表';
```

---

### 3. 评论详情表

> 评论详情再储存具体的评论数据，实现了评论结构和评论内容的解耦，这样的话，可以很好的防止在缓存过程中出现大key的问题。

```sql
-- 评论详情表
CREATE TABLE `comment_detail`
(
    `id`                bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `index_id`          bigint unsigned NOT NULL COMMENT '对应索引ID',
    `content`           varchar(1024) NOT NULL COMMENT '评论内容',
    `score`             int unsigned DEFAULT NULL COMMENT '近期订单评分',
    `extra_images`      JSON DEFAULT NULL COMMENT '评论图片URL列表(JSON数组)',
    `create_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常，1=已删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='评论详情表';
```

---

### 4. 外部关联表

引入一个额外的业务背景：如果用户对于某个车辆，有完成且打分的订单，用户评论会额外显示最近一次的订单的打分。

**具体的业务实现如下：**

> 用户发送评论时，查询数据库，如果评论的车辆之前有订单评分分数，把最近的一次订单评分插入到评论详情的score字段中 (create_time desc，limit 1)。即，一个当时评分的快照。

```sql
-- 订单表
CREATE TABLE `rental_order`
(
 `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
 `user_id` bigint unsigned NOT NULL COMMENT '对应用户ID',
 `car_id` bigint unsigned NOT NULL COMMENT '对应车辆ID',
 `trade_no` varchar(255) NOT NULL COMMENT '支付宝订单编号', 
 `start_rental_time` date NOT NULL COMMENT '车辆起租日期',
 `end_rental_time` date NOT NULL COMMENT '车辆还车日期',
 `price` decimal(10, 2) NOT NULL COMMENT '订单总额(人民币元)',
 `address` varchar(255) NOT NULL COMMENT '取还车地址', 
 `status` tinyint NOT NULL DEFAULT '0' COMMENT '订单状态：0=新建/待支付，1=已支付，2=租赁中，3=已完成，4=已取消 5=待退款 6=已退款',
 `score` int DEFAULT NULL COMMENT '订单评分0-10，计入车辆均分', 
 `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
 `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常，1=已删除',
 PRIMARY KEY (`id`)
) ENGINE = InnoDB
 AUTO_INCREMENT = 1
 DEFAULT CHARSET = utf8mb4
 COLLATE = utf8mb4_0900_ai_ci COMMENT ='订单信息表';
```


