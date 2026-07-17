# Fintech Realtime Processing Platform

A real-time fintech transaction processing platform built with Spring Boot microservices, Apache Kafka, PostgreSQL, MongoDB, Redis, RabbitMQ, Docker, and Spring Cloud.

The system processes financial transactions through an event-driven pipeline that includes validation, fraud detection, balance updates, notifications, and transaction archiving.

## Local configuration

Copy `.env.example` to `.env` and replace every password, `JWT_ACCESS_SECRET`, and `JWT_REFRESH_SECRET` before starting the stack. Access and refresh secrets must be different. `.env` is intentionally ignored by Git.

The Docker Compose defaults expose only the frontend, API gateway, and local-only operational interfaces. Internal microservices, databases, and brokers are reachable only on the Docker network.

For an existing PostgreSQL volume, apply schema updates with `./manage.sh migrate` before starting a version that requires a new table or column.



---

# Architecture Overview

<img width="514" height="763" alt="Ekran Resmi 2026-04-07 22 56 47" src="https://github.com/user-attachments/assets/fce92903-4ea0-4778-871d-c69f88955678" />




The project uses a Kafka topic pipeline where each microservice is responsible for one stage of the transaction lifecycle.

---

## UI Preview

<img width="1470" height="956" alt="Ekran Resmi 2026-04-13 00 42 18" src="https://github.com/user-attachments/assets/659756db-98ad-430f-ae2b-8f6a1eb04728" />


<img width="1470" height="956" alt="Ekran Resmi 2026-06-23 12 06 51" src="https://github.com/user-attachments/assets/e80a9188-6083-4e23-bf8c-df5f2ec3caae" />


<details>
<summary>See more screenshots</summary>

### Accounts
<img width="1470" height="956" alt="Ekran Resmi 2026-04-13 00 43 25" src="https://github.com/user-attachments/assets/449f2d3f-12b6-431e-b37d-30387803c94a" />


### Transactions
<img width="1470" height="956" alt="Ekran Resmi 2026-04-13 00 44 25" src="https://github.com/user-attachments/assets/cde9dbbf-5a59-4f94-b381-fdd6101cc97b" />


### New Transaction
<img width="1470" height="956" alt="Ekran Resmi 2026-04-13 00 47 26" src="https://github.com/user-attachments/assets/2801a10e-fd27-4c4d-831c-2099cebb0fa1" />

<img width="1470" height="956" alt="Ekran Resmi 2026-04-13 00 47 14" src="https://github.com/user-attachments/assets/71f95c25-d0d0-4e3c-8588-69eb103550db" />

### Administrator tasks
<img width="1470" height="956" alt="Ekran Resmi 2026-06-23 12 04 39" src="https://github.com/user-attachments/assets/4892d4ca-8fd6-47fb-9ae5-ee431647c366" />



<img width="1470" height="956" alt="Ekran Resmi 2026-06-23 12 06 41" src="https://github.com/user-attachments/assets/77171f6a-9e11-4f28-9a69-61ce7a17574b" />



<img width="1470" height="956" alt="Ekran Resmi 2026-06-23 12 05 09" src="https://github.com/user-attachments/assets/d14d5e92-68e8-44b9-8c63-07b929cb2a12" />



<img width="1470" height="956" alt="Ekran Resmi 2026-06-23 12 05 36" src="https://github.com/user-attachments/assets/6d9c9009-e7fe-4777-9968-de8fc9f02507" />


</details>

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
* Immutable audit trail for account and transaction access/changes
* Rotating refresh tokens with reuse detection and server-side revocation
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
* Validate access-token signature, issuer, audience, type, and required roles
* Apply Redis-backed request rate limiting

---

## User Service

Port: `8081`

Responsibilities:

* User registration and authentication
* Short-lived access JWT and rotating refresh-token families
* HttpOnly refresh cookie, hashed token persistence, reuse detection, and logout revocation
* Role-based access control

Database schema:

* `user_service`

### Access and refresh token security

Access and refresh tokens have separate responsibilities and signing secrets:

* Access tokens expire after 15 minutes by default and are the only tokens accepted by the API Gateway.
* Refresh tokens expire after 7 days by default and are sent only in an `HttpOnly`, `SameSite` cookie. They are never returned in the JSON body or stored in browser storage.
* JWT validation requires the expected signature, issuer, audience, `tokenType`, and expiration. Every generated token also carries a unique token ID (`jti`).
* Only a SHA-256 hash of each refresh token is stored in PostgreSQL.
* Every refresh rotates the token. Reusing an already rotated token revokes the complete token family, limiting damage from a stolen token.
* Logout revokes the current refresh-token family and clears the cookie.
* The frontend coalesces concurrent refresh attempts so parallel `401` responses do not race the one-time token rotation.

Use different, long random values for `JWT_ACCESS_SECRET` and `JWT_REFRESH_SECRET`. For HTTPS production deployments, set `AUTH_REFRESH_COOKIE_SECURE=true`. Existing PostgreSQL volumes must be upgraded before the new user-service version starts:

```bash
./manage.sh migrate
```

---

## Account Service

Port: `8082`

Responsibilities:

* Update account balances
* Process transfer operations
* Persist account and transaction information
* Store account/transaction audit records in `audit_service.audit_logs`

Consumes:

* `transaction-checked`

Produces:

* `transaction-processed`

Database schema:

* `account_service`

### Audit log API

`GET /api/v1/audit-logs?page=0&size=50` is routed through the API Gateway and is restricted to the `ADMIN` role. Optional `actorUsername`, `action`, and `resourceType` filters are supported. It returns the actor, time, action, account/transaction resource, service, and trusted client IP.

Successful account and transaction reads/creates are recorded automatically. Audit records are append-only at the application level; no update or delete API is exposed.

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
transaction-validated
    ↓
transaction-checked
    ↓
transaction-processed
    ↓
transaction-completed
```

Each topic represents a stage of the transaction lifecycle.

## Reliable event delivery

The money-movement path uses the Transactional Outbox and Consumer Inbox patterns:

* `transaction-service` stores the new transaction and its `transaction-raw` outbox event in the same PostgreSQL transaction.
* `account-service` atomically claims each transaction event in `processed_events`, updates balances, and writes the next outbox event.
* Duplicate Kafka deliveries are ignored by the `(consumer_name, event_id)` primary key, so a balance operation is applied once.
* Outbox publishers wait for Kafka acknowledgement before marking an event `PUBLISHED`. Failed sends stay `PENDING` and are retried.
* Account consumer failures are retried and then published to `transaction-dlq` instead of being swallowed.

Because outbox delivery is intentionally at-least-once, downstream consumers must also be idempotent when they perform non-repeatable side effects.

---

## Automated tests and CI

The account money-transfer path has Testcontainers integration coverage with real PostgreSQL 16 and Kafka containers. The tests verify that:

* a transfer debits and credits the correct accounts;
* duplicate Kafka delivery changes balances only once;
* the consumer inbox and outbox are committed with the balance update;
* the next `transaction-checked` event is published through Kafka;
* insufficient balance rolls back the database transaction and routes the event to `transaction-dlq`.

Docker must be running to execute the integration suite locally:

```bash
mvn -f common-library/pom.xml install
mvn -f account-service/pom.xml verify
```

The GitHub Actions workflow in `.github/workflows/ci.yml` runs all backend tests, the Testcontainers transfer scenarios, the frontend production build, and Docker Compose validation on every push and pull request. Test reports are uploaded as workflow artifacts even when a backend test fails.

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
* `refresh_tokens`
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

## System Monitoring & Infrastructure

<details>
<summary>View infrastructure dashboards</summary>

### Kafka UI
<img width="1470" height="956" alt="Ekran Resmi 2026-04-13 00 55 00" src="https://github.com/user-attachments/assets/9623d9ca-ddd3-48d2-b76e-c520da4240e4" />

### RabbitMQ
<img width="1201" height="751" alt="Ekran Resmi 2026-04-13 00 59 48" src="https://github.com/user-attachments/assets/999b64fa-38cb-405a-9dbe-95f5e105364f" />

### Eureka
<img width="1470" height="956" alt="Ekran Resmi 2026-04-13 00 54 31" src="https://github.com/user-attachments/assets/64d9d3d5-d682-4c43-8bf8-c3640595897b" />

</details>

---

# Future Improvements

* Distributed transaction management with Saga Pattern
* Extend Consumer Inbox idempotency to notification and reporting side effects
* Operational DLQ replay tooling
* Centralized logging with ELK or Grafana
* OpenTelemetry and tracing
* Resilience4j circuit breaker and retry
* Kubernetes deployment
* AI-based fraud detection model

---

# Example Resume Description

Developed a real-time fintech transaction processing platform using Spring Boot microservices, Apache Kafka, PostgreSQL, MongoDB, Redis, RabbitMQ, and Docker. Designed an event-driven Kafka topic pipeline for fraud detection, balance processing, notifications, and transaction archiving.
