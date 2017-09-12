package models;

import java.util.*;

import javax.persistence.*;

@Entity
@DiscriminatorValue("direct")
public class DirectInfo extends Info {

    @OneToOne
    public Direct direct;

    public DirectInfo() {
    }

    public DirectInfo(Customer customer, Company company, Direct direct) {
        super(customer, company);
        this.direct = direct;
    }
}

