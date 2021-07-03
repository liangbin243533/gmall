package com.atguigu.gmall.wms.config;

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
    @Bean("WMS-TTL-QUEUE")
    public Queue ttlQueue(){
        Map<String, Object> map = new HashMap<>();
        map.put("x-dead-letter-exchange", "GMALL-ORDER-EXCHANGE");
        map.put("x-dead-letter-routing-key", "stock.unlock");
        map.put("x-message-ttl", 90000); // 仅仅用于测试，实际根据需求，通常30分钟或者15分钟
        return new Queue("WMS-TTL-QUEUE", true, false, false, map);
    }

    /**
     * 延时队列绑定到交换机
     * rountingKey：order.create
     * @return
     */
    @Bean("WMS-TTL-BINDING")
    public Binding ttlBinding(){

        return new Binding("WMS-TTL-QUEUE",
                Binding.DestinationType.QUEUE,
                "GMALL-ORDER-EXCHANGE",
                "stock.ttl",
                null);
    }

}
