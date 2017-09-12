package models;

import javax.persistence.*;

@Entity
@NamedQuery(
        name = "findSMSCodeByTelephoneNumber",
        query = "SELECT wc FROM WeChatSMS wc where wc.telephoneNumber =:telephoneNumber"
)
public class WeChatSMS { //用于存储手机号和短信验证码
    @Id @GeneratedValue
    public Long id;
    @Column(nullable = false,unique = true)
    public String telephoneNumber;

    @Column(nullable = false)
    public String smsCode;

    public WeChatSMS(){

    }
    public WeChatSMS(String telephoneNumber,String smsCode){
        this.telephoneNumber = telephoneNumber;
        this.smsCode = smsCode;
    }
}
