package com.bank.clickstream;

import java.io.File;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.avro.registry.confluent.ConfluentRegistryAvroDeserializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;

public class BankingFraudDetectionJob {

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // ─── 1. Load Avro schema
        // ───────────────────────────────────────────────────────────
        Schema avroSchema = new Schema.Parser()
                .parse(new File("schemas/banking_ui_event.avsc"));

        // ─── 2. Build KafkaSource with Avro deserialization
        // ───────────────────────────────
        KafkaSource<GenericRecord> source = KafkaSource.<GenericRecord>builder()
                .setBootstrapServers("kafka:9092")
                .setTopics("banking-ui-events")
                .setGroupId("flink-avro-consumer")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(
                        ConfluentRegistryAvroDeserializationSchema
                                .forGeneric(avroSchema, "http://schema-registry:8081") // requires
                                                                                       // org.apache.avro.Schema
                                                                                       // :contentReference[oaicite:0]{index=0}
                )
                .build();

        // ─── 3. Ingest and map to (sessionId, initialScore)
        // ───────────────────────────────
        DataStream<Tuple2<String, Double>> raw = env.fromSource(
                source,
                WatermarkStrategy.forMonotonousTimestamps(),
                "Avro Kafka Source").map(record -> {
                    String sessionId = record.get("session_id").toString();
                    // stub score (real model in Step 4)
                    double score = 0.0;
                    return Tuple2.of(sessionId, score);
                });

        // ─── 4. Key by session and apply scoring process function
        // ─────────────────────────
        DataStream<Tuple2<String, Double>> flagged = raw
                .keyBy(tuple -> tuple.f0)
                .process(new FraudScoringProcessFunction());

        // ─── 5. Sink flagged sessions back to Kafka
        // ───────────────────────────────────────
        KafkaSink<Tuple2<String, Double>> sink = KafkaSink.<Tuple2<String, Double>>builder()
                .setBootstrapServers("kafka:9092")
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic("fraud-flags")
                                .setKeySerializationSchema((ctx, rec) -> rec.f0.getBytes())
                                .setValueSerializationSchema((ctx, rec) ->
                                // simple CSV: sessionId,score
                                (rec.f0 + "," + rec.f1).getBytes())
                                .build())
                .build();

        flagged.sinkTo(sink);

        env.execute("Banking Fraud & Bot-Detection");
    }

    /**
     * Stubbed ProcessFunction: in Step 4 you’ll replace this with your real anomaly
     * logic.
     */
    public static class FraudScoringProcessFunction
            extends KeyedProcessFunction<String, Tuple2<String, Double>, Tuple2<String, Double>> {

        @Override
        public void processElement(
                Tuple2<String, Double> value,
                Context ctx,
                Collector<Tuple2<String, Double>> out) throws Exception {
            // TODO: enrich & score
            out.collect(value);
        }
    }
}
