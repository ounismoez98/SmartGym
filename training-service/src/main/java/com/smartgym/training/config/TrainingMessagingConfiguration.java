package com.smartgym.training.config;

import java.util.HashMap;
import java.util.Map;

import com.smartgym.training.application.AiJobService;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.ai.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class TrainingMessagingConfiguration {
    public static final String COMMAND_QUEUE = "training.programme-generation.worker.v1";
    public static final String RETRY_QUEUE = "training.programme-generation.retry.v1";
    public static final String DEAD_QUEUE = "training.programme-generation.dead.v1";
    public static final String RESULT_QUEUE = "training.programme-generation.results.v1";
    public static final String RETRY_ROUTING_KEY = "training.programme-generation.retry.v1";
    public static final String DEAD_ROUTING_KEY = "training.programme-generation.dead.v1";
    public static final String ATTEMPT_HEADER = "x-smartgym-attempt";
    public static final int MAX_DELIVERY_ATTEMPTS = 3;

    @Bean
    TopicExchange trainingExchange() {
        return new TopicExchange(AiJobService.EXCHANGE, true, false);
    }

    @Bean
    DirectExchange trainingDeadLetterExchange() {
        return new DirectExchange(AiJobService.EXCHANGE + ".dlx", true, false);
    }

    @Bean
    Queue programmeGenerationCommandQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", AiJobService.EXCHANGE + ".dlx");
        arguments.put("x-dead-letter-routing-key", DEAD_ROUTING_KEY);
        return new Queue(COMMAND_QUEUE, true, false, false, arguments);
    }

    @Bean
    Queue programmeGenerationRetryQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-message-ttl", 5000);
        arguments.put("x-dead-letter-exchange", AiJobService.EXCHANGE);
        arguments.put("x-dead-letter-routing-key", AiJobService.REQUESTED_ROUTING_KEY);
        return new Queue(RETRY_QUEUE, true, false, false, arguments);
    }

    @Bean
    Queue programmeGenerationDeadQueue() {
        return new Queue(DEAD_QUEUE, true);
    }

    @Bean
    Queue programmeGenerationResultQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", AiJobService.EXCHANGE + ".dlx");
        arguments.put("x-dead-letter-routing-key", DEAD_ROUTING_KEY);
        return new Queue(RESULT_QUEUE, true, false, false, arguments);
    }

    @Bean
    Binding commandBinding(Queue programmeGenerationCommandQueue, TopicExchange trainingExchange) {
        return BindingBuilder.bind(programmeGenerationCommandQueue)
                .to(trainingExchange)
                .with(AiJobService.REQUESTED_ROUTING_KEY);
    }

    @Bean
    Binding resultCompletedBinding(Queue programmeGenerationResultQueue, TopicExchange trainingExchange) {
        return BindingBuilder.bind(programmeGenerationResultQueue)
                .to(trainingExchange)
                .with(AiJobService.COMPLETED_ROUTING_KEY);
    }

    @Bean
    Binding resultFailedBinding(Queue programmeGenerationResultQueue, TopicExchange trainingExchange) {
        return BindingBuilder.bind(programmeGenerationResultQueue)
                .to(trainingExchange)
                .with(AiJobService.FAILED_ROUTING_KEY);
    }

    @Bean
    Binding deadLetterBinding(Queue programmeGenerationDeadQueue, DirectExchange trainingDeadLetterExchange) {
        return BindingBuilder.bind(programmeGenerationDeadQueue)
                .to(trainingDeadLetterExchange)
                .with(DEAD_ROUTING_KEY);
    }

    @Bean
    SimpleRabbitListenerContainerFactory manualAckRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(1);
        return factory;
    }
}
