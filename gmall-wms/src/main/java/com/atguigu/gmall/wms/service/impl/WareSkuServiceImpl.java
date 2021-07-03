package com.atguigu.gmall.wms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.wms.vo.SkuLockVO;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.Query;
import com.atguigu.core.bean.QueryCondition;

import com.atguigu.gmall.wms.dao.WareSkuDao;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.service.WareSkuService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuDao, WareSkuEntity>
        implements WareSkuService {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private WareSkuDao wareSkuDao;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AmqpTemplate amqpTemplate;

    private static final String KEY_PREFIX = "stock:lock";

    @Override
    public PageVo queryPage(QueryCondition params) {
        IPage<WareSkuEntity> page = this.page(
                new Query<WareSkuEntity>().getPage(params),
                new QueryWrapper<WareSkuEntity>()
        );

        return new PageVo(page);
    }

    @Transactional
    @Override
    public String checkAndLockStock(List<SkuLockVO> skuLockVOS) {

        skuLockVOS.forEach(skuLockVO -> {
            // 检验并锁定库存
            lockStore(skuLockVO);
        });
        List<SkuLockVO> unLockSku = skuLockVOS.stream().filter(skuLockVO -> skuLockVO.getLock() == false).collect(Collectors.toList());
        // 如果没有锁住的集合为空
        if (!CollectionUtils.isEmpty(unLockSku)) {
            // 解锁已经锁定的商品库存
            List<SkuLockVO> lockSku = skuLockVOS.stream().filter(SkuLockVO::getLock).collect(Collectors.toList());
            lockSku.forEach(skuLockVO -> {
                this.wareSkuDao.unLockStore(skuLockVO.getWareSkuId(), skuLockVO.getCount());
            });

            // 提示锁定失败的商品
            List<Long> skuIds = unLockSku.stream().map(SkuLockVO::getSkuId).collect(Collectors.toList());
            return "下单失败,库存不足" + skuIds.toString();
        }
        String orderToken = skuLockVOS.get(0).getOrderToken();
        this.redisTemplate.opsForValue().set(KEY_PREFIX + orderToken, JSON.toJSONString(skuLockVOS));

        // 锁定库存成功，发送延时消息，定时解锁库存
        this.amqpTemplate.convertAndSend("GMALL-ORDER-EXCHANGE", "stock.ttl", orderToken);

        return null;
    }

    private void lockStore(SkuLockVO skuLockVO) {
        RLock lock = this.redissonClient.getLock("stock" + skuLockVO.getSkuId());
        lock.lock();

        // 查询库存是否够
        List<WareSkuEntity> wareSkuEntities = this.wareSkuDao.checkStore(skuLockVO.getSkuId(),
                skuLockVO.getCount());
        if (!CollectionUtils.isEmpty(wareSkuEntities)) {
            // 拿到第一个仓库锁库存
            Long id = wareSkuEntities.get(0).getId();
            wareSkuDao.lockStore(id, skuLockVO.getCount());
            skuLockVO.setWareSkuId(id);
            skuLockVO.setLock(true);

        } else {
            skuLockVO.setLock(false);
        }
        lock.unlock();
    }

}