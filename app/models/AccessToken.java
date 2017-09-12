package models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.Date;

@Entity
@NamedQueries(
        @NamedQuery(
                name = "queryAccessToken",
                query = "SELECT ac FROM AccessToken ac"
        )
)
public class AccessToken {
    @Id @GeneratedValue
  public  Long id;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false,length = 600)
    public String access_token; // 获取到的凭证
    public Long  expires_in;    //	凭证有效时间，单位：秒
    public Long  createTime;    //  凭证的获取时间

    public AccessToken(){

    }
    public AccessToken(String access_token,long  expires_in){
       this.access_token =  access_token;
       this.expires_in = expires_in;
       this.createTime = new Date().getTime();
    }
}
