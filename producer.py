# ── producer.py ──
from confluent_kafka.avro import AvroProducer
import avro.schema
import uuid
import time

# 1. Load THE schema you created
schema_path = "schemas/banking_ui_event.avsc"
value_schema = avro.schema.load(schema_path)

# 2. Configure AvroProducer
producer_config = {
    "bootstrap.servers": "kafka:9092",
    "schema.registry.url": "http://schema-registry:8081"
}
producer = AvroProducer(producer_config, default_value_schema=value_schema)

def send_event(event):
    """
    event: dict matching Avro schema fields
    """
    # Use event_id as the message key to support log compaction
    key = event["event_id"]
    producer.produce(
      topic="banking-ui-events",
      key=key,
      value=event
    )
    producer.flush()

if __name__ == "__main__":
    while True:
        # Simulate a UI event
        event = {
            "event_id": str(uuid.uuid4()),
            "session_id": str(uuid.uuid4()),
            "user_id": "user-123",
            "event_type": "nav_click",
            "element_id": "btn-transfer",
            "input_value_hash": "",
            "page": "/dashboard",
            "timestamp": int(time.time() * 1000),
            "latency_ms": 120,
            "device_fingerprint": "fp-xyz",
            "ip_address": "203.0.113.42"
        }
        send_event(event)
        time.sleep(0.1)
