import json
import time
import random
from kafka import KafkaProducer
from kafka.errors import KafkaError
import sys

def create_producer():
    """Create Kafka producer with retry logic"""
    max_retries = 10
    retry_delay = 5
    for attempt in range(max_retries):
        try:
            producer = KafkaProducer(
                bootstrap_servers=['localhost:9092'],
                value_serializer=lambda x: json.dumps(x).encode('utf-8'),
                request_timeout_ms=30000,
                metadata_max_age_ms=30000
            )
            # Try sending a dummy message to test the connection
            future = producer.send('network-flows', value={
                "probe": True,
                "timestamp": int(time.time() * 1000)
            })
            future.get(timeout=10)
            print("✓ Connected to Kafka successfully!")
            return producer
        except Exception as e:
            print(f"Attempt {attempt + 1}/{max_retries} failed: {e}")
            if attempt < max_retries - 1:
                print(f"Retrying in {retry_delay} seconds...")
                time.sleep(retry_delay)
            else:
                print("Failed to connect to Kafka after all retries")
                sys.exit(1)

def generate_network_flow():
    """Generate a realistic CICIDS2017-compliant network flow with 52 features."""
    attack_types = [
        "Normal", "DoS", "DDoS", "PortScan", "BruteForce", "WebAttack", "Botnet"
    ]
    is_attack = random.random() < 0.15
    attack_type = random.choice(attack_types[1:]) if is_attack else "Normal"

    # Realistic ranges for CICIDS2017 features
    flow = {
        "flowDuration": random.randint(1000, 2000000),
        "totalFwdPackets": random.randint(1, 10000),
        "totalBackwardPackets": random.randint(1, 5000),
        "totalLengthFwdPackets": random.randint(0, 10000000),
        "totalLengthBwdPackets": random.randint(0, 5000000),
        "fwdPacketLengthMax": random.randint(0, 1500),
        "fwdPacketLengthMin": random.randint(0, 1500),
        "fwdPacketLengthMean": random.uniform(0, 1500),
        "fwdPacketLengthStd": random.uniform(0, 500),
        "bwdPacketLengthMax": random.randint(0, 1500),
        "bwdPacketLengthMin": random.randint(0, 1500),
        "bwdPacketLengthMean": random.uniform(0, 1500),
        "bwdPacketLengthStd": random.uniform(0, 500),
        "flowBytesPerSec": random.uniform(0, 1_000_000),
        "flowPacketsPerSec": random.uniform(0, 10_000),
        "flowIATMean": random.uniform(0, 5_000_000),
        "flowIATStd": random.uniform(0, 1_000_000),
        "flowIATMax": random.randint(0, 10_000_000),
        "flowIATMin": random.randint(0, 1_000_000),
        "fwdIATTotal": random.randint(0, 10_000_000),
        "fwdIATMean": random.uniform(0, 1_000_000),
        "fwdIATStd": random.uniform(0, 500_000),
        "fwdIATMax": random.randint(0, 10_000_000),
        "fwdIATMin": random.randint(0, 1_000_000),
        "bwdIATTotal": random.randint(0, 10_000_000),
        "bwdIATMean": random.uniform(0, 1_000_000),
        "bwdIATStd": random.uniform(0, 500_000),
        "bwdIATMax": random.randint(0, 10_000_000),
        "bwdIATMin": random.randint(0, 1_000_000),
        "fwdPSHFlags": random.randint(0, 10),
        "bwdPSHFlags": random.randint(0, 10),
        "fwdURGFlags": random.randint(0, 10),
        "bwdURGFlags": random.randint(0, 10),
        "fwdHeaderLength": random.randint(20, 1000),
        "bwdHeaderLength": random.randint(20, 1000),
        "fwdPacketsPerSec": random.uniform(0, 10_000),
        "bwdPacketsPerSec": random.uniform(0, 10_000),
        "minPacketLength": random.randint(0, 1500),
        "maxPacketLength": random.randint(0, 1500),
        "packetLengthMean": random.uniform(0, 1500),
        "packetLengthStd": random.uniform(0, 500),
        "packetLengthVariance": random.uniform(0, 2_000_000),
        "FINFlagCount": random.randint(0, 10),
        "SYNFlagCount": random.randint(0, 10),
        "RSTFlagCount": random.randint(0, 10),
        "PSHFlagCount": random.randint(0, 10),
        "ACKFlagCount": random.randint(0, 20),
        "URGFlagCount": random.randint(0, 10),
        "CWEFlagCount": random.randint(0, 10),
        "ECEFlagCount": random.randint(0, 10),
        "downUpRatio": random.uniform(0, 100),
        "averagePacketSize": random.uniform(0, 1500),
        "avgFwdSegmentSize": random.uniform(0, 1500),
        "avgBwdSegmentSize": random.uniform(0, 1500),
        "attackType": attack_type,
        "timestamp": int(time.time() * 1000)
    }
    return flow

def main():
    print("Starting Network Flow Producer for Intrusion Detection...")
    print("Connecting to Kafka...")
    producer = create_producer()
    print("Generating and sending network flows...")
    flow_count = 0
    try:
        while True:
            flow = generate_network_flow()
            try:
                producer.send('network-flows', value=flow)
                flow_count += 1
                if flow_count % 100 == 0:
                    print(f"✓ Sent {flow_count} flows. Latest label: {flow['attackType']}")
            except KafkaError as e:
                print(f"Failed to send flow: {e}")
            time.sleep(0.05)  # 20 flows per second
    except KeyboardInterrupt:
        print(f"\nShutting down. Total flows sent: {flow_count}")
    finally:
        producer.close()

if __name__ == "__main__":
    main()