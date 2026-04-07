package com.fintech.common.event;

/**
 * RabbitMQ exchange, queue ve routing key sabitleri.
 */
public final class RabbitConstants {

    private RabbitConstants() {}

    // Exchange
    public static final String NOTIFICATION_EXCHANGE = "fintech.notification.exchange";

    // Queues
    public static final String EMAIL_QUEUE = "fintech.notification.email";
    public static final String SMS_QUEUE = "fintech.notification.sms";
    public static final String PUSH_QUEUE = "fintech.notification.push";

    // Routing Keys
    public static final String EMAIL_ROUTING_KEY = "notification.email";
    public static final String SMS_ROUTING_KEY = "notification.sms";
    public static final String PUSH_ROUTING_KEY = "notification.push";
}
