package com.aguigu.gmall.search.controller;

import com.aguigu.gmall.search.pojo.SearchParam;
import com.aguigu.gmall.search.pojo.SearchResponseVO;
import com.aguigu.gmall.search.service.SearchService;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.Resp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("search")
public class SearchController {

    @Autowired
    private SearchService searchService;

    @GetMapping
    public Resp<SearchResponseVO> search(SearchParam searchParam) throws IOException {
        SearchResponseVO responseVO = this.searchService.search(searchParam);
        return Resp.ok(responseVO);
    }
}
