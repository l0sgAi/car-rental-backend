package com.losgai.sys.util;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.losgai.sys.global.EsConstants;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class ElasticsearchIndexUtils {

    /**
     * 通用索引创建(不推荐，推荐自定义)
     * */
    @Deprecated
    public static void createIndex(String indexName, ElasticsearchClient esClient) throws IOException {
        // 1.判断是否有索引
        ExistsRequest existsRequest = new ExistsRequest.Builder()
                .index(indexName)
                .build();

        boolean value = esClient.indices().exists(existsRequest).value();
        if (!value) {
            log.info("索引不存在，创建索引:{}", indexName);
            CreateIndexRequest createIndexRequest = CreateIndexRequest.of(builder ->
                    builder.index(indexName)
            );
            esClient.indices().create(createIndexRequest);
        }
    }

    /**
     * Car索引创建
     * */
    public static void createCarIndex(ElasticsearchClient esClient) throws IOException {
        // 1.判断是否有索引
        ExistsRequest existsRequest = new ExistsRequest.Builder()
                .index(EsConstants.INDEX_NAME_CAR_RENTAL)
                .build();

        boolean value = esClient.indices().exists(existsRequest).value();
        if (!value) {
            log.info("索引不存在，创建索引:{}", EsConstants.INDEX_NAME_CAR_RENTAL);
            // 创建索引mapping
            esClient.indices().create(c -> c
                    .index(EsConstants.INDEX_NAME_CAR_RENTAL)
                    .mappings(m -> m
                            .properties("id", p -> p.long_(l -> l))

                            // 搜索属性
                            .properties("name", p -> p.text(t -> t
                                    .analyzer("ik_max_word")           // 索引时使用最细粒度分词
                                    .searchAnalyzer("ik_smart")        // 搜索时使用智能分词
                                    .fields("keyword", f -> f.keyword(k -> k))  // 保留精确匹配字段
                            ))
                            .properties("number", p -> p.keyword(k -> k))  // Exact match for license plate
                            .properties("carType", p -> p.keyword(k -> k))  // Exact match for type
                            .properties("powerType", p -> p.keyword(k -> k))  // Exact match for power type

                            // 排序属性
                            .properties("avgScore", p -> p.integer(i -> i))
                            .properties("hotScore", p -> p.integer(i -> i))
                            .properties("fuelConsumption", p -> p.integer(i -> i))
                            .properties("dailyRent", p -> p.double_(d -> d))  // BigDecimal -> double
                            .properties("seat", p -> p.integer(i -> i))

                            // 显示属性
                            .properties("brandId", p -> p.long_(l -> l.index(false)))
                            .properties("images", p -> p.keyword(k -> k.index(false)))
                            .properties("status", p -> p.integer(i -> i.index(false)))
                    )
            );
        }
    }

}
