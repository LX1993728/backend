package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
        name="queryCompanyCustomerRecharges",
        query="SELECT c FROM Recharge c WHERE c.companyCustomer = :companyCustomer ORDER BY c.rechargeWhen DESC"
    ),
    @NamedQuery(
        name="queryCustomerRecharges",
        query="SELECT c FROM Recharge c WHERE c.companyCustomer.customer.id = :customerId ORDER BY c.rechargeWhen DESC"
    ),
})
public class Recharge {

    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    public CompanyCustomer companyCustomer;

    public Long preBalance;
    public Long amount;
    public Long postBalance;

    public Date rechargeWhen;

    public Recharge() {
    }

    public Recharge(CompanyCustomer companyCustomer, Long amount) {
        this.companyCustomer = companyCustomer;
        this.amount = amount;
        this.preBalance = companyCustomer.balance;
        this.postBalance = companyCustomer.balance + amount;
        companyCustomer.balance += amount;
        Calendar calendar = Calendar.getInstance();
        this.rechargeWhen = calendar.getTime();
    }
}

