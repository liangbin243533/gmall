package com.atguigu.gmall.index.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.index.annotation.GmallCache;
import com.atguigu.gmall.index.config.RedissonConfig;
import com.atguigu.gmall.index.feign.GmallPmsClient;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.vo.CategoryVO;
import org.apache.commons.lang.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "index:cates";

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private GmallPmsClient pmsClient;

    public List<CategoryEntity> queryLevel1Categories() {
        Resp<List<CategoryEntity>> listResp = this.pmsClient.queryCategoriesByPidOrLevel(1, null);
        return listResp.getData();
    };

    @GmallCache(prefix = "index:cates:", timeout = 7200, random = 100)
    public List<CategoryVO> querySubCategories(Long pid) {

        //Determine if there is data in cache
        /*String cateJson = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (!StringUtils.isEmpty(cateJson)) {
            return JSON.parseArray(cateJson, CategoryVO.class);
        }
        RLock lock = this.redissonClient.getLock("lock" + pid);
        lock.lock();

        String cateJson2 = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);
        if (!StringUtils.isEmpty(cateJson2)) {
            lock.unlock();
            return JSON.parseArray(cateJson2, CategoryVO.class);
        }*/

        //query in DB
        Resp<List<CategoryVO>> listResp = this.pmsClient.querySubCategories(pid);
        List<CategoryVO> categoryVOS = listResp.getData();

        //put data into cache
        /*this.redisTemplate.opsForValue().set(KEY_PREFIX + pid,
                JSON.toJSONString(categoryVOS),
                7 + new Random().nextInt(5),
                TimeUnit.DAYS);

        lock.unlock();*/

        return listResp.getData();
    }

}
