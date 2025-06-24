# Clickstream Analytics Technical Demonstration

> **NOTE:** This repository contains a fully functional clickstream analytics pipeline built end-to‐end using open-source components. It is intended to **demonstrate technical expertise** in real-time data ingestion, stream processing, orchestration, and observability. **It is _not_ hardened for production**—see the “Future Improvements” section for how to evolve this prototype to a production-grade system.

---

## Table of Contents

1. [Project Overview](#project-overview)  
2. [Key Components](#key-components)  
3. [Prerequisites](#prerequisites)  
4. [Getting Started](#getting-started)  
5. [Service Breakdown](#service-breakdown)  
   - [ZooKeeper](#1-zookeeper)  
   - [Kafka](#2-kafka)  
   - [Producer](#3-producer)  
   - [Spark Structured Streaming](#4-spark-structured-streaming)  
   - [Redis Feature Store](#5-redis-feature-store)  
   - [Airflow Orchestration](#6-airflow-orchestration)  
   - [Monitoring & Alerting](#7-monitoring--alerting)  
   - [Visualization (Grafana)](#8-visualization-grafana)  
6. [Project Structure](#project-structure)  
7. [Usage Examples](#usage-examples)  
8. [Future Improvements](#future-improvements)  
9. [License](#license)  

---

## Project Overview

This technical demonstration ingests synthetic clickstream events into Kafka, processes them in (micro-)batches with Spark Structured Streaming, writes aggregated per-page counts into Redis, orchestrates end-to-end via Airflow, and implements end-to-end observability with Prometheus, Alertmanager, and Grafana. It showcases:

- **Real-time ingestion** at 50 events/sec  
- **Event-time windowing** and stateful aggregation  
- **Low-latency feature serving** in Redis  
- **Automated workflows** and SLA checks in Airflow  
- **Production-style** monitoring & alerting (Prometheus + Alertmanager)  
- **Live dashboards** in Grafana  

---

## Key Components

- **Docker Compose**: Local deployment of all services  
- **Kafka**: Distributed event bus for clickstream data  
- **Spark Structured Streaming**: Micro-batch processing engine  
- **Redis**: In-memory feature store  
- **Airflow**: Workflow orchestration & validation  
- **Prometheus & Alertmanager**: Metrics collection & alerting  
- **Grafana**: Real-time dashboards  

---

## Prerequisites

- **macOS / Linux** machine with Docker & Docker Compose installed  
- **Python 3.8+** for running `producer.py` and local scripts  
- **Java 11** for Spark Structured Streaming  
- **Network**: Docker bridge network allowed on ports:  
  - Kafka: 9092  
  - Zookeeper: 2181  
  - Spark UI: 4040 (optional)  
  - Prometheus: 9090  
  - Grafana: 3000  
  - Alertmanager: 9093  
  - Redis-Exporter: 9121  
  - Kafka-Exporter: 9308  
  - Flink-Metrics: 9450  
  - StatsD-Exporter: 9102  

---

## Getting Started

1. **Clone the repo**  
   ```bash
   git clone https://github.com/your-org/clickstream-demo.git
   cd clickstream-demo

2. **Launch all services**
   ```bash
   docker-compose up -d

3. **Verify services**
   ```bash
   docker ps --format "table {{.Names}}\t{{.Status}}"

4. **Run the producer (in a new terminal)**
   ```bash
   cd clickstream-demo
   ./producer.py

5. **Trigger the Airflow DAG**
   ```bash
   Open http://localhost:8080 (admin/admin)
   Refresh DAGs → Trigger clickstream_pipeline

6. **Open dashboards**
   ```bash
   Prometheus: http://localhost:9090/targets
   Alertmanager: http://localhost:9093
   Grafana: http://localhost:3000 (admin/admin)


---

## Service Breakdown

1. **ZooKeeper**
   - Coordinates Kafka brokers.
   - Image: bitnami/zookeeper:3.7.1
   - Healthcheck: zkServer.sh status

2. **Kafka**
   - Persistent, partitioned clickstream topic clickstream-events.
   - Image: bitnami/kafka:3.3.2
   - Healthcheck: kafka-broker-api-versions --bootstrap-server localhost:9092

3. **Producer**
   - Simulates user page views at configurable RPS.
   - Script: producer.py
   - Library: kafka-python

4. **Spark Structured Streaming**
   - Aggregates per-page counts in 1 min tumbling windows.
   - App: spark_stream.py (streaming)
   - App: spark_batch.py (batch for Airflow DAG)
   - Framework: PySpark + Kafka connector

5. **Redis Feature Store**
   - Stores windowed counts as Redis hashes:
     ```bash
     HSET click_count:/home 2025-06-10T05:04:00 35
   - Image: redis:6.2.13-alpine
  
6. **Airflow Orchestration**
   - Defines DAG clickstream_pipeline in dags/clickstream_pipeline.py:
   - create_kafka_topic
   - start_producer
   - run_spark_batch
   - verify_redis

7. **Monitoring & Alerting**
   - Prometheus scrapes:
   - redis-exporter:9121
   - kafka-exporter:9308
   - flink-jobmanager:9450
   - statsd-exporter:9102
   - prometheus:9090
   - Alert Rules: fire on up==0 for 2 min (KafkaDown, RedisDown, FlinkDown, AirflowDown)
   - Alertmanager: routes to Slack / email via alertmanager/config.yml

8. **Visualization (Grafana)**
   - Dashboards: imported from grafana/clickstream-dashboard.json
   - Panels:
      - Service Availability (promQL, max by(job)(up{job=~...}))
      - Click Counts (Redis key-value query)
      - DAG Success Rate (promQL ratio of dag_run metrics)
    
---

## Project Structure

```text
clickstream-demo/
├── dags/
│   └── clickstream_pipeline.py       # Airflow DAG for orchestrating the pipeline
├── producer.py                       # Kafka producer to simulate clickstream data
├── spark_stream.py                   # Spark Structured Streaming app for real-time processing
├── spark_batch.py                    # Spark batch job for periodic processing via Airflow
├── prometheus.yml                    # Prometheus configuration for scraping exporters
├── alert_rules.yml                   # Alert rules for Prometheus (e.g., service down)
├── alertmanager/
│   └── config.yml                    # Alertmanager config for routing alerts
├── grafana/
│   └── clickstream-dashboard.json    # Grafana dashboard configuration
├── docker-compose.yml                # Docker Compose file for all services
└── README.md                         # Documentation for the entire project
```

---

## Usage Examples

1. Produce 50 events/sec
   ```bash
   ./producer.py

2. Trigger Airflow DAG via CLI
   ```bash
   airflow dags trigger clickstream_pipeline

3. Inspect Redis feature store
   ```bash
   docker-compose exec redis redis-cli HGETALL click_count:/home

4. Query in Prometheus
   ```bash
   rate(airflow_dag_run_total[1h]) - rate(airflow_dag_run_failed_total[1h])

5. Grafana panels auto‐refresh every 15s by default

---

## Future Improvements

1. **Production-grade security:**

   - Enable ACLs & TLS in ZK/Kafka

   - Requirepass in Redis + Grafana auth

2. **Scalability & High Availability:**

   - Kafka RF > 1, ZooKeeper quorum > 3

   - Spark on standalone cluster or YARN/K8s

   - Redis Sentinel/Cluster for failover

3. **Schema & Governance:**

   - Schema Registry (Confluent) for Kafka topics

   - Data catalog (e.g. OpenMetadata) integration

4. **CI/CD & Testing:**

   - GitHub Actions for linting, smoke tests

   - Integration tests with TestContainers

5. **Cost & Resource Management:**

   - Auto‐scaling Flink / Spark executors

   - Kubernetes operator for DAGs & pipelines
  

---

# Clickstream Fraud-Detection Platform – Continuation Summary

This README captures all work completed in our recent continuation, so we know exactly where to pick up next.

---

## 1. Avro Schema Definition  
- Created `schemas/banking_ui_event.avsc` describing the `BankingUIEvent` record.  
- Fields include UUIDs, event_type, element_id, masked input hash, timestamp (millis), latency, device fingerprint, IP.  

## 2. Schema Registry & Avro Producer  
- Added Confluent Schema Registry service to `docker-compose.yml` (port 8081).  
- Installed `'confluent-kafka[avro]'` and `avro-python3` (via pip, Option B with prebuilt wheel).  
- Updated `producer.py` to use `AvroProducer` with registry URL, defaulting to our new Avro schema.  

## 3. Flink Job & Build Infrastructure  
- Created `flink-jobs/pom.xml` (Flink 1.16.0, Confluent overrides, shade plugin).  
- Mounted connector JARs (`kafka-schema-registry-client-7.4.0.jar`, `common-utils-7.4.0.jar`, `flink-avro-confluent-registry-1.16.0.jar`) into `/opt/flink/usrlib`.  
- Installed missing Confluent client JARs into local Maven repo so the build could resolve them.  
- Packaged a fat JAR (`flink-jobs-1.0-SNAPSHOT.jar`) via `mvn clean package -DskipTests`.  

## 4. Flink Job Implementation  
- Wrote `BankingFraudDetectionJob.java`  
  - **KafkaSource** with `ConfluentRegistryAvroDeserializationSchema` pointing at Schema Registry.  
  - **EnrichmentAndScoringFunction** stub:
    - Redis lookup (Jedis) for device risk / login failures / account status.  
    - Geo-IP HTTP call (okhttp3) to external service.  
    - ML scoring HTTP call to `/score` endpoint.  
    - Emits only sessions with `anomaly_score > 0.8`.  
  - **KafkaSink** writing flagged sessions to `fraud-flags` topic as `sessionId,score`.  

## 5. Custom Metric Instrumentation  
- Imported `org.apache.flink.metrics.Counter` and `DescriptiveStatisticsHistogram`.  
- In `open()` registered:
  - `flagged_sessions_total` (Counter)  
  - `pipeline_latency_ms` (Histogram, single-arg constructor)  
- In `processElement()` incremented counter and recorded latency (`now – event.timestamp`) whenever a session was flagged.  

## 6. Observability Configuration  
- **Flink PrometheusReporter** enabled in `flink-conf.yaml` on port 9249.  
- **Prometheus scrape jobs** added in `prometheus.yml` for:
  - `jobmanager:9249`  
  - `taskmanager:9249`  
- **Alert rules** appended in `alert_rules.yml`:
  - `HighFraudFlagRate` (> 5 flags/min → warning)  
  - `PipelineLatencySLABreach` (P95 > 500 ms → critical)  

## 7. Grafana Dashboard Enhancements  
- Added two new panels to `clickstream-dashboard.json`:
  1. **Fraud-Flag Throughput** (PromQL `rate(...flagged_sessions_total[1m])`)  
  2. **Pipeline Latency (P95)** (PromQL `histogram_quantile(0.95, sum(rate(...latency_ms_bucket[5m])) by (le))`)  

---


## Placeholders & Simulations Remaining

1. **Event Producer & Schema**  
   - Simulator in `producer.py` must be replaced with real front-end/back-end instrumentation.  
   - Avro schema versioning & evolution pipeline via CI.

2. **ML Scoring Service**  
   - Dummy Flask stub always returns `1.0`.  
   - Needs real model deployment (TensorFlow Serving, Spark ML endpoint, custom microservice).  
   - Hard-coded threshold (`>0.8`) should be driven by runtime config.

3. **Geo-IP Enrichment**  
   - `geoip-service` stub must be implemented with real Geo-IP data (MaxMind, IP2Location).

4. **Redis Risk Lookup**  
   - Simulated Redis keys (`risk:<fp>:score`, `account:<user>:status`) need real data pipelines populating those values.

5. **Flink Process Logic**  
   - `EnrichmentAndScoringFunction` stub (“// TODO…”) must be expanded to full feature extraction, stateful windowing, and fraud-pattern detection.

6. **CI/CD & Deployment**  
   - Docker-Compose → Helm/Kustomize for Kubernetes.  
   - Automate JAR builds & deployments via CI pipeline (GitHub Actions, Jenkins).

7. **Secrets & Config**  
   - Move hard-coded endpoints and credentials into Vault or Kubernetes Secrets.  
   - Enable TLS, ACLs, and RBAC for all services.

8. **Observability at Scale**  
   - Migrate Prometheus to Operator + Thanos for long-term metrics.  
   - Add ServiceMonitors, expand scrape targets to Geo-IP, ML services.  
   - Implement centralized logging (ELK/Loki) and tracing (OpenTelemetry).

9. **Performance Testing & Auto-Scaling**  
   - Load-test click-streams (k6, Gatling).  
   - Configure HPAs for Flink TaskManagers and Kafka brokers (via Cruise Control).

---

## Next Milestone  
Choose which placeholder or simulation to replace first—e.g.:

- **Deploy a real ML model** and retire the dummy stub.  
- **Integrate your banking UI** to produce live events.  
- **Migrate to Kubernetes** with Helm charts and secrets management.

We’ll then iterate on that piece and continue hardening the platform.
