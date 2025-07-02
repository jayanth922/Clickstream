package com.bank.clickstream;

import ai.onnxruntime.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class NetworkIntrusionDetectionJob {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        // Kafka Source for network flow data
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers("kafka:9092")
                .setTopics("network-flows")
                .setGroupId("intrusion-detection-consumer")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // Process network flows through ML model
        DataStream<String> processedFlows = env.fromSource(
                source,
                WatermarkStrategy.noWatermarks(),
                "Network Flow Source")
                .map(new NetworkIntrusionDetectionFunction())
                .filter(result -> result != null);

        // Kafka Sink for detected intrusions
        KafkaSink<String> sink = KafkaSink.<String>builder()
                .setBootstrapServers("kafka:9092")
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<String>builder()
                                .setTopic("intrusion-alerts")
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build())
                .build();

        processedFlows.sinkTo(sink);
        env.execute("Network Intrusion Detection with TabNet");
    }

    // NetworkFlow class definition
    public static class NetworkFlow {
        public int destinationPort;
        public long flowDuration;
        public int totalFwdPackets;
        public long totalLengthOfFwdPackets;
        public int fwdPacketLengthMax;
        public int fwdPacketLengthMin;
        public double fwdPacketLengthMean;
        public double fwdPacketLengthStd;
        public int bwdPacketLengthMax;
        public int bwdPacketLengthMin;
        public double bwdPacketLengthMean;
        public double bwdPacketLengthStd;
        public double flowBytesPerS;
        public double flowPacketsPerS;
        public double flowIATMean;
        public double flowIATStd;
        public long flowIATMax;
        public long flowIATMin;
        public long fwdIATTotal;
        public double fwdIATMean;
        public double fwdIATStd;
        public long fwdIATMax;
        public long fwdIATMin;
        public long bwdIATTotal;
        public double bwdIATMean;
        public double bwdIATStd;
        public long bwdIATMax;
        public long bwdIATMin;
        public long fwdHeaderLength;
        public long bwdHeaderLength;
        public double fwdPacketsPerS;
        public double bwdPacketsPerS;
        public int minPacketLength;
        public int maxPacketLength;
        public double packetLengthMean;
        public double packetLengthStd;
        public double packetLengthVariance;
        public int finFlagCount;
        public int pshFlagCount;
        public int ackFlagCount;
        public double averagePacketSize;
        public long subflowFwdBytes;
        public long initWinBytesForward;
        public long initWinBytesBackward;
        public int actDataPktFwd;
        public long minSegSizeForward;
        public double activeMean;
        public long activeMax;
        public long activeMin;
        public double idleMean;
        public long idleMax;
        public long idleMin;
        public String sourceIP;
        public String destinationIP;
    }

    // StandardScaler class definition
    public static class StandardScaler {
        private float[] means;
        private float[] stds;

        public StandardScaler() {
            // Initialize with default values - replace with your actual scaler parameters
            this.means = new float[52]; // Initialize with your actual means
            this.stds = new float[52];  // Initialize with your actual standard deviations
            
            // Fill with default values (you should replace these with actual values)
            for (int i = 0; i < 52; i++) {
                means[i] = 0.0f;
                stds[i] = 1.0f;
            }
        }

        public float[] transform(float[] features) {
            float[] scaled = new float[features.length];
            for (int i = 0; i < features.length; i++) {
                scaled[i] = (features[i] - means[i]) / stds[i];
            }
            return scaled;
        }
    }

    // LabelEncoder class definition
    public static class LabelEncoder {
        private String[] labels;

        public LabelEncoder() {
            // Initialize with attack type labels
            this.labels = new String[]{"Bots", "Brute Force", "DDoS", "DoS", "Normal Traffic", "Port Scanning", "Web Attacks"};
        }

        public String indexToLabel(int index) {
            if (index >= 0 && index < labels.length) {
                return labels[index];
            }
            return "Unknown";
        }
    }

    public static class NetworkIntrusionDetectionFunction extends RichMapFunction<String, String> {
        private transient OrtSession session;
        private transient OrtEnvironment ortEnv;
        private transient StandardScaler scaler;
        private transient LabelEncoder labelEncoder;
        private transient ObjectMapper objectMapper;
        private transient Counter intrusionsDetected;
        private transient Counter totalFlowsProcessed;

        // Attack type threshold - adjust based on your requirements
        private static final double NORMAL_TRAFFIC_THRESHOLD = 0.5;

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);

            // Initialize ONNX Runtime
            ortEnv = OrtEnvironment.getEnvironment();
            session = ortEnv.createSession("/opt/flink/models/tabnet_best.onnx");

            // Initialize preprocessing components with your exact values
            scaler = new StandardScaler();
            labelEncoder = new LabelEncoder();

            objectMapper = new ObjectMapper();

            // Register metrics
            intrusionsDetected = getRuntimeContext()
                    .getMetricGroup()
                    .counter("intrusions_detected");

            totalFlowsProcessed = getRuntimeContext()
                    .getMetricGroup()
                    .counter("total_flows_processed");
        }

        @Override
        public String map(String jsonFlow) throws Exception {
            totalFlowsProcessed.inc();

            try {
                JsonNode flowNode = objectMapper.readTree(jsonFlow);
                NetworkFlow flow = parseNetworkFlow(flowNode);

                // Preprocess features using your exact scaler parameters
                float[] features = preprocessFeatures(flow);

                // Run TabNet inference
                OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnv,
                        new float[][] { features });

                Map<String, OnnxTensor> inputs = new HashMap<>();
                inputs.put("features", inputTensor);

                OrtSession.Result result = session.run(inputs);
                // Fix: get the Optional<OnnxValue> for "output" and then get() then getValue()
                float[][] predictions = (float[][]) result.get("output").get().getValue();

                // Get prediction probabilities for all 7 classes
                float[] classProbabilities = predictions[0];

                // Find the predicted class (highest probability)
                int predictedClassIndex = 0;
                float maxProbability = classProbabilities[0];

                for (int i = 1; i < classProbabilities.length; i++) {
                    if (classProbabilities[i] > maxProbability) {
                        maxProbability = classProbabilities[i];
                        predictedClassIndex = i;
                    }
                }

                String predictedAttackType = labelEncoder.indexToLabel(predictedClassIndex);

                // Alert if it's not normal traffic and confidence is high enough
                if (!"Normal Traffic".equals(predictedAttackType) && maxProbability > NORMAL_TRAFFIC_THRESHOLD) {
                    intrusionsDetected.inc();

                    String alertJson = createAlertJson(flow, predictedAttackType, maxProbability, classProbabilities);

                    // Clean up
                    inputTensor.close();
                    result.close();

                    return alertJson;
                }

                // Clean up
                inputTensor.close();
                result.close();

            } catch (Exception e) {
                System.err.println("Error processing flow: " + e.getMessage());
                e.printStackTrace();
            }

            return null; // No intrusion detected or normal traffic
        }

        private NetworkFlow parseNetworkFlow(JsonNode node) {
            NetworkFlow flow = new NetworkFlow();

            // Parse all 52 features in exact order from your training data
            flow.destinationPort = node.get("Destination Port").asInt();
            flow.flowDuration = node.get("Flow Duration").asLong();
            flow.totalFwdPackets = node.get("Total Fwd Packets").asInt();
            flow.totalLengthOfFwdPackets = node.get("Total Length of Fwd Packets").asLong();
            flow.fwdPacketLengthMax = node.get("Fwd Packet Length Max").asInt();
            flow.fwdPacketLengthMin = node.get("Fwd Packet Length Min").asInt();
            flow.fwdPacketLengthMean = node.get("Fwd Packet Length Mean").asDouble();
            flow.fwdPacketLengthStd = node.get("Fwd Packet Length Std").asDouble();
            flow.bwdPacketLengthMax = node.get("Bwd Packet Length Max").asInt();
            flow.bwdPacketLengthMin = node.get("Bwd Packet Length Min").asInt();
            flow.bwdPacketLengthMean = node.get("Bwd Packet Length Mean").asDouble();
            flow.bwdPacketLengthStd = node.get("Bwd Packet Length Std").asDouble();
            flow.flowBytesPerS = node.get("Flow Bytes/s").asDouble();
            flow.flowPacketsPerS = node.get("Flow Packets/s").asDouble();
            flow.flowIATMean = node.get("Flow IAT Mean").asDouble();
            flow.flowIATStd = node.get("Flow IAT Std").asDouble();
            flow.flowIATMax = node.get("Flow IAT Max").asLong();
            flow.flowIATMin = node.get("Flow IAT Min").asLong();
            flow.fwdIATTotal = node.get("Fwd IAT Total").asLong();
            flow.fwdIATMean = node.get("Fwd IAT Mean").asDouble();
            flow.fwdIATStd = node.get("Fwd IAT Std").asDouble();
            flow.fwdIATMax = node.get("Fwd IAT Max").asLong();
            flow.fwdIATMin = node.get("Fwd IAT Min").asLong();
            flow.bwdIATTotal = node.get("Bwd IAT Total").asLong();
            flow.bwdIATMean = node.get("Bwd IAT Mean").asDouble();
            flow.bwdIATStd = node.get("Bwd IAT Std").asDouble();
            flow.bwdIATMax = node.get("Bwd IAT Max").asLong();
            flow.bwdIATMin = node.get("Bwd IAT Min").asLong();
            flow.fwdHeaderLength = node.get("Fwd Header Length").asLong();
            flow.bwdHeaderLength = node.get("Bwd Header Length").asLong();
            flow.fwdPacketsPerS = node.get("Fwd Packets/s").asDouble();
            flow.bwdPacketsPerS = node.get("Bwd Packets/s").asDouble();
            flow.minPacketLength = node.get("Min Packet Length").asInt();
            flow.maxPacketLength = node.get("Max Packet Length").asInt();
            flow.packetLengthMean = node.get("Packet Length Mean").asDouble();
            flow.packetLengthStd = node.get("Packet Length Std").asDouble();
            flow.packetLengthVariance = node.get("Packet Length Variance").asDouble();
            flow.finFlagCount = node.get("FIN Flag Count").asInt();
            flow.pshFlagCount = node.get("PSH Flag Count").asInt();
            flow.ackFlagCount = node.get("ACK Flag Count").asInt();
            flow.averagePacketSize = node.get("Average Packet Size").asDouble();
            flow.subflowFwdBytes = node.get("Subflow Fwd Bytes").asLong();
            flow.initWinBytesForward = node.get("Init_Win_bytes_forward").asLong();
            flow.initWinBytesBackward = node.get("Init_Win_bytes_backward").asLong();
            flow.actDataPktFwd = node.get("act_data_pkt_fwd").asInt();
            flow.minSegSizeForward = node.get("min_seg_size_forward").asLong();
            flow.activeMean = node.get("Active Mean").asDouble();
            flow.activeMax = node.get("Active Max").asLong();
            flow.activeMin = node.get("Active Min").asLong();
            flow.idleMean = node.get("Idle Mean").asDouble();
            flow.idleMax = node.get("Idle Max").asLong();
            flow.idleMin = node.get("Idle Min").asLong();

            // Additional fields for context (not part of model features)
            flow.sourceIP = node.has("source_ip") ? node.get("source_ip").asText() : "unknown";
            flow.destinationIP = node.has("destination_ip") ? node.get("destination_ip").asText() : "unknown";

            return flow;
        }

        private float[] preprocessFeatures(NetworkFlow flow) {
            // Convert NetworkFlow to feature array in exact order (52 features)
            float[] rawFeatures = new float[52];

            rawFeatures[0] = flow.destinationPort;
            rawFeatures[1] = flow.flowDuration;
            rawFeatures[2] = flow.totalFwdPackets;
            rawFeatures[3] = flow.totalLengthOfFwdPackets;
            rawFeatures[4] = flow.fwdPacketLengthMax;
            rawFeatures[5] = flow.fwdPacketLengthMin;
            rawFeatures[6] = (float) flow.fwdPacketLengthMean;
            rawFeatures[7] = (float) flow.fwdPacketLengthStd;
            rawFeatures[8] = flow.bwdPacketLengthMax;
            rawFeatures[9] = flow.bwdPacketLengthMin;
            rawFeatures[10] = (float) flow.bwdPacketLengthMean;
            rawFeatures[11] = (float) flow.bwdPacketLengthStd;
            rawFeatures[12] = (float) flow.flowBytesPerS;
            rawFeatures[13] = (float) flow.flowPacketsPerS;
            rawFeatures[14] = (float) flow.flowIATMean;
            rawFeatures[15] = (float) flow.flowIATStd;
            rawFeatures[16] = flow.flowIATMax;
            rawFeatures[17] = flow.flowIATMin;
            rawFeatures[18] = flow.fwdIATTotal;
            rawFeatures[19] = (float) flow.fwdIATMean;
            rawFeatures[20] = (float) flow.fwdIATStd;
            rawFeatures[21] = flow.fwdIATMax;
            rawFeatures[22] = flow.fwdIATMin;
            rawFeatures[23] = flow.bwdIATTotal;
            rawFeatures[24] = (float) flow.bwdIATMean;
            rawFeatures[25] = (float) flow.bwdIATStd;
            rawFeatures[26] = flow.bwdIATMax;
            rawFeatures[27] = flow.bwdIATMin;
            rawFeatures[28] = flow.fwdHeaderLength;
            rawFeatures[29] = flow.bwdHeaderLength;
            rawFeatures[30] = (float) flow.fwdPacketsPerS;
            rawFeatures[31] = (float) flow.bwdPacketsPerS;
            rawFeatures[32] = flow.minPacketLength;
            rawFeatures[33] = flow.maxPacketLength;
            rawFeatures[34] = (float) flow.packetLengthMean;
            rawFeatures[35] = (float) flow.packetLengthStd;
            rawFeatures[36] = (float) flow.packetLengthVariance;
            rawFeatures[37] = flow.finFlagCount;
            rawFeatures[38] = flow.pshFlagCount;
            rawFeatures[39] = flow.ackFlagCount;
            rawFeatures[40] = (float) flow.averagePacketSize;
            rawFeatures[41] = flow.subflowFwdBytes;
            rawFeatures[42] = flow.initWinBytesForward;
            rawFeatures[43] = flow.initWinBytesBackward;
            rawFeatures[44] = flow.actDataPktFwd;
            rawFeatures[45] = flow.minSegSizeForward;
            rawFeatures[46] = (float) flow.activeMean;
            rawFeatures[47] = flow.activeMax;
            rawFeatures[48] = flow.activeMin;
            rawFeatures[49] = (float) flow.idleMean;
            rawFeatures[50] = flow.idleMax;
            rawFeatures[51] = flow.idleMin;

            // Apply scaling using your exact scaler parameters
            return scaler.transform(rawFeatures);
        }

        private String createAlertJson(NetworkFlow flow, String attackType, float confidence,
                float[] allProbabilities) {
            StringBuilder probabilitiesJson = new StringBuilder();
            probabilitiesJson.append("{");
            String[] attackTypes = { "Bots", "Brute Force", "DDoS", "DoS", "Normal Traffic", "Port Scanning",
                    "Web Attacks" };

            for (int i = 0; i < attackTypes.length; i++) {
                if (i > 0)
                    probabilitiesJson.append(",");
                probabilitiesJson.append(String.format("\"%s\":%.4f", attackTypes[i], allProbabilities[i]));
            }
            probabilitiesJson.append("}");

            return String.format(
                    "{\"timestamp\":%d,\"source_ip\":\"%s\",\"destination_ip\":\"%s\",\"destination_port\":%d," +
                            "\"attack_type\":\"%s\",\"confidence\":%.4f,\"all_probabilities\":%s," +
                            "\"flow_duration\":%d,\"total_fwd_packets\":%d,\"severity\":\"%s\"}",
                    System.currentTimeMillis(),
                    flow.sourceIP,
                    flow.destinationIP,
                    flow.destinationPort,
                    attackType,
                    confidence,
                    probabilitiesJson.toString(),
                    flow.flowDuration,
                    flow.totalFwdPackets,
                    getSeverity(attackType));
        }

        private String getSeverity(String attackType) {
            switch (attackType) {
                case "DDoS":
                case "DoS":
                    return "HIGH";
                case "Brute Force":
                case "Web Attacks":
                    return "MEDIUM";
                case "Bots":
                case "Port Scanning":
                    return "LOW";
                default:
                    return "UNKNOWN";
            }
        }

        @Override
        public void close() throws Exception {
            if (session != null)
                session.close();
            if (ortEnv != null)
                ortEnv.close();
            super.close();
        }
    }
}