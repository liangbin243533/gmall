package com.atguigu.gmall.order.controller;

import com.alipay.api.AlipayApiException;
import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.order.pay.AlipayTemplate;
import com.atguigu.gmall.order.pay.PayAsyncVo;
import com.atguigu.gmall.order.pay.PayVo;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.order.vo.OrderConfirmVO;
import com.atguigu.gmall.oms.vo.OrderSubmitVO;
import com.atguigu.gmall.wms.vo.SkuLockVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("order")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @Autowired
    private AlipayTemplate alipayTemplate;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 购物车页面传递的数据结构如下：
     * {101: 3, 102: 1}
     * {skuId: count}
     * @return
     */
    @GetMapping("confirm")
    public Resp<OrderConfirmVO> confirm(){

        OrderConfirmVO orderConfirmVO = this.orderService.confirm();

        return Resp.ok(orderConfirmVO);
    }

    @PostMapping("submit")
    public Resp<Object> submit(@RequestBody OrderSubmitVO orderSubmitVO) {
        OrderEntity orderEntity = this.orderService.submit(orderSubmitVO);

        try {
            PayVo payVo = new PayVo();
            payVo.setOut_trade_no(orderEntity.getOrderSn());
            payVo.setTotal_amount("0.1");
            payVo.setSubject("谷粒商城收银台");
            payVo.setBody("在线支付");
            String form = this.alipayTemplate.pay(payVo);
            System.out.println(form);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return Resp.ok(null);
    }

    @RequestMapping("pay/success")
    public Resp<Object> paySuccess(PayAsyncVo payAsyncVo){

        /*this.orderService.paySuccess(payAsyncVo);*/
        amqpTemplate.convertAndSend("GMALL-ORDER-EXCHANGE", "order.pay", payAsyncVo.getOut_trade_no());

        /*return Resp.ok("支付成功！");*/
        return Resp.ok(null);
    }

    @PostMapping("seckill/{skuId}")
    public Resp<Object> kill(@PathVariable("skuId") Long skuId){

        RSemaphore semaphore = this.redissonClient.getSemaphore("semaphore:lock" + skuId);
        semaphore.trySetPermits(500);
        if (semaphore.tryAcquire()) {
            // 查询redis中的库存信息
            String countString = this.redisTemplate.opsForValue().get("order:seckill:" + skuId);

            // 没有，秒杀结束
            if (StringUtils.isEmpty(countString) || Integer.parseInt(countString) == 0) {
                return Resp.ok("秒杀结束");
            }

            // 减库存
            Integer count = Integer.parseInt(countString);
            this.redisTemplate.opsForValue().set("order:seckill:" + skuId, String.valueOf(--count));

            // 发送消息给队列，真正减库存
            SkuLockVO skuLockVO = new SkuLockVO();
            skuLockVO.setCount(1);
            skuLockVO.setSkuId(skuId);
            String orderToken = IdWorker.getIdStr();
            skuLockVO.setOrderToken(orderToken);
            this.amqpTemplate.convertAndSend("GMALL-ORDER-EXCHANGE", "order:seckill", skuLockVO);

            RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("count:down:" + orderToken);
            countDownLatch.trySetCount(1);
            countDownLatch.countDown();

            semaphore.release();

            //响应成功
            return Resp.ok("秒杀成功");
        }
        return Resp.ok("再接再厉");
    }
    @GetMapping("seckill/{orderToken}")
    public Resp<Object> querySeckill(@PathVariable("orderToken")String orderToken) throws InterruptedException {

        RCountDownLatch latch = this.redissonClient.getCountDownLatch("count:down:" + orderToken);

        latch.await();

        // 查询订单信息
        // 发送请求查询订单

        return Resp.ok(null);
    }
}
