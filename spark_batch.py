#!/usr/bin/env python3
import json
import redis
from pyspark.sql import SparkSession
from pyspark.sql.functions import from_json, col, to_timestamp, window
from pyspark.sql.types import StructType, StringType

# 1) Spark setup
spark = (
    SparkSession.builder
    .appName("ClickstreamBatchToRedis")
    .getOrCreate()
)

# 2) Redis client
r = redis.Redis(host='localhost', port=6379, db=0)

# 3) Schema + read
schema = (
    StructType()
    .add("user_id", StringType())
    .add("session_id", StringType())
    .add("timestamp", StringType())
    .add("page", StringType())
)
df = (
    spark.read
         .format("kafka")
         .option("kafka.bootstrap.servers", "localhost:9092")
         .option("subscribe", "clickstream-events")
         .option("startingOffsets", "earliest")
         .option("endingOffsets", "latest")
         .load()
         .select(from_json(col("value").cast("string"), schema).alias("data"))
         .selectExpr(
             "data.user_id",
             "data.session_id",
             "to_timestamp(data.timestamp) as event_time",
             "data.page"
         )
)

# 4) Aggregate
agg = (
    df
    .groupBy(
      window(col("event_time"), "1 minute"),
      col("page")
    )
    .count()
    .selectExpr("page", "count as cnt", "window.end as window_end")
)

# 5) Collect & write to Redis
for row in agg.collect():
    key = f"click_count:{row['page']}"
    # store last‐minute count under the timestamp field
    r.hset(key, row['window_end'].isoformat(), row['cnt'])

print("Batch job complete. Wrote counts to Redis.")
