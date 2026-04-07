// ============================================
// FINTECH REALTIME PROCESSING SYSTEM
// MongoDB Initialization Script
// ============================================

db = db.getSiblingDB('fintech_transactions');

// ============================================
// COMPLETED TRANSACTIONS COLLECTION
// Kafka Connect Sink bu collection'a yazar
// ============================================

db.createCollection('completed_transactions', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['transactionId', 'amount', 'currency', 'type', 'status', 'createdAt'],
      properties: {
        transactionId: { bsonType: 'string', description: 'UUID formatında işlem ID' },
        sourceAccountId: { bsonType: 'long', description: 'Gönderen hesap ID' },
        targetAccountId: { bsonType: 'long', description: 'Alıcı hesap ID' },
        sourceAccountNumber: { bsonType: 'string' },
        targetAccountNumber: { bsonType: 'string' },
        amount: { bsonType: 'double', minimum: 0 },
        currency: { enum: ['TRY', 'USD', 'EUR', 'GBP'] },
        type: { enum: ['TRANSFER', 'PAYMENT', 'DEPOSIT', 'WITHDRAWAL'] },
        status: { bsonType: 'string' },
        fraudScore: { bsonType: 'int', minimum: 0, maximum: 100 },
        description: { bsonType: 'string' },
        pipelineMetadata: {
          bsonType: 'object',
          properties: {
            rawTimestamp: { bsonType: 'date' },
            validatedTimestamp: { bsonType: 'date' },
            checkedTimestamp: { bsonType: 'date' },
            processedTimestamp: { bsonType: 'date' },
            completedTimestamp: { bsonType: 'date' },
            totalProcessingTimeMs: { bsonType: 'long' }
          }
        },
        createdAt: { bsonType: 'date' },
        completedAt: { bsonType: 'date' }
      }
    }
  }
});

// Indexler
db.completed_transactions.createIndex({ transactionId: 1 }, { unique: true });
db.completed_transactions.createIndex({ sourceAccountId: 1, createdAt: -1 });
db.completed_transactions.createIndex({ targetAccountId: 1, createdAt: -1 });
db.completed_transactions.createIndex({ createdAt: -1 });
db.completed_transactions.createIndex({ type: 1, createdAt: -1 });
db.completed_transactions.createIndex({ status: 1 });
db.completed_transactions.createIndex({ currency: 1, amount: -1 });

// ============================================
// DAILY REPORTS COLLECTION
// Raporlama servisi için aggregate sonuçlar
// ============================================

db.createCollection('daily_reports');
db.daily_reports.createIndex({ reportDate: 1 }, { unique: true });
db.daily_reports.createIndex({ createdAt: -1 });

// ============================================
// NOTIFICATION LOGS COLLECTION
// Gönderilen bildirimlerin kaydı
// ============================================

db.createCollection('notification_logs');
db.notification_logs.createIndex({ transactionId: 1 });
db.notification_logs.createIndex({ userId: 1, createdAt: -1 });
db.notification_logs.createIndex({ type: 1 });
db.notification_logs.createIndex({ createdAt: -1 }, { expireAfterSeconds: 7776000 }); // 90 gün TTL

print('MongoDB initialization completed successfully!');
