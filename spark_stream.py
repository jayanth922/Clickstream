#!/usr/bin/env python3
import json
from pyspark.sql import SparkSession
from pyspark.sql.functions import from_json, col, window, to_timestamp
from pyspark.sql.types import StructType, StringType
import redis

# ========== 1) Initialize Spark ==========
spark = (
    SparkSession.builder
    .appName("ClickstreamAnalyticsWithRedis")
    .getOrCreate()
)

# ========== 2) Redis Client ==========
# Connect to localhost:6379
redis_client = redis.Redis(host='localhost', port=6379, db=0)

def write_to_redis(batch_df, batch_id):
    """
    For each row in the micro-batch, write:
      key = f"click_count:{page}"
      field = window_end ISO string
      value = cnt
    into a Redis hash so you can HGETALL per page.
    """
    records = batch_df.collect()
    for row in records:
        page = row['page']
        cnt = row['cnt']
        ts  = row['window_end'].isoformat()
        key = f"click_count:{page}"
        # Redis hash: field=timestamp, value=count
        redis_client.hset(key, ts, cnt)

# ========== 3) Schema & Source ==========
schema = (
    StructType()
    .add("user_id", StringType())
    .add("session_id", StringType())
    .add("timestamp", StringType())
    .add("page", StringType())
)

df_raw = (
    spark.readStream
    .format("kafka")
    .option("kafka.bootstrap.servers", "localhost:9092")
    .option("subscribe", "clickstream-events")
    .option("startingOffsets", "earliest")
    .load()
)

df = (
    df_raw
    .select(from_json(col("value").cast("string"), schema).alias("data"))
    .selectExpr(
        "data.user_id",
        "data.session_id",
        # parse ISO string into Spark TimestampType
        "to_timestamp(data.timestamp) as event_time",
        "data.page"
    )
)

# ========== 4) Aggregation ==========
agg = (
    df
    .withWatermark("event_time", "5 seconds")
    .groupBy(
        window(col("event_time"), "1 minute"),
        col("page")
    )
    .count()
    .selectExpr(
        "page",
        "count as cnt",
        "window.end as window_end"
    )
)

# ========== 5) Write to Redis ==========
query = (
    agg.writeStream
       .outputMode("update")
       .foreachBatch(write_to_redis)
       .option("checkpointLocation", "/tmp/spark-redis-checkpoint")
       .start()
)

query.awaitTermination()
