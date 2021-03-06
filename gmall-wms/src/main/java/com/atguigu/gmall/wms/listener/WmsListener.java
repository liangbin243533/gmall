package com.atguigu.gmall.wms.listener;

import com.alibaba.fastjson.JSON;
import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.wms.dao.WareSkuDao;
import com.atguigu.gmall.wms.vo.SkuLockVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class WmsListener {
    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "stock:lock";

    @Autowired
    private WareSkuDao wareSkuDao;

    @RabbitListener(bindings = @QueueBinding(value = @Queue(value = "WMS-UNLOCK-QUEUE", durable = "true"),
                    exchange = @Exchange(value = "GMALL-ORDER-EXCHANGE", type = ExchangeTypes.TOPIC, ignoreDeclarationExceptions = "true"),
            key = {"stock.unlock"}
    ))
    public void unlockListener(String orderToken) {

        String lockJson = this.redisTemplate.opsForValue().get(KEY_PREFIX + orderToken);
        // 不为空，才解锁库存
        if (StringUtils.isEmpty(lockJson)) {
            return;
        }
        List<SkuLockVO> skuLockVOS = JSON.parseArray(lockJson, SkuLockVO.class);

        skuLockVOS.forEach(skuLockVO -> {
            this.wareSkuDao.unLockStore(skuLockVO.getWareSkuId(), skuLockVO.getCount());
        });

        // 解锁完库存，删除，才不会重复解锁库存
        this.redisTemplate.delete("KEY_PREFIX + orderToken");

    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "WMS-MINUS-QUEUE", durable = "true"),
            exchange = @Exchange(value = "GMALL-ORDER-EXCHANGE", type = ExchangeTypes.TOPIC, ignoreDeclarationExceptions = "true"),
            key = {"stock.minus"}
    ))
    public void minusStoreListener(String orderToken) {

        String lockJson = this.redisTemplate.opsForValue().get(KEY_PREFIX + orderToken);
        List<SkuLockVO> skuLockVOS = JSON.parseArray(lockJson, SkuLockVO.class);

        skuLockVOS.forEach(skuLockVO -> {
            this.wareSkuDao.minusStore(skuLockVO.getWareSkuId(), skuLockVO.getCount());
        });

    }

}
