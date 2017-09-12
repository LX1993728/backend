package models;

import java.util.*;
import javax.persistence.*;

@Entity
public class Direct {

    @Id @GeneratedValue
    public Long id;

    public String companyCustomerName;

    @OneToMany(mappedBy="directUse", cascade=CascadeType.PERSIST)
    public List<Ticket> uses;

    @OneToMany(mappedBy="directIssue", cascade=CascadeType.PERSIST)
    public List<Ticket> issues;

    public Direct() {
        this.uses = new ArrayList<Ticket>();
        this.issues = new ArrayList<Ticket>();
        this.companyCustomerName = null;
    }

    public Direct addUse(Ticket ticket, String companyCustomerName) {
        this.uses.add(ticket);
        this.companyCustomerName = companyCustomerName;
        return this;
    }

    public Direct addIssue(Ticket ticket, String companyCustomerName) {
        this.issues.add(ticket);
        this.companyCustomerName = companyCustomerName;
        return this;
    }
}

