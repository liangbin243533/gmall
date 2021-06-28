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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class ItemService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    public ItemVO queryItemVO(Long skuId) {

        ItemVO itemVO = new ItemVO();

        itemVO.setSkuId(skuId);

        //query sku base on skuId
        CompletableFuture<Object> skuCompletableFuture = CompletableFuture.supplyAsync(() -> {
            Resp<SkuInfoEntity> skuResp = this.pmsClient.querySkuById(skuId);
            SkuInfoEntity skuInfoEntity = skuResp.getData();
            if (skuInfoEntity == null) {
                return itemVO;
            }

            itemVO.setSkuTitle(skuInfoEntity.getSkuTitle());
            itemVO.setSubTitle(skuInfoEntity.getSkuSubtitle());
            itemVO.setPrice(skuInfoEntity.getPrice());
            itemVO.setWeight(skuInfoEntity.getWeight());
            itemVO.setSpuId(skuInfoEntity.getSpuId());
            //Getting spuId in sku
            return skuInfoEntity;
        }, threadPoolExecutor);

        //query spu base on spuId in sku
        CompletableFuture<Void> spuCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
            Resp<SpuInfoEntity> spuResp = this.pmsClient.querySpuById(((SkuInfoEntity) sku).getSpuId());
            SpuInfoEntity spuInfoEntity = spuResp.getData();

            if (spuInfoEntity != null) {
                itemVO.setSpuName(spuInfoEntity.getSpuName());
            }
        }, threadPoolExecutor);


        //query picture list base on skuId
        CompletableFuture<Void> imageCompletableFuture = CompletableFuture.runAsync(() -> {
            Resp<List<SkuImagesEntity>> skuImagesResp = this.pmsClient.querySkuImagesBySkuId(skuId);
            List<SkuImagesEntity> skuImagesEntities = skuImagesResp.getData();
            itemVO.setPics(skuImagesEntities);
        }, threadPoolExecutor);

        //query brand base on Id
        CompletableFuture<Void> brandCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
            Resp<BrandEntity> brandEntityResp = this.pmsClient.queryBrandById(((SkuInfoEntity) sku).getBrandId());
            BrandEntity brandEntity = brandEntityResp.getData();
            itemVO.setBrandEntity(brandEntity);
        }, threadPoolExecutor);

        //query category base on Id
        CompletableFuture<Void> catCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
            Resp<CategoryEntity> categoryEntityResp = this.pmsClient.queryCategoryById(((SkuInfoEntity) sku).getCatalogId());
            CategoryEntity categoryEntity = categoryEntityResp.getData();
            itemVO.setCategoryEntity(categoryEntity);
        }, threadPoolExecutor);

        //query sale info base on skuId
         CompletableFuture<Void> saleCompletableFuture = CompletableFuture.runAsync(() -> {
            Resp<List<SaleVO>> salesResp = this.smsClient.querySalesBySkuId(skuId);
            List<SaleVO> saleVOList = salesResp.getData();
            itemVO.setSales(saleVOList);
        }, threadPoolExecutor);

        //query store info
        CompletableFuture<Void> storeCompletableFuture = CompletableFuture.runAsync(() -> {
            Resp<List<WareSkuEntity>> wareResp = this.wmsClient.queryWareSkusBySkuId(skuId);
            List<WareSkuEntity> wareSkuEntities = wareResp.getData();
            itemVO.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() > 0));
        }, threadPoolExecutor);


        //query all sale attribute base on skuIds
        CompletableFuture<Void> saleAttrCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
            Resp<List<SkuSaleAttrValueEntity>> saleAttrValueResp = this.pmsClient
                    .querySkuSaleAttrValuesBySpuId(((SkuInfoEntity) sku).getSpuId());
            List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = saleAttrValueResp.getData();
            itemVO.setSaleAttrs(skuSaleAttrValueEntities);
        }, threadPoolExecutor);

        //query poster info
        CompletableFuture<Void> descCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
            Resp<SpuInfoDescEntity> spuInfoDescEntityResp = this.pmsClient.querySpuDescBySpuId(((SkuInfoEntity) sku).getSpuId());
            SpuInfoDescEntity descEntity = spuInfoDescEntityResp.getData();
            if (descEntity != null) {
                String decript = descEntity.getDecript();
                String[] split = StringUtils.split(decript, ",");
                itemVO.setImages(Arrays.asList(split));
            }
        }, threadPoolExecutor);


        //query group and attributes base on cat_id and spu_id
        CompletableFuture<Void> groupCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
            Resp<List<ItemGroupVO>> itemGroupResp = this.pmsClient
                    .queryItemGroupVOByCidAndSpuId(((SkuInfoEntity) sku).getCatalogId(), ((SkuInfoEntity) sku).getSpuId());
            List<ItemGroupVO> itemGroupVOS = itemGroupResp.getData();
            itemVO.setGroups(itemGroupVOS);
        }, threadPoolExecutor);

        CompletableFuture.allOf(spuCompletableFuture,
                imageCompletableFuture,
                brandCompletableFuture,
                catCompletableFuture,
                saleAttrCompletableFuture,
                saleCompletableFuture,
                storeCompletableFuture,
                descCompletableFuture,
                groupCompletableFuture
        ).join();
        return itemVO;
    }
}
