package com.atguigu.gmall.pms.vo;

import com.atguigu.gmall.pms.entity.SkuInfoEntity;
import com.atguigu.gmall.pms.entity.SkuSaleAttrValueEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SkuInfoVO extends SkuInfoEntity {

    //Fields in SkuBounds
    private BigDecimal growBounds;
    private BigDecimal buyBounds;
    private List<Integer> work;

    //Discount fields
    private Integer fullCount;
    private BigDecimal discount;
    private Integer ladderAddOther;

    //Full reduction
    private BigDecimal fullPrice;
    private BigDecimal reducePrice;
    private Integer fullAddOther;

    //Sale properties and values
    private List<SkuSaleAttrValueEntity> saleAttrs;

    //sku images
    private List<String> images;
}
