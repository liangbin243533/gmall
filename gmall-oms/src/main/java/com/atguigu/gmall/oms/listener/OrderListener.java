package com.atguigu.gmall.oms.listener;

import com.atguigu.gmall.oms.dao.OrderDao;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.service.OrderService;
import com.atguigu.gmall.ums.vo.UserBoundsVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
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

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "ORDER-PAY-QUEUE", durable = "true"),
            exchange = @Exchange(value = "GMALL-ORDER-EXCHANGE", ignoreDeclarationExceptions = "true", type = ExchangeTypes.TOPIC),
            key = {"order.pay"}
    ))
    public void payOrder(String orderToken){

/*        if (this.orderService.successOrder(orderToken) == 1){
            // 如果订单支付成功，真正的减库存
            this.amqpTemplate.convertAndSend("ORDER-STOCK-EXCHANGE", "stock.minus", orderToken);
            // 给用户添加积分信息
            OrderEntity orderEntity = this.orderService.getOne(new QueryWrapper<OrderEntity>().eq("", orderToken));
            UserBoundsVO UserBoundsVO = new UserBoundsVO();
            UserBoundsVO.setIntegration(orderEntity.getIntegration());
            UserBoundsVO.setGrowth(orderEntity.getGrowth());
            this.amqpTemplate.convertAndSend("ORDER-USER-EXCHANGE", "bound.plus", UserBoundsVO);
        }*/
        // 更新订单状态
        if (this.orderDao.payOrder(orderToken) == 1) {
            // 减库存
            this.amqpTemplate.convertAndSend("GMALL-ORDER-EXCHANGE", "stock.minus", orderToken);

            // 加积分
            OrderEntity orderEntity = this.orderDao.selectOne(new QueryWrapper<OrderEntity>().eq("order_sn", orderToken));
            UserBoundsVO userBoundsVO = new UserBoundsVO();
            userBoundsVO.setIntegration(orderEntity.getIntegration());
            userBoundsVO.setGrowth(orderEntity.getGrowth());
            userBoundsVO.setMemberId(orderEntity.getMemberId());
            this.amqpTemplate.convertAndSend("GMALL-ORDER-EXCHANGE", "user.bounds", userBoundsVO);

        }
    }
}
