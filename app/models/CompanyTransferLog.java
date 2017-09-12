package models;

import javax.persistence.*;
import java.util.Date;

@Entity
public class CompanyTransferLog {
    @Id
    @GeneratedValue
    public Long id;

    @ManyToOne
    public RechargeStatus rechargeStatus;

    public String result_code;

    public Date responseTime;

    public Date requestTime;
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false,length = 600)
    public String responseInfo;


    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false,length = 600)
    public String requestInfo;

    public CompanyTransferLog(){

    }

    public CompanyTransferLog(RechargeStatus rechargeStatus,String requestInfo){
        this.rechargeStatus = rechargeStatus;
        this.requestInfo = requestInfo;
        this.requestTime = new Date();
        this.responseInfo = "NOT Response";

    }

    public void  setResponse(String responseInfo, String result_code){
        this.responseInfo = responseInfo;
        this.result_code = result_code;
        this.responseTime = new Date();
    }

}
