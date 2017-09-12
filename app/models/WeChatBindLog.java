package models;

import javax.persistence.*;
import java.util.Date;

@Entity
public class WeChatBindLog { // 用来持久化微信WeCHatUser和Customer绑定以及解绑的记录
    @Id @GeneratedValue
    public Long id;

    @OneToOne
    public Customer customer;

    @OneToOne
    public WeChatUser weChatUser;

    public Date createTime; // 解绑或绑定的日期

    public WeChatBindLog(){

    }

    public WeChatBindLog(Customer customer,WeChatUser weChatUser){
        this.createTime = new Date();
        this.weChatUser = weChatUser;
        this.customer = customer;
    }
}
