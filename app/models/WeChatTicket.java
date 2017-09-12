package models;

import javax.persistence.*;

@Entity
@NamedQueries({
        @NamedQuery(
                name = "queryTicket",
                query = "SELECT tic FROM WeChatTicket tic"
        )
})
public class WeChatTicket {
    @Id
    @GeneratedValue
    public  Long id;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false,length = 600)
    public String ticket; // 获取到的JS凭证
    public Long  expires_in;    //	ticket有效时间，单位：秒
    public Long  createTime;    //  凭证的获取时间
    public int errcode;
    public String errmsg;

    public WeChatTicket(){

    }
}
