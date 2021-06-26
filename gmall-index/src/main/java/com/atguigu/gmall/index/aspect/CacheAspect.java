package com.atguigu.gmall.index.aspect;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.index.annotation.GmallCache;
import com.atguigu.gmall.pms.vo.CategoryVO;
import org.apache.commons.lang.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Component
@Aspect
public class CacheAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Around("@annotation(com.atguigu.gmall.index.annotation.GmallCache)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {

        Object result = null;
        //Obtaining target method
        MethodSignature signature = (MethodSignature)joinPoint.getSignature();
        Method method = signature.getMethod();
        GmallCache gmallCache = method.getAnnotation(GmallCache.class);
        Class<?> returnType = method.getReturnType();
        String prefix = gmallCache.prefix();

        //Obtaining parameters of target method
        Object[] args = joinPoint.getArgs();
        String key = prefix + Arrays.asList(args).toString();

        //Query from Cache, If there is data in Cache, just return
        result = this.cacheHit(key, returnType);
        if (result != null) {
            return result;
        }

        //If there is no data, adding distributed lock
        RLock lock = this.redissonClient.getLock("lock" + Arrays.asList(args).toString());
        lock.lock();

        //Query cache again
        result = this.cacheHit(key, returnType);
        if (result != null) {
            lock.unlock();
            return result;
        }

        //if there is no data, just executing target method
        result = joinPoint.proceed(args);

        //Putting data into cache and release distributed lock
        int timeout = gmallCache.timeout();
        int random = gmallCache.random();
        this.redisTemplate.opsForValue().set(key,
                JSON.toJSONString(result),
                timeout + (int)(Math.random() * random),
                TimeUnit.MINUTES);
        lock.unlock();

        return result;
    }
    private Object cacheHit(String key, Class<?> returnType) {
        //Query from Cache, If there is data in Cache, just return
        String json = this.redisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(json)) {
            return JSON.parseObject(json, returnType);
        }
        return null;
    }
}
