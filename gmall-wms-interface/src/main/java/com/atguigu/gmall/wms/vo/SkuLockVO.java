package com.atguigu.gmall.wms.vo;

import lombok.Data;

@Data
public class SkuLockVO {

    private Long skuId;

    private Integer count; // 锁定数量

    private Boolean lock;

    private Long wareSkuId; // wms_ware_sku表的主键,锁定库存的ID

    private String orderToken; // 那个订单（订单编号）
}
