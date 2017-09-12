package models;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;

@Entity
public class RechargeStatus {
    @Id @GeneratedValue
    public Long id;

    @OneToOne
    public  Recharge recharge;

    @OneToOne
    public CompanyCustomer companyCustomer;

    public int customerPayBy; // 0: wx   1: ali

    public int companyPayBy;  // 0:wx   1:ali

    public int customerStatus; // 0:init  1: success -1: failure

    public int companyStatus; // 0:init  1: success -1: failure

    public RechargeStatus(){

    }

    public RechargeStatus(CompanyCustomer companyCustomer, int customerPayBy){
        this.recharge = null;
        this.companyCustomer = companyCustomer;
        this.customerPayBy = customerPayBy;
        this.customerStatus = 0;
        this.companyPayBy = 0;
        this.companyStatus = 0;
    }

    public RechargeStatus(Recharge recharge, CompanyCustomer companyCustomer, int customerPayBy, int companyPayBy, int companyStatus, int customerStatus){
        this.recharge = recharge;
        this.companyCustomer = companyCustomer;
        this.customerPayBy = customerPayBy;
        this.companyPayBy = companyPayBy;
        this.companyStatus = companyStatus;
        this.customerStatus = customerStatus;
    }
}
