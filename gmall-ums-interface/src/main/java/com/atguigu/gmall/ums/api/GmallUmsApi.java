package com.atguigu.gmall.ums.api;

import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.ums.entity.MemberEntity;
import com.atguigu.gmall.ums.entity.MemberReceiveAddressEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

public interface GmallUmsApi {

    @GetMapping("ums/member/query")
    public Resp<MemberEntity> queryUser(
            @RequestParam("username") String username,
            @RequestParam("password") String password);

    /**
     * 根据用户id查询收货地址
     * @param userId
     * @return
     */
    @GetMapping("ums/memberreceiveaddress/{userId}")
    public Resp<List<MemberReceiveAddressEntity>> queryAddressesByUserId(@PathVariable("userId")Long userId);

    /**
     * 根据id查询用户信息
     * @param id
     * @return
     */
    @GetMapping("ums/member/info/{id}")
    public Resp<MemberEntity> queryMemberById(@PathVariable("id") Long id);
}
