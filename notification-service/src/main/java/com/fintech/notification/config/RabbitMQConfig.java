package com.fintech.notification.config;

import com.fintech.common.event.RabbitConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(RabbitConstants.NOTIFICATION_EXCHANGE);
    }

    // ── Email Queue ──
    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(RabbitConstants.EMAIL_QUEUE).build();
    }

    @Bean
    public Binding emailBinding(Queue emailQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(emailQueue).to(notificationExchange).with(RabbitConstants.EMAIL_ROUTING_KEY);
    }

    // ── SMS Queue ──
    @Bean
    public Queue smsQueue() {
        return QueueBuilder.durable(RabbitConstants.SMS_QUEUE).build();
    }

    @Bean
    public Binding smsBinding(Queue smsQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(smsQueue).to(notificationExchange).with(RabbitConstants.SMS_ROUTING_KEY);
    }

    // ── Push Queue ──
    @Bean
    public Queue pushQueue() {
        return QueueBuilder.durable(RabbitConstants.PUSH_QUEUE).build();
    }

    @Bean
    public Binding pushBinding(Queue pushQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(pushQueue).to(notificationExchange).with(RabbitConstants.PUSH_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
