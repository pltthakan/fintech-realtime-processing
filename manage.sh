#!/bin/bash

# ============================================
# FINTECH REALTIME PROCESSING SYSTEM
# Altyapı Yönetim Scripti
# ============================================

set -e

COMPOSE_FILE="docker-compose.yml"
PROJECT_NAME="fintech"

# Renkli çıktı
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_header() {
    echo ""
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo ""
}

print_success() { echo -e "${GREEN}✓ $1${NC}"; }
print_warning() { echo -e "${YELLOW}⚠ $1${NC}"; }
print_error()   { echo -e "${RED}✗ $1${NC}"; }

# ============================================
# KOMUTLAR
# ============================================

start_infra() {
    print_header "Altyapı Servisleri Başlatılıyor"

    echo "1/5 - PostgreSQL başlatılıyor..."
    docker compose -f $COMPOSE_FILE up -d postgresql
    sleep 5
    print_success "PostgreSQL hazır"

    echo "2/5 - MongoDB ve Redis başlatılıyor..."
    docker compose -f $COMPOSE_FILE up -d mongodb redis
    sleep 3
    print_success "MongoDB ve Redis hazır"

    echo "3/5 - Zookeeper ve Kafka başlatılıyor..."
    docker compose -f $COMPOSE_FILE up -d zookeeper
    sleep 15
    docker compose -f $COMPOSE_FILE up -d kafka
    sleep 10
    print_success "Kafka hazır"

    echo "4/5 - Kafka topic'leri oluşturuluyor..."
    docker compose -f $COMPOSE_FILE up kafka-init
    print_success "Topic'ler oluşturuldu"

    echo "5/5 - Kafka Connect, RabbitMQ ve Kafka UI başlatılıyor..."
    docker compose -f $COMPOSE_FILE up -d kafka-connect rabbitmq kafka-ui
    sleep 15
    print_success "Tüm altyapı servisleri hazır"

    print_header "Altyapı Başarıyla Ayağa Kalktı!"
    show_urls
}

stop_infra() {
    print_header "Altyapı Servisleri Durduruluyor"
    docker compose -f $COMPOSE_FILE down
    print_success "Tüm servisler durduruldu"
}

restart_infra() {
    stop_infra
    start_infra
}

destroy_infra() {
    print_header "Altyapı Tamamen Siliniyor (Volume'lar dahil)"
    docker compose -f $COMPOSE_FILE down -v --remove-orphans
    print_success "Tüm servisler ve veriler silindi"
}

show_status() {
    print_header "Servis Durumları"
    docker compose -f $COMPOSE_FILE ps
}

show_urls() {
    echo ""
    echo -e "${GREEN}Erişim Adresleri:${NC}"
    echo "  Frontend    : http://localhost:${FRONTEND_HOST_PORT:-3001}"
    echo "  API Gateway : http://localhost:${API_GATEWAY_HOST_PORT:-8087}"
    echo "  Eureka      : http://localhost:8761"
    echo "  Kafka UI    : http://localhost:9090"
    echo "  Kafka Connect REST: http://localhost:${KAFKA_CONNECT_HOST_PORT:-8088}"
    echo "  RabbitMQ UI : http://localhost:15672"
    echo "  RedisInsight: http://localhost:5540"
    echo ""
    echo -e "${YELLOW}Sadece Docker ağına açık servisler:${NC}"
    echo "  PostgreSQL, MongoDB, Redis, Kafka, Zookeeper, RabbitMQ AMQP"
    echo "  User, Account, Transaction, Fraud, Notification ve Reporting servisleri"
    echo "  Kimlik bilgileri: .env (Git tarafından yok sayılır)"
    echo ""
}

show_logs() {
    local service=${1:-""}
    if [ -z "$service" ]; then
        docker compose -f $COMPOSE_FILE logs -f --tail=50
    else
        docker compose -f $COMPOSE_FILE logs -f --tail=50 "$service"
    fi
}

deploy_connector() {
    print_header "MongoDB Sink Connector Deploy Ediliyor"

    # Kafka Connect hazır mı kontrol et
    echo "Kafka Connect hazır mı kontrol ediliyor..."
    local connect_url="http://localhost:${KAFKA_CONNECT_HOST_PORT:-8088}"
    until curl -s "$connect_url/connectors" > /dev/null 2>&1; do
        echo "  Kafka Connect bekleniyor..."
        sleep 5
    done
    print_success "Kafka Connect hazır"

    # Connector'ı deploy et
    echo "Connector oluşturuluyor..."
    curl -s -X POST \
        -H "Content-Type: application/json" \
        -d @kafka-connect-config/mongodb-sink-connector.json \
        "$connect_url/connectors" | python3 -m json.tool 2>/dev/null || echo ""

    print_success "MongoDB Sink Connector deploy edildi"

    echo ""
    echo "Connector durumu:"
    curl -s "$connect_url/connectors/mongodb-sink-completed-transactions/status" | python3 -m json.tool 2>/dev/null || echo "Henüz hazır değil, birkaç saniye bekleyin."
}

migrate_database() {
    print_header "PostgreSQL Şema Göçü Uygulanıyor"

    docker compose -f $COMPOSE_FILE exec -T postgresql sh -c \
        'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /docker-entrypoint-initdb.d/02-add-transaction-owner.sql'

    print_success "Transaction sahiplik göçü uygulandı"
}

check_topics() {
    print_header "Kafka Topic Listesi"
    docker compose -f $COMPOSE_FILE exec kafka kafka-topics --list --bootstrap-server localhost:9092
}

# ============================================
# KULLANIM
# ============================================

show_help() {
    echo ""
    echo "Kullanım: ./manage.sh [komut] [parametreler]"
    echo ""
    echo "Komutlar:"
    echo "  start           Tüm altyapı servislerini başlat"
    echo "  stop            Tüm servisleri durdur"
    echo "  restart         Servisleri yeniden başlat"
    echo "  destroy         Tüm servisleri ve verileri sil"
    echo "  status          Servis durumlarını göster"
    echo "  urls            Erişim adreslerini göster"
    echo "  logs [servis]   Logları takip et (opsiyonel: servis adı)"
    echo "  connector       MongoDB Sink Connector'ı deploy et"
    echo "  migrate         Mevcut veritabanına şema göçlerini uygula"
    echo "  topics          Kafka topic'lerini listele"
    echo "  help            Bu yardım mesajını göster"
    echo ""
    echo "Örnekler:"
    echo "  ./manage.sh start"
    echo "  ./manage.sh logs kafka"
    echo "  ./manage.sh connector"
    echo ""
}

# ============================================
# ANA ÇALIŞTIRICI
# ============================================

case "${1:-help}" in
    start)      start_infra ;;
    stop)       stop_infra ;;
    restart)    restart_infra ;;
    destroy)    destroy_infra ;;
    status)     show_status ;;
    urls)       show_urls ;;
    logs)       show_logs "$2" ;;
    connector)  deploy_connector ;;
    migrate)    migrate_database ;;
    topics)     check_topics ;;
    help|*)     show_help ;;
esac
