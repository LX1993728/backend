package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
         name="findEnabledIssueRulesOfCompany",
         query="SELECT ir FROM IssueRule ir WHERE ir.isEnabled = true AND ir.company = :company ORDER BY ir.startingAmount DESC"
    ),
    @NamedQuery(
         name="findEnabledIssueRulesForBatch",
         query="SELECT ir FROM IssueRule ir WHERE ir.isEnabled = true AND ir.batch = :batch"
    ),
})
public class IssueRule {

    @Id @GeneratedValue
    public Long id;

    public String name;

    @ManyToOne
    public Company company;

    public Long startingAmount;   // 起步额度
    public Long endAmount;        // 结束额度

    @ManyToOne
    public Batch batch;

    public boolean isEnabled;

    public IssueRule() {
    }

    public IssueRule(String name, Company company, Long startingAmount, Long endAmount, Batch batch) {
        this.name = name;
        this.company = company;
        this.startingAmount = startingAmount;
        this.endAmount = endAmount;
        this.batch = batch;
        this.isEnabled = true;
    }
}

