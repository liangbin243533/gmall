package com.aguigu.gmall.search.pojo;

import lombok.Data;

@Data
public class SearchParam {
    //search?catelog3=1&catelog3=2&brand=1&props=43:3g-4g-5g&props=45:4.7-5.0&order=2:asc/desc&priceFrom=100&priceTo=10000&pageNum=1&pageSize=12&keyword=手机
    private String[] catelog3; //category3 Id
    private String[] brand; //brand Id
    private String keyword; //search keywords
    private String order;   //sort
    private Integer pageNum = 1;

    private String[] props;
    private Integer pageSize = 12;

    private Integer priceFrom;
    private Integer priceTo;

}
