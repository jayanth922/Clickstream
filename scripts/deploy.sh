#!/bin/bash

set -e

echo "=== Network Intrusion Detection System Deployment ==="

# Check if model files exist
echo "Checking model artifacts..."
if [ ! -f "models/tabnet_best.onnx" ]; then
    echo "ERROR: models/tabnet_best.onnx not found!"
    echo "Please copy your ONNX model to the models/ directory"
    exit 1
fi

if [ ! -f "models/scaler.pkl" ]; then
    echo "ERROR: models/scaler.pkl not found!"
    exit 1
fi

if [ ! -f "models/label_encoder.pkl" ]; then
    echo "ERROR: models/label_encoder.pkl not found!"
    exit 1
fi

echo "✓ Model artifacts found"

# Build Flink job
echo "Building Flink job..."
cd flink-jobs
mvn clean package -q
cd ..

# Copy JAR to lib directory
mkdir -p flink-jobs/lib
cp flink-jobs/target/flink-jobs-1.0-SNAPSHOT.jar flink-jobs/lib/
echo "✓ Flink job built and copied to lib/"

# Start infrastructure
echo "Starting infrastructure..."
docker-compose up -d zookeeper kafka redis flink-jobmanager flink-taskmanager prometheus grafana

# Wait for services to be ready
echo "Waiting for services to be ready..."
sleep 30

# Create Kafka topics
echo "Creating Kafka topics..."
bash scripts/create-topics.sh

# Deploy Flink job
echo "Deploying Flink job..."
sleep 10
docker-compose exec flink-jobmanager flink run -d /opt/flink/usrlib/flink-jobs-1.0-SNAPSHOT.jar

echo "✓ Network Intrusion Detection System deployed successfully!"
echo ""
echo "Access URLs:"
echo "  - Flink UI: http://localhost:8081"
echo "  - Prometheus: http://localhost:9090"
echo "  - Grafana: http://localhost:3000 (admin/admin)"
echo ""
echo "To test the system, run the network flow producer:"
echo "  python3 network_flow_producer.py"