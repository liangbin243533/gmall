package com.atguigu.gmall.cart.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.core.bean.Resp;
import com.atguigu.core.bean.UserInfo;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptors.LoginInterceptor;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.pms.entity.SkuInfoEntity;
import com.atguigu.gmall.pms.entity.SkuSaleAttrValueEntity;
import com.atguigu.gmall.sms.api.vo.SaleVO;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String CART_PREFIX = "gmall:cart:";

    private static final String PRICE_PREFIX = "gmall:sku:";

    public void addCart(Cart cart) {

        // 获取userInfo
        UserInfo userInfo = LoginInterceptor.getUserInfo();

        // 获取redis的key
        String key = CART_PREFIX;
        if (userInfo.getId() == null) {
            key += userInfo.getUserKey();
        } else {
            key += userInfo.getId();
        }

        // 查询用户购物车
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);
        // 判断购物车是否存在
        Long skuId = cart.getSkuId();
        Integer count = cart.getCount();
        // 注意这里的skuId要转化成String，因为redis中保存的都是String
        if (hashOps.hasKey(skuId.toString())){
            // 购物车已存在该记录，更新数量
            String cartJson = hashOps.get(skuId.toString()).toString();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(cart.getCount() + count);
        } else {
            // 购物车不存在该记录，新增记录
            Resp<SkuInfoEntity> skuResp = this.pmsClient.querySkuById(skuId);
            SkuInfoEntity skuInfoEntity = skuResp.getData();
            cart.setCount(count);
            cart.setDefaultImage(skuInfoEntity.getSkuDefaultImg());
            cart.setPrice(skuInfoEntity.getPrice());
            cart.setTitle(skuInfoEntity.getSkuTitle());
            Resp<List<SkuSaleAttrValueEntity>> saleAttrValueResp = this.pmsClient.querySkuSaleAttrValueBySkuId(skuId);
            cart.setSkuAttrValue(saleAttrValueResp.getData());
            // 营销信息 TODO
            Resp<List<SaleVO>> saleResp = this.smsClient.querySalesBySkuId(skuId);
            cart.setSales(saleResp.getData());
            Resp<List<WareSkuEntity>> wareResp = this.wmsClient.queryWareSkusBySkuId(skuId);
            List<WareSkuEntity> wareSkuEntities = wareResp.getData();
            if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                cart.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() > 0));
            }
            // restore current price when adding cart
            this.redisTemplate.opsForValue().set(PRICE_PREFIX + skuId, skuInfoEntity.getPrice().toString());

        }
        // 将购物车记录写入redis
        hashOps.put(skuId.toString(), JSON.toJSONString(cart));
    }

    public List<Cart> queryCarts() {
        UserInfo userInfo = LoginInterceptor.getUserInfo();

        // 查询未登录购物车
        List<Cart> userKeyCarts = null;
        String userKey = CART_PREFIX + userInfo.getUserKey();
        BoundHashOperations<String, Object, Object> userKeyOps = this.redisTemplate.boundHashOps(userKey);
        List<Object> cartJsonList = userKeyOps.values();
        if (!CollectionUtils.isEmpty(cartJsonList)) {
            userKeyCarts = cartJsonList.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                //查询当前价格,并更新最新的价格到购物车
                String priceString = this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId());
                cart.setCurrentPrice(new BigDecimal(priceString));
                return cart;
            }).collect(Collectors.toList());
        }

        // 判断用户是否登录，未登录直接返回
        if (userInfo.getId() == null) {
            return userKeyCarts;
        }

        // 用户已登录，查询登录状态的购物车
        String key = CART_PREFIX + userInfo.getId();
        BoundHashOperations<String, Object, Object> userIdOps = this.redisTemplate.boundHashOps(key); // 获取登录状态的购物车

        // 如果未登录状态的购物车不为空，需要合并
        if (!CollectionUtils.isEmpty(userKeyCarts)) {
            // 合并购物车
            userKeyCarts.forEach(userKeyCart -> {
                Long skuId = userKeyCart.getSkuId();
                Integer count = userKeyCart.getCount();
                if (userIdOps.hasKey(skuId.toString())) {
                    // 购物车已存在该记录，更新数量
                    String cartJson = userIdOps.get(skuId.toString()).toString();
                    userKeyCart = JSON.parseObject(cartJson, Cart.class);
                    userKeyCart.setCount(userKeyCart.getCount() + count);
                }
                // 购物车不存在该记录，新增记录
                userIdOps.put(skuId.toString(), JSON.toJSONString(userKeyCart));
            });
            // 合并完成后，删除未登录的购物车
            this.redisTemplate.delete(userKey);
        }

        // 返回登录状态的购物车
        List<Object> userCartJsonList = userIdOps.values();
        if (!CollectionUtils.isEmpty(userCartJsonList)) {
            return userCartJsonList.stream().map(userCartJson -> {
                Cart cart = JSON.parseObject(userCartJson.toString(), Cart.class);
                //查询当前价格,并更新最新的价格到购物车
                String priceString = this.redisTemplate.opsForValue().get(PRICE_PREFIX + cart.getSkuId());
                cart.setCurrentPrice(new BigDecimal(priceString));
                return cart;
            }).collect(Collectors.toList());
        }

        return null;
    }

    public void updateCart(Cart cart) {

        // 获取登陆信息
        UserInfo userInfo = LoginInterceptor.getUserInfo();

        // 获取redis的key
        String key = CART_PREFIX;
        if (userInfo.getId() == null) {
            key += userInfo.getUserKey();
        } else {
            key += userInfo.getId();
        }

        // 获取hash操作对象
        BoundHashOperations<String, Object, Object> hashOperations = this.redisTemplate.boundHashOps(key);
        String skuId = cart.getSkuId().toString();
        if (hashOperations.hasKey(skuId)) {
            // 获取购物车信息
            String cartJson = hashOperations.get(skuId).toString();
            Integer count = cart.getCount();
            cart = JSON.parseObject(cartJson, Cart.class);
            // 更新数量
            cart.setCount(count);
            // 写入购物车
            hashOperations.put(cart.getSkuId().toString(), JSON.toJSONString(cart));
        }
    }
    public void deleteCart(Long skuId) {
        // 获取登陆信息
        UserInfo userInfo = LoginInterceptor.getUserInfo();

        // 获取redis的key
        String key = CART_PREFIX;
        if (userInfo.getId() == null) {
            key += userInfo.getUserKey();
        } else {
            key += userInfo.getId();
        }
        BoundHashOperations<String, Object, Object> hashOperations = this.redisTemplate.boundHashOps(key);
        hashOperations.delete(skuId.toString());
    }

    public List<Cart> queryCheckedCartsByUserId(Long userId) {
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(CART_PREFIX + userId);
        List<Object> cartJsonList = hashOps.values();
        List<Cart> collect = cartJsonList.stream()
                .map(cartJson -> JSON.parseObject(cartJson.toString(), Cart.class))
                .collect(Collectors.toList());
        return collect;
    }
}
