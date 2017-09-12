package models;

import javax.persistence.*;
import java.util.Date;
@NamedQueries(
        {
                @NamedQuery(
                        name= "queryCusRechargeLogByStatus",
                        query = "SELECT c from CustomerTrasactionLog c where c.rechargeStatus= :rechargeStatus "
                )
        }
)
@Entity
public class CustomerTrasactionLog {
    @Id  @GeneratedValue
    public Long id;

    @OneToOne
    public RechargeStatus rechargeStatus;

    public String result_code;

    public Date responseTime;
    public Date requestTime;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false,length = 600)
    public String requestInfo;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false,length = 600)
    public String responseInfo;

    public String deveceType; // Android or IOS
    public CustomerTrasactionLog(){

    }

    public CustomerTrasactionLog(RechargeStatus rechargeStatus,String requestInfo,int type){
        this.rechargeStatus = rechargeStatus;
        this.requestInfo = requestInfo;
        this.requestTime = new Date();
        this.responseInfo = "NOT Response";
        if(type == 1){
            this.deveceType = "AndroidRecharge";
        }else if(type == 2){
            this.deveceType = "IOSRecharge";
        }
    }

    public void  setResponse(String responseInfo, String result_code){
        this.responseInfo = responseInfo;
        this.result_code = result_code;
        this.responseTime = new Date();
    }


}
