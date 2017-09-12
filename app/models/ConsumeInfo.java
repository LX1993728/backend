package models;

import java.util.*;

import javax.persistence.*;

@Entity
@DiscriminatorValue("consume")
@NamedQueries({
    @NamedQuery(
        name="queryConsumeAndRechargeInfo",
        query="SELECT c FROM ConsumeInfo c WHERE c.consume.companyCustomer = :companyCustomer AND c.consume.type = 2 ORDER BY c.infoWhen DESC"
    ),
    @NamedQuery(
        name="queryConsumeInfo",
        query="SELECT c FROM ConsumeInfo c WHERE c.consume.companyCustomer = :companyCustomer AND c.consume.type = 0 ORDER BY c.infoWhen DESC"
    ),
})
public class ConsumeInfo extends Info {

    @OneToOne
    public Consume consume;

    public ConsumeInfo() {
    }

    public ConsumeInfo(Customer customer, Company company, Consume consume) {
        super(customer, company);
        this.consume = consume;
    }
}

