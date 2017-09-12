package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.persistence.*;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Entity
public class WeChatUser {
    @Id @GeneratedValue
    public Long id;

    @Column(nullable = false)
    public String  openid;  // 用户的唯一标示
    public String  nickname; // 用户昵称
    public String  sex; // 用户的性别，值为1时是男性，值为2时是女性，值为0时是未知
    public String  province;
    public String  city;
    public String  country;
    public String  headimgurl; // 用户头像地址
    public String  privilege; // 用户特权信息，json 数组，如微信沃卡用户为（chinaunicom）
    @JsonIgnore
    @Column(nullable = false)
    public boolean isEnabled; // 是否解绑


    public WeChatUser(){

    }
    public static WeChatUser instance(String weChatUserJson) throws IOException{
        ObjectMapper objectMapper = new ObjectMapper();
        WeChatUser wxInfo = objectMapper.readValue(weChatUserJson,WeChatUser.class) ;
        wxInfo.isEnabled = true;
        return wxInfo;
    }
   public  void  copy(Map weChatUserMap) throws IOException{
       this.openid = (String) weChatUserMap.get("openid");
       this.nickname =(String) weChatUserMap.get("nickname");
       this.sex = (String)weChatUserMap.get("sex");
       this.province = (String)weChatUserMap.get("province");
       this.city = (String)weChatUserMap.get("city");
       this.country = (String)weChatUserMap.get("country");
       this.headimgurl = (String)weChatUserMap.get("headimgurl");
       this.privilege = (String)weChatUserMap.get("privilege");
       this.isEnabled = true;
   }
}
