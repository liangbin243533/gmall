package com.atguigu.gmall.item.vo;

import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.entity.SkuImagesEntity;
import com.atguigu.gmall.pms.entity.SkuSaleAttrValueEntity;
import com.atguigu.gmall.pms.vo.ItemGroupVO;
import com.atguigu.gmall.sms.api.vo.SaleVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ItemVO {

    private Long skuId;

    private CategoryEntity categoryEntity;

    private BrandEntity brandEntity;

    private Long spuId;

    private String spuName;

    private String skuTitle;

    private String subTitle;

    private BigDecimal price;

    private BigDecimal weight;

    private List<SkuImagesEntity> pics;

    private List<SaleVO> sales;  //sale info

    private boolean store;  //If there is store

    private List<SkuSaleAttrValueEntity> saleAttrs; //sale attribute

    private List<String> images;  //sale poster

    private List<ItemGroupVO> groups;  //attribute group




}
