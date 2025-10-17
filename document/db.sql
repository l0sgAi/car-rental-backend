-- 用户表
CREATE TABLE `user`
(
    `id`             bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `username`       varchar(50)     NOT NULL COMMENT '用户名',
    `password`       varchar(512)    NOT NULL COMMENT '加密后的密码',

    `id_number`      varchar(50)              DEFAULT NULL COMMENT '身份证号',
    `license_number` varchar(50)              DEFAULT NULL COMMENT '驾驶证编号',
    `license_date`   date                     DEFAULT NULL COMMENT '初次领驾驶证日期',
    `phone`          varchar(20)              DEFAULT NULL COMMENT '手机号',
    `real_name`      varchar(50)              DEFAULT NULL COMMENT '实名姓名',
    `avatar_url`     varchar(255)             DEFAULT NULL COMMENT '头像链接',
    `gender`         tinyint                  DEFAULT '0' COMMENT '性别：0=未知，1=男，2=女',
    `birthdate`      date                     DEFAULT NULL COMMENT '出生日期',
    `status`         tinyint         NOT NULL DEFAULT '1' COMMENT '状态：0=禁用，1=启用',
    `role`           bigint unsigned NOT NULL DEFAULT '0' COMMENT '角色id 0用户1管理员',

    `create_time`    datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        tinyint         NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常，1=已删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户信息表';

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

-- 品牌表

CREATE TABLE `brand`
(
    `id`       bigint  NOT NULL AUTO_INCREMENT COMMENT '品牌id',
    `name`     char(50)         DEFAULT NULL COMMENT '品牌名',
    `logo`     varchar(2000)    DEFAULT NULL COMMENT '品牌logo地址',
    `descript` text             DEFAULT NULL COMMENT '介绍',
    `deleted`  tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常，1=已删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='品牌';

-- 订单表
CREATE TABLE `rental_order`
(
    `id`                bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id`           bigint unsigned NOT NULL COMMENT '对应用户ID',
    `car_id`            bigint unsigned NOT NULL COMMENT '对应车辆ID',

    `start_rental_time` date            NOT NULL COMMENT '车辆起租日期',
    `end_rental_time`   date            NOT NULL COMMENT '车辆还车日期',
    `price`             decimal(10, 2)  NOT NULL COMMENT '订单总额(人民币元)',

    `status`            tinyint         NOT NULL DEFAULT '0' COMMENT '订单状态：0=新建/待支付，1=已支付，2=租赁中，3=已完成，4=已取消',
    `score`             int                      DEFAULT NULL COMMENT '订单评分0-10，计入车辆均分',

    `create_time`       datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           tinyint         NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常，1=已删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='订单信息表';

-- 评论表
CREATE TABLE `comment`
(
    `id`                bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id`           bigint unsigned NOT NULL COMMENT '对应用户ID',
    `car_id`            bigint unsigned NOT NULL COMMENT '对应车辆ID',
    `parent_comment_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '父级评论id,默认0即为顶级评论',
    `follow_comment_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '回复评论id,默认0即非回复评论',

    `content`           varchar(1024)   NOT NULL COMMENT '评论内容',
    `like_count`        int unsigned    NOT NULL DEFAULT '0' COMMENT '点赞数',

    `create_time`       datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           tinyint         NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常，1=已删除',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='订单信息表';