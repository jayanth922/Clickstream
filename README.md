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
   ```
   git clone https://github.com/your-org/clickstream-demo.git
   cd clickstream-demo


2. **Launch all services**
   ```
   docker-compose up -d


3. **Verify services**
   ```
   docker ps --format "table {{.Names}}\t{{.Status}}"

4. **Run the producer (in a new terminal)**
   ```
   cd clickstream-demo
   ./producer.py

5. **Trigger the Airflow DAG**
   
   Open http://localhost:8080 (admin/admin)
   Refresh DAGs → Trigger clickstream_pipeline

6. **Open dashboards**

   - Prometheus: http://localhost:9090/targets

   - Alertmanager: http://localhost:9093

   - Grafana: http://localhost:3000 (admin/admin)



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
  - HSET click_count:/home 2025-06-10T05:04:00 35
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


## Project Structure
clickstream-demo/
├── dags/
│   ├── clickstream_pipeline.py
│   └── example_dag.py
├── docker-compose.yml
├── producer.py
├── spark_stream.py
├── spark_batch.py
├── prometheus.yml
├── alert_rules.yml
├── alertmanager/
│   └── config.yml
└── grafana/
    └── clickstream-dashboard.json
