package com.atguigu.gmall.cart.api;

import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.cart.pojo.Cart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

public interface GmallCartApi {

    @GetMapping("cart")
    public Resp<List<Cart>> queryCarts();

    @GetMapping("cart/{userId}")
    public Resp<List<Cart>> queryCheckedCartsByUserId(@PathVariable("userId")Long userId);
}
