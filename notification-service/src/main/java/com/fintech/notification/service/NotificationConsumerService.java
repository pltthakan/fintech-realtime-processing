package com.fintech.notification.service;

import com.fintech.common.event.NotificationEvent;
import com.fintech.common.event.RabbitConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ'dan gelen bildirimleri tüketir.
 * Gerçek uygulamada burada SMTP, SMS API, Firebase Push API çağrılır.
 */
@Slf4j
@Service
public class NotificationConsumerService {

    @RabbitListener(queues = RabbitConstants.EMAIL_QUEUE)
    public void handleEmailNotification(NotificationEvent event) {
        log.info("📧 EMAIL gönderildi - userId: {}, txId: {}, konu: {}",
                event.getUserId(), event.getTransactionId(), event.getSubject());
        log.info("   → Mesaj: {}", event.getMessage());
        // Gerçek uygulamada: JavaMailSender ile email gönderimi
    }

    @RabbitListener(queues = RabbitConstants.SMS_QUEUE)
    public void handleSmsNotification(NotificationEvent event) {
        log.info("📱 SMS gönderildi - userId: {}, txId: {}",
                event.getUserId(), event.getTransactionId());
        log.info("   → Mesaj: {}", event.getMessage());
        // Gerçek uygulamada: Twilio, Netgsm gibi SMS API çağrısı
    }

    @RabbitListener(queues = RabbitConstants.PUSH_QUEUE)
    public void handlePushNotification(NotificationEvent event) {
        log.info("🔔 PUSH bildirim gönderildi - userId: {}, txId: {}",
                event.getUserId(), event.getTransactionId());
        log.info("   → Mesaj: {}", event.getMessage());
        // Gerçek uygulamada: Firebase Cloud Messaging API çağrısı
    }
}
