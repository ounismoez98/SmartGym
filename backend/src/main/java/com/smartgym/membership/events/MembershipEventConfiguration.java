package com.smartgym.membership.events;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class MembershipEventConfiguration {
    @Bean
    TopicExchange gymEventsExchange() {
        return new TopicExchange("gym.events", true, false);
    }

    @Bean
    Jackson2JsonMessageConverter membershipEventMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
