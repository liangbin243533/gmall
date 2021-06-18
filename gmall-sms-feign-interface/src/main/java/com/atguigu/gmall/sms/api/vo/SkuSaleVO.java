package com.atguigu.gmall.sms.api.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SkuSaleVO {

    private Long skuId;
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

}
