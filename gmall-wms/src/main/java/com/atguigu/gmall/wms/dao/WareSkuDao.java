package com.atguigu.gmall.wms.dao;

import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * εεεΊε­
 * 
 * @author lixianfeng
 * @email lxf@atguigu.com
 * @date 2021-06-11 18:42:42
 */
@Mapper
public interface WareSkuDao extends BaseMapper<WareSkuEntity> {

    List<WareSkuEntity> checkStore(@Param("skuId") Long skuId, @Param("count") Integer count);

    int lockStore(@Param("id") Long id, @Param("count")Integer count);

    int unLockStore(@Param("wareSkuId")Long wareSkuId, @Param("count")Integer count);

    int minusStore(@Param("wareSkuId")Long wareSkuId, @Param("count")Integer count);
}
