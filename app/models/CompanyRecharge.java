package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class CompanyRecharge {
    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    public Company company;

    public Date rechargeWhen;

    public String totalFee;
    public String tradeNo;
    public String tradeStatus;

    public int status;    // 0: initial, 1: success, 2: failed

    public CompanyRecharge() {
    }

    public CompanyRecharge(Company company, String totalFee) {
        this.company = company;
        this.totalFee = totalFee;
        Calendar calendar = Calendar.getInstance();
        this.rechargeWhen = calendar.getTime();
        this.status = 0;
    }
}
