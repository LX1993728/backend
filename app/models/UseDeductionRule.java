package models;

import java.util.*;

import javax.persistence.*;

@Entity
@DiscriminatorValue("deduction")
public class UseDeductionRule extends UseRule {
    // default = 100
    // normally less than 100
    // for example, rate = 80 means ticket with 100 can only be used as 80
    public int rate;

    public UseDeductionRule() {
    }

    public UseDeductionRule(String name, Company usedBy,Long maxConsume, Long leastConsume, Long maxDeduction, List<TicketBatch> batches, int rate, String note) {
        super(name, usedBy, maxConsume,leastConsume, maxDeduction, batches, note);
        this.rate = rate;
    }
}

