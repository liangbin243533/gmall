package com.atguigu.gmall.oms.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@EnableRabbit
@Configuration
public class RabbitMqConfig {

    /**
     * 延时队列
     * @return
     */
    @Bean("ORDER-TTL-QUEUE")
    public Queue ttlQueue(){
        Map<String, Object> map = new HashMap<>();
        map.put("x-dead-letter-exchange", "GMALL-ORDER-EXCHANGE");
        map.put("x-dead-letter-routing-key", "order.dead");
        map.put("x-message-ttl", 120000); // 仅仅用于测试，实际根据需求，通常30分钟或者15分钟
        return new Queue("ORDER-TTL-QUEUE", true, false, false, map);
    }

    /**
     * 延时队列绑定到交换机
     * rountingKey：order.create
     * @return
     */
    @Bean("ORDER-TTL-BINDING")
    public Binding ttlBinding(){

        return new Binding("ORDER-TTL-QUEUE",
                Binding.DestinationType.QUEUE,
                "GMALL-ORDER-EXCHANGE",
                "order.ttl",
                null);
    }
    /**
     * 死信队列
     * @return
     */
    @Bean("ORDER-DEAD-QUEUE")
    public Queue dlQueue(){
        return new Queue("ORDER-DEAD-QUEUE",
                true,
                false,
                false,
                null);
    }

    /**
     * 死信队列绑定到交换机
     * routingKey：order.close
     * @return
     */
    @Bean("ORDER-DEAD-BINDING")
    public Binding deadBinding(){
        return new Binding("ORDER-DEAD-QUEUE",
                Binding.DestinationType.QUEUE,
                "GMALL-ORDER-EXCHANGE",
                "order.dead",
                null);
    }

}
