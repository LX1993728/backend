package models;


import javax.persistence.*;

@Entity
@DiscriminatorValue("integral")
@NamedQueries({
    @NamedQuery(
        name="queryIntegralInfo",
        query="SELECT i FROM IntegralInfo i WHERE i.integral.companyCustomer = :companyCustomer ORDER BY i.infoWhen DESC"
    ),
})
public class IntegralInfo extends Info{

    @OneToOne
    public Integral integral;

    public IntegralInfo() {
    }

    public IntegralInfo(Customer customer, Company company, Integral integral) {
        super(customer, company);
        this.integral = integral;
    }
}
