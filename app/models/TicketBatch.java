package models;

import java.util.*;

import javax.persistence.*;
import org.hibernate.annotations.Index;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public abstract class TicketBatch extends Batch {

    @ManyToMany(targetEntity=Company.class, cascade=CascadeType.PERSIST)
    public List<Company> validCompanies;

    public Long maxNumber;
    public Long current;
    public Long leftNumber;

    public int maxTransfer;

    @ManyToOne
    public Expired expired;

    @ManyToMany(targetEntity=UseRule.class, mappedBy="batches")
    @JsonIgnore
    public List<UseRule> useRules;

    public TicketBatch() {
    }

    public TicketBatch(String name, String note, Company company, Manager issuedBy, List<Company> validCompanies, Long maxNumber, int maxTransfer, Expired expired) {
        super(name, note, company,issuedBy);
        this.validCompanies = validCompanies;
        this.maxNumber = maxNumber;
        this.current = 0L;
        this.leftNumber = maxNumber;
        this.maxTransfer = maxTransfer;
        this.expired = expired;
    }
}

