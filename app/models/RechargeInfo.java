package models;

import play.api.i18n.Messages;

import java.util.*;

import javax.persistence.*;

@Entity
@DiscriminatorValue("recharge")
@NamedQueries({
    @NamedQuery(
        name="queryRechargeInfo",
        query="SELECT r FROM RechargeInfo r WHERE r.recharge.companyCustomer = :companyCustomer ORDER BY r.infoWhen DESC"
    ),
})
public class RechargeInfo extends Info {

    @OneToOne
    public Recharge recharge;

    public RechargeInfo() {
    }

    public RechargeInfo(Customer customer, Company company, Recharge recharge) {
        super(customer, company);
        this.recharge = recharge;
    }
}

