#!/bin/sh

set -e

KAFKA_CONNECT_URL="${KAFKA_CONNECT_URL:-http://localhost:8083}"
SCRIPT_DIR="$(dirname "$0")"

# Lista de conectores, um por linha: "nome_do_conector:arquivo_de_config.json"
# (sem usar array — sh puro não suporta arrays bash).
CONNECTORS="
payment-service-outbox-connector:debezium-payment-service-connector.json
ledger-service-outbox-connector:debezium-ledger-service-connector.json
"

echo "Aguardando o Kafka Connect ficar disponível..."
until curl -s -o /dev/null "$KAFKA_CONNECT_URL/connectors"; do
  sleep 2
done
echo "Kafka Connect disponível."

for entry in $CONNECTORS; do
  CONNECTOR_NAME="${entry%%:*}"
  CONFIG_FILE="${entry##*:}"

  HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$KAFKA_CONNECT_URL/connectors/$CONNECTOR_NAME")

  if [ "$HTTP_STATUS" = "200" ]; then
    echo "Conector '$CONNECTOR_NAME' já está registrado. Nada a fazer."
  else
    echo "Registrando o conector '$CONNECTOR_NAME'..."
    RESPONSE=$(curl -s -o /tmp/response.json -w "%{http_code}" -X POST \
      -H "Content-Type: application/json" \
      -d @"$SCRIPT_DIR/$CONFIG_FILE" \
      "$KAFKA_CONNECT_URL/connectors")

    if [ "$RESPONSE" = "201" ]; then
      echo "Conector '$CONNECTOR_NAME' registrado com sucesso."
    else
      echo "ERRO ao registrar '$CONNECTOR_NAME' (HTTP $RESPONSE):"
      cat /tmp/response.json
      echo ""
    fi
  fi
done