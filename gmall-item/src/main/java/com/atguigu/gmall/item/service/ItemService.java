package com.atguigu.gmall.item.service;

import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.item.feign.GmallPmsClient;
import com.atguigu.gmall.item.feign.GmallSmsClient;
import com.atguigu.gmall.item.feign.GmallWmsClient;
import com.atguigu.gmall.item.vo.ItemVO;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.ItemGroupVO;
import com.atguigu.gmall.sms.api.vo.SaleVO;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class ItemService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    public ItemVO queryItemVO(Long skuId) {

        ItemVO itemVO = new ItemVO();

        itemVO.setSkuId(skuId);

        //query sku base on skuId
        Resp<SkuInfoEntity> skuResp = this.pmsClient.querySkuById(skuId);
        SkuInfoEntity skuInfoEntity = skuResp.getData();
        if (skuInfoEntity == null) {
            return itemVO;
        }

        itemVO.setSkuTitle(skuInfoEntity.getSkuTitle());
        itemVO.setSubTitle(skuInfoEntity.getSkuSubtitle());
        itemVO.setPrice(skuInfoEntity.getPrice());
        itemVO.setWeight(skuInfoEntity.getWeight());

        //Getting spuId in sku
        Long spuId = skuInfoEntity.getSpuId();
        //query spu base on spuId in sku
        Resp<SpuInfoEntity> spuResp = this.pmsClient.querySpuById(spuId);
        SpuInfoEntity spuInfoEntity = spuResp.getData();
        itemVO.setSpuId(spuId);
        if (spuInfoEntity != null) {
            itemVO.setSpuName(spuInfoEntity.getSpuName());
        }

        //query picture list base on skuId
        Resp<List<SkuImagesEntity>> skuImagesResp = this.pmsClient.querySkuImagesBySkuId(skuId);
        List<SkuImagesEntity> skuImagesEntities = skuImagesResp.getData();
        itemVO.setPics(skuImagesEntities);

        //query brand and category base on Id
        Resp<BrandEntity> brandEntityResp = this.pmsClient.queryBrandById(skuInfoEntity.getBrandId());
        BrandEntity brandEntity = brandEntityResp.getData();
        itemVO.setBrandEntity(brandEntity);

        Resp<CategoryEntity> categoryEntityResp = this.pmsClient.queryCategoryById(skuInfoEntity.getCatalogId());
        CategoryEntity categoryEntity = categoryEntityResp.getData();
        itemVO.setCategoryEntity(categoryEntity);

        //query sale info base on skuId
        Resp<List<SaleVO>> salesResp = this.smsClient.querySalesBySkuId(skuId);
        List<SaleVO> saleVOList = salesResp.getData();
        itemVO.setSales(saleVOList);

        //query store info
        Resp<List<WareSkuEntity>> wareResp = this.wmsClient.queryWareSkusBySkuId(skuId);
        List<WareSkuEntity> wareSkuEntities = wareResp.getData();
        itemVO.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() > 0));

        //query all sale attribute base on skuIds
        Resp<List<SkuSaleAttrValueEntity>> saleAttrValueResp = this.pmsClient.querySkuSaleAttrValuesBySpuId(spuId);
        List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = saleAttrValueResp.getData();
        itemVO.setSaleAttrs(skuSaleAttrValueEntities);

        //query poster info
        Resp<SpuInfoDescEntity> spuInfoDescEntityResp = this.pmsClient.querySpuDescBySpuId(spuId);
        SpuInfoDescEntity descEntity = spuInfoDescEntityResp.getData();
        if (descEntity != null) {
            String decript = descEntity.getDecript();
            String[] split = StringUtils.split(decript, ",");
            itemVO.setImages(Arrays.asList(split));
        }

        //query group and attributes base on cat_id and spu_id
        Resp<List<ItemGroupVO>> itemGroupResp = this.pmsClient.queryItemGroupVOByCidAndSpuId(skuInfoEntity.getCatalogId(), spuId);
        List<ItemGroupVO> itemGroupVOS = itemGroupResp.getData();
        itemVO.setGroups(itemGroupVOS);

        return itemVO;
    }
}
