package com.atguigu.gmall.order.service;

import com.atguigu.core.bean.Resp;
import com.atguigu.core.bean.UserInfo;
import com.atguigu.core.exception.OrderException;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.order.feign.*;
import com.atguigu.gmall.order.interceptors.LoginInterceptor;
import com.atguigu.gmall.order.vo.OrderConfirmVO;
import com.atguigu.gmall.oms.vo.OrderItemVO;
import com.atguigu.gmall.oms.vo.OrderSubmitVO;
import com.atguigu.gmall.pms.entity.SkuInfoEntity;
import com.atguigu.gmall.pms.entity.SkuSaleAttrValueEntity;
import com.atguigu.gmall.ums.entity.MemberEntity;
import com.atguigu.gmall.ums.entity.MemberReceiveAddressEntity;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.vo.SkuLockVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Service
public class OrderService {
    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallUmsClient umsClient;

    @Autowired
    private GmallCartClient cartClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallOmsClient omsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String TOKEN_PREFIX = "order:token:";

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    private AmqpTemplate amqpTemplate;

    public OrderConfirmVO confirm() {

        OrderConfirmVO orderConfirmVO = new OrderConfirmVO();

        // 获取用户信息
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getId();
        if (userId == null) {
            return null;
        }

        // 获取用户的收获地址列表
        CompletableFuture<Void> addressFuture = CompletableFuture.runAsync(() -> {
            Resp<List<MemberReceiveAddressEntity>> addressResp = this.umsClient.queryAddressesByUserId(userId);
            List<MemberReceiveAddressEntity> addresses = addressResp.getData();
            orderConfirmVO.setAddresses(addresses);
        }, threadPoolExecutor);
        // 获取订单的商品清单
        CompletableFuture<Void> bigSkuCompletableFuture = CompletableFuture.supplyAsync(() -> {
            Resp<List<Cart>> cartsResp = this.cartClient.queryCheckedCartsByUserId(userId);
            List<Cart> cartList = cartsResp.getData();
            if (CollectionUtils.isEmpty(cartList)) {
                new OrderException();
            }
            return cartList;
        }, threadPoolExecutor).thenAcceptAsync(cartList -> {
            List<OrderItemVO> itemVOS = cartList.stream().map(cart -> {
                OrderItemVO orderItemVO = new OrderItemVO();
                Long skuId = cart.getSkuId();

                // 获取购物车中选中的商品信息
                CompletableFuture<Void> skuCompletableFuture = CompletableFuture.runAsync(() -> {
                    Resp<SkuInfoEntity> skuInfoEntityResp = this.pmsClient.querySkuById(skuId);
                    SkuInfoEntity skuInfoEntity = skuInfoEntityResp.getData();
                    if (skuInfoEntity != null) {
                        orderItemVO.setWeight(skuInfoEntity.getWeight());
                        orderItemVO.setCount(cart.getCount());
                        orderItemVO.setDefaultImage(skuInfoEntity.getSkuDefaultImg());
                        orderItemVO.setPrice(skuInfoEntity.getPrice());
                        orderItemVO.setTitle(skuInfoEntity.getSkuTitle());
                        orderItemVO.setSkuId(skuId);
                    }
                }, threadPoolExecutor);

                // 查询sku的营销属性
                CompletableFuture<Void> saleAttrCompletableFuture = CompletableFuture.runAsync(() -> {
                    Resp<List<SkuSaleAttrValueEntity>> saleAttrValueResp = this.pmsClient.querySkuSaleAttrValueBySkuId(skuId);
                    List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = saleAttrValueResp.getData();
                    orderItemVO.setSaleAttrValues(skuSaleAttrValueEntities);
                }, threadPoolExecutor);


                // 查询库存信息
                CompletableFuture<Void> wareSkuCompletableFuture = CompletableFuture.runAsync(() -> {
                    Resp<List<WareSkuEntity>> wareSkuResp = this.wmsClient.queryWareSkusBySkuId(skuId);
                    List<WareSkuEntity> wareSkuEntities = wareSkuResp.getData();
                    orderItemVO.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() > 0));
                }, threadPoolExecutor);
                CompletableFuture.allOf(skuCompletableFuture, saleAttrCompletableFuture, wareSkuCompletableFuture).join();

                return orderItemVO;

            }).collect(Collectors.toList());
            orderConfirmVO.setOrderItems(itemVOS);
        }, threadPoolExecutor);

        // 获取用户可用积分信息
        CompletableFuture<Void> boundFuture = CompletableFuture.runAsync(() -> {
            Resp<MemberEntity> memberEntityResp = this.umsClient.queryMemberById(userId);
            MemberEntity memberEntity = memberEntityResp.getData();
            orderConfirmVO.setBounds(memberEntity.getIntegration());
        }, threadPoolExecutor);
        // 随机生成唯一令牌，防止重复提交
        CompletableFuture<Void> tokenFuture = CompletableFuture.runAsync(() -> {
            // 随机生成唯一令牌，防止重复提交
            String token = IdWorker.getTimeId(); // 分布式id生成器，timeId适合做订单号
            this.redisTemplate.opsForValue().set(TOKEN_PREFIX + token, token);
            orderConfirmVO.setOrderToken(token);
        }, threadPoolExecutor);

        CompletableFuture.allOf(bigSkuCompletableFuture, addressFuture, boundFuture, tokenFuture).join();

        return orderConfirmVO;
    }

    public void submit(OrderSubmitVO submitVO) {

        UserInfo userInfo = LoginInterceptor.getUserInfo();

        // 防止重复提交订单
        // 1.获取Token
        String orderToken = submitVO.getOrderToken();
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        Long flag = this.redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Arrays.asList(TOKEN_PREFIX + orderToken), orderToken);
        if (flag == 0) {
            throw new OrderException("订单不可重复提交");
        }
        // 校验价格,总价一致就放行
        List<OrderItemVO> items = submitVO.getItems(); // 送货清单
        BigDecimal totalPrice = submitVO.getTotalPrice();
        if (CollectionUtils.isEmpty(items)) {
            throw new OrderException("请勾选购买商品");
        }

        // 获取实时总价信息
        BigDecimal currentTotalPrice = items.stream().map(item -> {
            Resp<SkuInfoEntity> skuInfoEntityResp = this.pmsClient.querySkuById(item.getSkuId());
            SkuInfoEntity skuInfoEntity = skuInfoEntityResp.getData();
            if (skuInfoEntity != null) {
                return skuInfoEntity.getPrice().multiply(new BigDecimal(item.getCount()));
            }
            return new BigDecimal(0);
        }).reduce((a, b) -> a.add(b)).get();

        // 判断实时价格和页面价格是否一致
        if (currentTotalPrice.compareTo(totalPrice) != 0) {
            throw new OrderException("页面已经过期，请刷新页面后重新下单");
        }

        //校验库存并锁定库存，提示库存不够的商品信息
        List<SkuLockVO> lockVOS = items.stream().map(orderItemVO -> {
            SkuLockVO skuLockVO = new SkuLockVO();
            skuLockVO.setSkuId(orderItemVO.getSkuId());
            skuLockVO.setCount(orderItemVO.getCount());
            return skuLockVO;
        }).collect(Collectors.toList());
        Resp<Object> wareResp = this.wmsClient.checkAndLockStock(lockVOS);
        if (wareResp.getCode() != 0) {
            throw new OrderException(wareResp.getMsg());
        }

        //下单(创建订单及订单详情)
        try {
            submitVO.setUserId(userInfo.getId());
            Resp<OrderEntity> orderEntityResp = this.omsClient.saveOrder(submitVO);
        } catch (Exception e) {
            e.printStackTrace();
            throw new OrderException("创建订单失败");
        }


        //删除购物车(发送消息删除购物车)
        HashMap<String, Object> map = new HashMap<>();
        map.put("userId", userInfo.getId());
        List<Long> skuIds = items.stream().map(OrderItemVO::getSkuId).collect(Collectors.toList());
        map.put("skuIds", skuIds);

        this.amqpTemplate.convertAndSend("GMALL-ORDER-EXCHANGE","cart.delete", map);


    }
}