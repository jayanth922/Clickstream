from airflow import DAG
from airflow.operators.bash import BashOperator
from airflow.operators.python import PythonOperator
from datetime import datetime, timedelta
import redis

default_args = {
    'owner': 'jayanth',
    'depends_on_past': False,
    'retries': 1,
    'retry_delay': timedelta(minutes=1),
}

def verify_redis():
    r = redis.Redis(host='localhost', port=6379, db=0)
    keys = r.keys("click_count:*")
    if not keys:
        raise ValueError("No click_count hashes found in Redis")
    # For debugging: print each hash
    for k in keys:
        print(f"{k.decode()}: {r.hgetall(k)}")

with DAG(
    dag_id='clickstream_pipeline',
    default_args=default_args,
    start_date=datetime(2025, 6, 10),
    schedule_interval='@hourly',  # or '@once' / custom
    catchup=False
) as dag:

    create_topic = BashOperator(
        task_id='create_kafka_topic',
        bash_command=(
            "docker-compose exec kafka "
            "kafka-topics.sh --create "
            "--topic clickstream-events "
            "--bootstrap-server localhost:9092 "
            "--partitions 3 --replication-factor 1 || true"
        )
    )

    start_producer = BashOperator(
        task_id='start_producer',
        bash_command=(
            "cd /opt/clickstream-demo && "
            "nohup ./producer.py > producer.log 2>&1 &"
        )
    )

    run_spark_batch = BashOperator(
        task_id='run_spark_batch',
        bash_command=(
            "cd /opt/clickstream-demo && "
            "spark-submit "
            "--packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0 "
            "spark_batch.py"
        )
    )

    verify = PythonOperator(
        task_id='verify_redis',
        python_callable=verify_redis
    )

    create_topic >> start_producer >> run_spark_batch >> verify
