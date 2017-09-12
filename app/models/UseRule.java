package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "TYPE", discriminatorType=DiscriminatorType.STRING)
@NamedQueries({
    @NamedQuery(
        name="findEnabledUseRulesOfCompany",
        query="SELECT ur FROM UseRule ur WHERE ur.isEnabled = true AND ur.usedBy = :company"
    ),
    @NamedQuery(
        name="findEnabledUseRulesOfCompanyForTicket",
        query="SELECT ur FROM UseRule ur WHERE ur.isEnabled = true AND ur.usedBy = :company"
    ),
    @NamedQuery(
        name="findEnabledUseRulesOfBatch",
        query="SELECT ur FROM UseRule ur WHERE ur.isEnabled = true AND ur.usedBy = :company AND :batch MEMBER OF ur.batches"
    ),
})
public class UseRule {

    @Id @GeneratedValue
    public Long id;

    public String name;

    @ManyToOne @JsonIgnore
    public Company usedBy;

    @ManyToMany(targetEntity=TicketBatch.class, cascade=CascadeType.PERSIST)
    public List<TicketBatch> batches;

    public Long leastConsume;   // 最少消费

    public Long maxDeduction;   // 最多抵扣

    public Long maxConsume;    //最高消费

    public String note;

    public boolean isEnabled;

    public UseRule() {
    }

    public UseRule(String name, Company usedBy, Long maxConsume, Long leastConsume, Long maxDeduction, List<TicketBatch> batches, String note) {
        this.name = name;
        this.usedBy = usedBy;
        this.leastConsume = leastConsume;
        this.maxDeduction = maxDeduction;
        this.maxConsume = maxConsume;
        this.batches = batches;
        this.isEnabled = true;
        this.note = note;
    }
}

