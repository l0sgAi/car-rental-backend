package com.losgai.sys.service.rental.impl;

import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.losgai.sys.config.RabbitMQAiMessageConfig;
import com.losgai.sys.dto.CarDocument;
import com.losgai.sys.dto.CarSearchParam;
import com.losgai.sys.entity.carRental.Car;
import com.losgai.sys.enums.ResultCodeEnum;
import com.losgai.sys.global.EsConstants;
import com.losgai.sys.mapper.CarMapper;
import com.losgai.sys.mq.sender.Sender;
import com.losgai.sys.service.rental.CarService;
import com.losgai.sys.util.ElasticsearchIndexUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarServiceImpl implements CarService {

    private final CarMapper carMapper;

    private final ElasticsearchClient esClient;

    private final Sender sender;

    @Override
    public List<Car> queryByKeyWord(String keyWord) {
        return carMapper.queryByKeyWord(keyWord);
    }

    @Override
    public List<Car> globalQuery(CarSearchParam carSearchParam) {
        try {
            // 构建查询请求
            SearchRequest searchRequest = buildSearchRequest(carSearchParam);

            // 执行查询
            SearchResponse<Car> response = esClient.search(searchRequest, Car.class);

            // 提取结果
            List<Car> carList = new ArrayList<>();
            for (Hit<Car> hit : response.hits().hits()) {
                Car car = hit.source();
                if (car != null) {
                    carList.add(car);
                }
            }

            log.info("ES查询结果数量: {}", carList.size());
            return carList;

        } catch (Exception e) {
            log.error("ES查询失败", e);
            throw new RuntimeException("查询车辆信息失败", e);
        }
    }

    @Override
    public ResultCodeEnum add(Car car) {
        car.setDeleted(0);
        car.setCreateTime(Date.from(Instant.now()));
        car.setUpdateTime(Date.from(Instant.now()));
        carMapper.insert(car);
        if (car.getStatus() == 0) {
            // 通过消息队列，添加到ES中
            sender.sendCar(RabbitMQAiMessageConfig.EXCHANGE_NAME,
                    RabbitMQAiMessageConfig.ROUTING_KEY_CAR,
                    car);
        }
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    public ResultCodeEnum update(Car car) {
        car.setUpdateTime(Date.from(Instant.now()));
        carMapper.updateByPrimaryKeySelective(car);
        if (car.getStatus() == 0) {
            // 通过消息队列，添加到ES中
            sender.sendCar(RabbitMQAiMessageConfig.EXCHANGE_NAME,
                    RabbitMQAiMessageConfig.ROUTING_KEY_CAR_UPDATE,
                    car);
        }
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    @Transactional
    public ResultCodeEnum delete(Long id) {
        carMapper.deleteByPrimaryKey(id);
        carMapper.deleteOrdersByCarId(id);
        carMapper.deleteCommentsByCarId(id);
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    @Description("上架所有可租车辆至ES")
    public ResultCodeEnum up() {
        // 获取所有可租车辆列表
        List<Car> cars = carMapper.getAllCanRentCars();
        // 通过消息队列，添加到ES中
        sender.sendCars(RabbitMQAiMessageConfig.EXCHANGE_NAME,
                RabbitMQAiMessageConfig.ROUTING_KEY_CAR_BATCH,
                cars);
        return ResultCodeEnum.SUCCESS;
    }

    @Override
    @Description("文档批量插入Elasticsearch，返回状态true/false")
    public boolean insertESDocBatch(String indexName, List<Car> cars) throws IOException {
        // 1.判断是否有索引，没有则创建
        ElasticsearchIndexUtils.createCarIndex(esClient);

        // 2.转换为CarDocument并插入
        List<BulkOperation> bulkOperations = cars.stream()
                .map(CarDocument::fromCar)
                .map(carDoc ->
                        BulkOperation.of(builder ->
                                builder.index(i -> i.index(indexName)
                                        .document(carDoc)
                                        .id(String.valueOf(carDoc.getId())))))
                .toList();

        BulkResponse response = esClient.bulk(e -> e.index(indexName).operations(bulkOperations));
        log.info("ES批量插入操作完成，耗时{}毫秒", response.took());

        if (response.errors()) {
            log.error("ES批量操作-有错误发生");
            return false;
        }
        return true;
    }

    @Override
    @Description("文档单条插入Elasticsearch，返回状态true/false")
    public boolean insertESDoc(String indexName, Car car) throws IOException {
        // 1.判断是否有索引，没有则创建
        ElasticsearchIndexUtils.createCarIndex(esClient);

        // 2.转换为CarDocument并插入单条文档
        CarDocument carDoc = CarDocument.fromCar(car);

        IndexRequest<CarDocument> indexRequest = IndexRequest.of(builder -> builder
                .index(indexName)
                .document(carDoc)
                .id(String.valueOf(carDoc.getId()))
        );

        IndexResponse response = esClient.index(indexRequest);
        log.info("插入文档成功，ID: {}, 状态: {}", response.id(), response.result().jsonValue());
        return true;
    }

    @Override
    @Description("异步更新Elasticsearch文档")
    public boolean updateESDoc(String indexName, Car car) throws IOException {
        try {
            CarDocument carDoc = CarDocument.fromCar(car);

            // 使用update API
            UpdateRequest<CarDocument, CarDocument> updateRequest = UpdateRequest.of(u -> u
                    .index(indexName)
                    .id(String.valueOf(car.getId()))
                    .doc(carDoc)  // 更新的文档
                    .docAsUpsert(true)  // 如果文档不存在则插入
            );

            UpdateResponse<CarDocument> response = esClient.update(updateRequest, CarDocument.class);
            log.info("ES文档更新成功，ID: {}, 状态: {}", response.id(), response.result().jsonValue());
            return true;
        } catch (Exception e) {
            log.error("ES文档更新失败, carId: {}", car.getId(), e);
            return false;
        }
    }

    /**
     * 构建ES查询请求
     */
    private SearchRequest buildSearchRequest(CarSearchParam param) {
        return SearchRequest.of(s -> s
                .index(EsConstants.INDEX_NAME_CAR_RENTAL)  // "/car" 索引
                .query(buildQuery(param))                   // 构建查询条件
                .sort(buildSort(param))                     // 构建排序条件
                .size(1000)                           // 默认返回1000条，可根据需求调整
        );
    }

    /**
     * 构建查询条件
     */
    private Query buildQuery(CarSearchParam param) {
        String keyWord = param.getKeyWord();

        // 如果没有关键字，返回match_all查询
        if (StrUtil.isBlank(keyWord)) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        // 使用multi_match进行多字段搜索
        return Query.of(q -> q
                .multiMatch(m -> m
                        .query(keyWord)
                        .fields(
                                "name^3",      // name字段权重最高(boost=3)
                                "number^2",    // 车牌号权重次之(boost=2)
                                "carType",     // 车辆类型
                                "powerType"    // 动力类型
                        )
                        .type(TextQueryType.BestFields)  // 使用最佳字段匹配
                        .operator(Operator.Or)            // 使用OR操作符
                )
        );
    }

    /**
     * 构建排序条件
     */
    private List<SortOptions> buildSort(CarSearchParam param) {
        List<SortOptions> sortList = new ArrayList<>();

        // 按平均评分排序
        if (param.getAvgScore() != null) {
            sortList.add(buildSortOption("avgScore", param.getAvgScore()));
        }

        // 按热度排序
        if (param.getHotScore() != null) {
            sortList.add(buildSortOption("hotScore", param.getHotScore()));
        }

        // 按油耗排序
        if (param.getFuelConsumption() != null) {
            sortList.add(buildSortOption("fuelConsumption", param.getFuelConsumption()));
        }

        // 按日租金排序
        if (param.getDailyRent() != null) {
            sortList.add(buildSortOption("dailyRent", param.getDailyRent()));
        }

        // 按座位数排序
        if (param.getSeat() != null) {
            sortList.add(buildSortOption("seat", param.getSeat()));
        }

        // 如果没有任何排序条件，默认按相关性评分排序
        if (sortList.isEmpty()) {
            sortList.add(SortOptions.of(s -> s
                    .score(sc -> sc.order(SortOrder.Desc))
            ));
        }

        return sortList;
    }

    /**
     * 构建单个排序选项
     *
     * @param fieldName 字段名
     * @param order     0=正序(ASC), 1=倒序(DESC)
     */
    private SortOptions buildSortOption(String fieldName, Integer order) {
        SortOrder sortOrder = (order != null && order == 0) ? SortOrder.Asc : SortOrder.Desc;
        return SortOptions.of(s -> s
                .field(f -> f
                        .field(fieldName)
                        .order(sortOrder)
                )
        );
    }

}
