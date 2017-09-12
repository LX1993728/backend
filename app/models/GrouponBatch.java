package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@DiscriminatorValue("groupon")
@NamedQueries({
    @NamedQuery(
        name="queryEnabledGrouponBatches",
        query="SELECT gb FROM GrouponBatch gb WHERE gb.isEnabled = true AND :company = gb.company"
    ),
    @NamedQuery(
        name="queryGrouponBatchesAvaiableInCompany",
        query="SELECT gb FROM GrouponBatch gb, Company c WHERE gb.isEnabled = true AND (:company = c AND gb.company MEMBER OF c.associateCompanies AND :company MEMBER OF gb.validCompanies OR :company = c AND gb.company = :company AND :company MEMBER OF gb.validCompanies)"
    ),
})
public class GrouponBatch extends TicketBatch {
    public Long cost;
    public Long deduction;

    public GrouponBatch() {
    }

    public GrouponBatch(String name, String note, Company company, Manager issuedBy, List<Company> validCompanies, Long maxNumber, int maxTransfer, Expired expired, Long cost, Long deduction) {
        super(name, note, company, issuedBy, validCompanies, maxNumber, maxTransfer, expired);
        this.cost = cost;
        this.deduction = deduction;
    }
}
