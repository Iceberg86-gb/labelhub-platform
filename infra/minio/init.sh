#!/usr/bin/env sh
set -eu

until mc alias set local http://minio:9000 "$S3_ACCESS_KEY" "$S3_SECRET_KEY"; do
  sleep 2
done

mc mb --ignore-existing "local/${S3_BUCKET}"
