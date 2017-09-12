package models;

import java.util.*;

import javax.persistence.*;
import org.hibernate.annotations.Index;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "TYPE", discriminatorType=DiscriminatorType.STRING)
@NamedQueries({
  @NamedQuery(
     name="queryBeenReadInfos",
     query="SELECT i FROM Info i WHERE i.isRemoved = false AND i.customer = :customer AND i.company = :company AND i.beenRead = true ORDER BY i.infoWhen DESC"),
  @NamedQuery(
     name="queryCustomerInfosByCompany",
     query="SELECT i FROM Info i WHERE i.isRemoved = false AND i.customer = :customer AND i.company = :company ORDER BY i.infoWhen DESC"),
  @NamedQuery(
     name="queryCompaniesOfInfo",
     query="SELECT i.company FROM Info i WHERE i.isRemoved = false AND i.customer = :customer ORDER BY i.company.id DESC"),
  @NamedQuery(
     name="querySystemInfos",
     query="SELECT i FROM Info i WHERE i.isRemoved = false AND i.customer = :customer ORDER BY i.infoWhen DESC"),
  @NamedQuery(
     name="queryNotBeenReadInfos",
     query="SELECT i FROM Info i WHERE i.isRemoved = false AND i.customer = :customer AND i.company = :company AND i.beenRead = false  ORDER BY i.infoWhen DESC"),
})

public abstract class Info {

    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    @JsonIgnore
    public Customer customer;

    @ManyToOne
    @JsonIgnore
    public Company company;

    // isTagged is for important info for customer
    public boolean isTagged;

    public boolean beenRead;

    @JsonIgnore
    public boolean isRemoved;

    public Date infoWhen;

    public Info() {
    }

    public Info(Customer customer, Company company) {
        this.customer = customer;
        this.company = company;
        Calendar calendar = Calendar.getInstance();
        this.infoWhen = calendar.getTime();
        this.isTagged = false;
        this.beenRead = false;
        this.isRemoved = false;
    }
}

