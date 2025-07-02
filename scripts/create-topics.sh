#!/bin/bash

echo "Creating Kafka topics for Network Intrusion Detection..."

# Wait for Kafka to be ready
echo "Waiting for Kafka to be ready..."
until docker-compose exec kafka kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1; do
    echo "Kafka is unavailable - sleeping..."
    sleep 2
done

echo "Kafka is ready!"

# Create topics
echo "Creating network-flows topic..."
docker-compose exec kafka kafka-topics --create \
    --topic network-flows \
    --bootstrap-server localhost:9092 \
    --partitions 3 \
    --replication-factor 1 \
    --if-not-exists

echo "Creating intrusion-alerts topic..."
docker-compose exec kafka kafka-topics --create \
    --topic intrusion-alerts \
    --bootstrap-server localhost:9092 \
    --partitions 3 \
    --replication-factor 1 \
    --if-not-exists

echo "Listing all topics..."
docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

echo "Topic creation completed!"