// File: flink-jobs/src/main/java/com/bank/clickstream/BankingFraudDetectionJob.java
package com.bank.clickstream;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.naming.Context;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.avro.registry.confluent.ConfluentRegistryAvroDeserializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;

import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.runtime.metrics.DescriptiveStatisticsHistogram;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class BankingFraudDetectionJob {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 1. Load Avro schema
        Schema avroSchema = new Schema.Parser()
                .parse(new File("schemas/banking_ui_event.avsc"));

        // 2. Build KafkaSource
        KafkaSource<GenericRecord> source = KafkaSource.<GenericRecord>builder()
                .setBootstrapServers("kafka:9092")
                .setTopics("banking-ui-events")
                .setGroupId("fraud-consumer")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(
                        ConfluentRegistryAvroDeserializationSchema
                                .forGeneric(avroSchema, "http://schema-registry:8081"))
                .build();

        // 3. Enrich & score per session
        DataStream<Tuple2<String, Double>> flagged = env.fromSource(
                source,
                WatermarkStrategy.forMonotonousTimestamps(),
                "Kafka Avro Source")
                .keyBy(rec -> rec.get("session_id").toString())
                .process(new EnrichmentAndScoringFunction());

        // 4. Sink flagged sessions to Kafka
        KafkaSink<Tuple2<String, Double>> sink = KafkaSink.<Tuple2<String, Double>>builder()
                .setBootstrapServers("kafka:9092")
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<Tuple2<String, Double>>builder()
                                .setTopic("fraud-flags")
                                .setKeySerializationSchema(elem -> elem.f0.getBytes(StandardCharsets.UTF_8))
                                .setValueSerializationSchema(elem -> {
                                    String csv = elem.f0 + "," + elem.f1;
                                    return csv.getBytes(StandardCharsets.UTF_8);
                                })
                                .build())
                .build();

        flagged.sinkTo(sink);
        env.execute("Banking Fraud & Bot-Detection");
    }

    public static class EnrichmentAndScoringFunction
            extends KeyedProcessFunction<String, GenericRecord, Tuple2<String, Double>> {

        private transient JedisPool jedisPool;
        private transient OkHttpClient httpClient;
        private transient ObjectMapper mapper;
        private transient Counter flaggedCounter;
        private transient Histogram latencyHistogram;
        private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
        private String geoIpService;
        private String mlService;

        @Override
        public void open(Configuration cfg) {
            this.jedisPool = new JedisPool(new JedisPoolConfig(), "redis", 6379);
            this.httpClient = new OkHttpClient();
            this.mapper = new ObjectMapper();
            this.geoIpService = "http://geoip-service:8080/lookup";
            this.mlService = "http://ml-model-service:5000/score";

            // Metrics registration
            flaggedCounter = getRuntimeContext()
                    .getMetricGroup()
                    .counter("flagged_sessions_total");
            latencyHistogram = getRuntimeContext()
                    .getMetricGroup()
                    .histogram("pipeline_latency_ms", new DescriptiveStatisticsHistogram(1024));
        }

        @Override
        public void processElement(
                GenericRecord rec,
                Context ctx,
                Collector<Tuple2<String, Double>> out) throws Exception {

            String sessionId = rec.get("session_id").toString();
            int latency = (Integer) rec.get("latency_ms");
            String ip = rec.get("ip_address").toString();
            String userId = rec.get("user_id").toString();
            String deviceFp = rec.get("device_fingerprint").toString();

            // Redis enrichment
            double deviceRisk;
            int failures;
            String accountStatus;
            try (Jedis jedis = jedisPool.getResource()) {
                deviceRisk = Double.parseDouble(jedis.get("risk:" + deviceFp + ":score"));
                failures = Integer.parseInt(jedis.get("risk:" + deviceFp + ":fails"));
                accountStatus = jedis.get("account:" + userId + ":status");
            }

            // Geo-IP lookup
            String url = geoIpService + "?ip=" + URLEncoder.encode(ip, StandardCharsets.UTF_8);
            Request geoReq = new Request.Builder().url(url).build();
            try (Response geoRes = httpClient.newCall(geoReq).execute()) {
                JsonNode geoJson = mapper.readTree(geoRes.body().string());
                double ipRep = geoJson.get("reputation").asDouble();
                boolean isTor = geoJson.get("is_tor").asBoolean();

                // ML scoring call
                ObjectNode features = mapper.createObjectNode();
                features.put("latency_ms", latency);
                features.put("device_risk_score", deviceRisk);
                features.put("login_failures", failures);
                features.put("account_status", accountStatus);
                features.put("ip_reputation", ipRep);
                features.put("is_tor", isTor);

                RequestBody body = RequestBody.create(features.toString(), JSON);
                Request mlReq = new Request.Builder()
                        .url(mlService)
                        .post(body)
                        .build();

                try (Response mlRes = httpClient.newCall(mlReq).execute()) {
                    JsonNode mlJson = mapper.readTree(mlRes.body().string());
                    double score = mlJson.get("anomaly_score").asDouble();

                    if (score > 0.8) {
                        // Record a flagged session
                        flaggedCounter.inc();

                        // Record end-to-end latency
                        long now = System.currentTimeMillis();
                        long eventTs = ((Long) rec.get("timestamp")).longValue();
                        latencyHistogram.update(now - eventTs);

                        out.collect(Tuple2.of(sessionId, score));
                    }
                }
            }
        }

        @Override
        public void close() {
            jedisPool.close();
        }
    }
}
