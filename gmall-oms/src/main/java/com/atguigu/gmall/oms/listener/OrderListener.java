package com.atguigu.gmall.oms.listener;

import com.atguigu.gmall.oms.dao.OrderDao;
import com.atguigu.gmall.oms.service.OrderService;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OrderListener {
    @Autowired
    private OrderDao orderDao;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @RabbitListener(queues = {"ORDER-DEAD-QUEUE"})
    public void closeOrder(String orderToken)  {

        // 关单
        int status = this.orderDao.closeOrder(orderToken);
        if (status == 1) {
            // 如果关单成功，发送消息给库存系统，释放库存
            this.amqpTemplate.convertAndSend("GMALL-ORDER-EXCHANGE", "stock.unlock", orderToken);
        }
        // 如果关单失败，说明订单可能已被关闭，直接确认消息
        // 手动ACK
        /*channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);*/
    }
}
