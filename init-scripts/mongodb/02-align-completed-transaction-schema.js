// Completed transaction eventleri Kafka Connect tarafından doğrudan MongoDB'ye yazılır.
// Event şemasındaki tarih alanı completedTimestamp olduğundan koleksiyon doğrulaması buna göre hizalanır.

db = db.getSiblingDB('fintech_transactions');

db.runCommand({
  collMod: 'completed_transactions',
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['transactionId', 'amount', 'currency', 'type', 'status', 'completedTimestamp'],
      properties: {
        transactionId: { bsonType: 'string' },
        currency: { enum: ['TRY', 'USD', 'EUR', 'GBP'] },
        type: { enum: ['TRANSFER', 'PAYMENT', 'DEPOSIT', 'WITHDRAWAL'] },
        status: { bsonType: 'string' },
        completedTimestamp: { bsonType: ['date', 'string'] }
      }
    }
  },
  validationLevel: 'moderate',
  validationAction: 'error'
});

db.completed_transactions.createIndex({ transactionId: 1 }, { unique: true });

print('Completed transaction schema aligned with Kafka events.');
