# Fintech Realtime Processing Platform

A real-time fintech transaction processing platform built with Spring Boot microservices, Apache Kafka, PostgreSQL, MongoDB, Redis, RabbitMQ, Docker, and Spring Cloud.

The system processes financial transactions through an event-driven pipeline that includes validation, fraud detection, balance updates, notifications, and transaction archiving.

---

# Architecture Overview

<img width="514" height="763" alt="Ekran Resmi 2026-04-07 22 56 47" src="https://github.com/user-attachments/assets/fce92903-4ea0-4778-871d-c69f88955678" />




The project uses a Kafka topic pipeline where each microservice is responsible for one stage of the transaction lifecycle.

---

# Features

* Real-time transaction processing
* Event-driven microservice architecture
* Fraud and AML validation pipeline
* Account balance update and transaction persistence
* Email / SMS / push notification support
* PostgreSQL for operational data
* MongoDB for completed transaction archive
* Redis for cache, session, and distributed lock support
* RabbitMQ for asynchronous notification delivery
* Eureka-based service discovery
* API Gateway routing with Spring Cloud Gateway
* Docker Compose environment for all services
* Kafka UI for topic monitoring

---

# Tech Stack

## Backend

* Java 21
* Spring Boot
* Spring Cloud Gateway
* Spring Security
* Spring Data JPA
* Hibernate
* Spring Cloud Netflix Eureka

## Messaging

* Apache Kafka
* Kafka Connect
* RabbitMQ

## Databases

* PostgreSQL
* MongoDB
* Redis

## Frontend

* React

## Infrastructure

* Docker
* Docker Compose
* Kafka UI
* Zookeeper

---

# Microservices

## API Gateway

Port: `8080`

Responsibilities:

* Single entry point for the frontend
* Route requests to microservices
* Future support for rate limiting, JWT validation, and logging

---

## User Service

Port: `8081`

Responsibilities:

* User registration and authentication
* JWT token generation
* Role-based access control

Database schema:

* `user_service`

---

## Account Service

Port: `8082`

Responsibilities:

* Update account balances
* Process transfer operations
* Persist account and transaction information

Consumes:

* `transaction-checked`

Produces:

* `transaction-processed`

Database schema:

* `account_service`

---

## Transaction Service

Port: `8083`

Responsibilities:

* Receive transaction requests
* Perform initial validation
* Publish transaction events to Kafka

Produces:

* `transaction-raw`

Database schema:

* `transaction_service`

---

## Fraud Detection Service

Port: `8084`

Responsibilities:

* Fraud and AML checks
* Detect suspicious transaction behavior
* Validate transaction amount and frequency

Consumes:

* `transaction-raw`

Produces:

* `transaction-checked`

Database schema:

* `fraud_service`

---

## Notification Service

Port: `8085`

Responsibilities:

* Send email, SMS, or push notifications
* Forward notification tasks to RabbitMQ workers

Consumes:

* `transaction-processed`

Produces:

* `transaction-completed`

---

## Reporting Service

Port: `8086`

Responsibilities:

* Dashboard and reporting APIs
* Query completed transactions from MongoDB
* Provide analytics and statistics

---

## Eureka Server

Port: `8761`

Responsibilities:

* Service registration and discovery
* Dynamic routing and load balancing support

---

# Kafka Topic Pipeline

```text
transaction-raw
    ↓
transaction-checked
    ↓
transaction-processed
    ↓
transaction-completed
```

Each topic represents a stage of the transaction lifecycle.

---

# Database Design

## PostgreSQL

Used for operational and transactional data.

Schemas:

* `user_service`
* `account_service`
* `transaction_service`
* `fraud_service`

Example tables:

* `users`
* `accounts`
* `transactions`
* `fraud_checks`

---

## MongoDB

Used to archive completed transactions and support reporting queries.

The archive flow:

```text
transaction-completed
      ↓
Kafka Connect Sink
      ↓
MongoDB
```

---

# Service Discovery

All services register themselves in Eureka.

Example configuration:

```yaml
spring:
  application:
    name: transaction-service

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

Example gateway route:

```yaml
uri: lb://TRANSACTION-SERVICE
```

---

# Running the Project

## 1. Clone the repository

```bash
git clone <repository-url>
cd fintech-realtime-processing
```

## 2. Start the infrastructure

```bash
docker-compose up -d
```

This starts:

* PostgreSQL
* MongoDB
* Redis
* RabbitMQ
* Kafka
* Zookeeper
* Kafka UI

---

## 3. Start Eureka Server

```bash
cd eureka-server
mvn spring-boot:run
```

Open:

```text
http://localhost:8761
```

---

## 4. Start the microservices

Run the following services in order:

1. `user-service`
2. `transaction-service`
3. `fraud-detection-service`
4. `account-service`
5. `notification-service`
6. `reporting-service`
7. `api-gateway`

Example:

```bash
cd transaction-service
mvn spring-boot:run
```

---

# Monitoring

Kafka UI:

```text
http://localhost:9090
```

Eureka Dashboard:

```text
http://localhost:8761
```

API Gateway:

```text
http://localhost:8080
```

---

# Future Improvements

* Distributed transaction management with Saga Pattern
* Outbox Pattern support
* Dead Letter Queue topics
* Centralized logging with ELK or Grafana
* OpenTelemetry and tracing
* Resilience4j circuit breaker and retry
* Kubernetes deployment
* AI-based fraud detection model

---

# Example Resume Description

Developed a real-time fintech transaction processing platform using Spring Boot microservices, Apache Kafka, PostgreSQL, MongoDB, Redis, RabbitMQ, and Docker. Designed an event-driven Kafka topic pipeline for fraud detection, balance processing, notifications, and transaction archiving.

