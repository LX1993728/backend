package models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.*;

@Entity
@NamedQueries({
   @NamedQuery(
      name="queryStaticsConsumes",
      query="SELECT s FROM StaticsConsume s WHERE s.date >= :startDate AND s.date < :endDate AND s.company = :company ORDER BY s.date DESC"
   ),
   @NamedQuery(
      name="queryStaticsConsume",
      query="SELECT s FROM StaticsConsume s WHERE s.date = :date AND s.company = :company"
   ),
})
public class StaticsConsume {

    @Id @GeneratedValue
    public Long id;

    public Date date;
    public Long uses;
    public Long issues;

    @ManyToOne
    @JsonIgnore
    public Company company;

    @OneToMany(mappedBy = "staticsConsume")
    public List<Consume> consumes;

    public Long amount; //应收总额
    public Long netPaid; //实收总额
    public Long netDeduction; //实扣总额
    public Long recharge; //充值金额
    public Long integralIncrease;
    public Long integralReduce;

    public StaticsConsume() {

    }

    public StaticsConsume(Company company, Date date, Long uses, Long issues, Long amount, Long netPaid, Long netDeduction, Long recharge, Long integralIncrease, Long integralReduce) {
        this.company = company;
        this.date = date;
        this.uses = uses;
        this.issues = issues;
        this.amount = amount;
        this.netPaid = netPaid;
        this.netDeduction = netDeduction;
        this.recharge = recharge;
        this.integralIncrease = integralIncrease;
        this.integralReduce = integralReduce;
    }
}
