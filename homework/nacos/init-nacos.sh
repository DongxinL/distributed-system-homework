#!/bin/sh
set -eu

publish_config() {
  data_id="$1"
  file_path="$2"
  curl -sf -X POST "http://nacos:8848/nacos/v1/cs/configs" \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode "group=DEFAULT_GROUP" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content@${file_path}" >/dev/null
  echo "published ${data_id}"
}

echo "waiting for nacos..."
until curl -sf "http://nacos:8848/nacos/v1/console/health/liveness" >/dev/null; do
  sleep 3
done

publish_config "flash-sale-service.yaml" "/configs/flash-sale-service.yaml"
publish_config "gateway-service.yaml" "/configs/gateway-service.yaml"

echo "nacos config initialization completed"