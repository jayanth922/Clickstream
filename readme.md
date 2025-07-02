# Clickstream Analytics Demo

## Service Roles & Ports
- **Zookeeper**: 2181
- **Kafka**: 9092
- **Flink JobManager**: 8081
- **Redis**: 6379
- **Airflow**: 8080
- **Prometheus**: 9090
- **Grafana**: 3000

## Quickstart
1. `docker-compose up -d`
2. Verify:
   - Airflow UI → http://localhost:8080  
   - Flink UI     → http://localhost:8081  
   - Grafana      → http://localhost:3000  
   - Prometheus   → http://localhost:9090
