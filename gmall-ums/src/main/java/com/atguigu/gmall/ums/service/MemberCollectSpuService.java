package com.atguigu.gmall.ums.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.ums.entity.MemberCollectSpuEntity;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.QueryCondition;


/**
 * 会员收藏的商品
 *
 * @author lixianfeng
 * @email lxf@atguigu.com
 * @date 2021-06-11 18:31:46
 */
public interface MemberCollectSpuService extends IService<MemberCollectSpuEntity> {

    PageVo queryPage(QueryCondition params);
}

