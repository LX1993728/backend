package models;

import java.util.*;

import javax.persistence.*;

@Entity
@DiscriminatorValue("discount")
public class UseDiscountRule extends UseRule {

    public int discountType;      // 0: new discount = ticket discount * convertRate, 1: new discount
    public int discount;          // new discount, don't care the discount of the ticket
    public double convertRate;    // new discount = ticket discount * convertRate;

    public UseDiscountRule() {
    }

    public UseDiscountRule(String name, Company usedBy, Long maxConsume,Long leastConsume, Long maxDeduction, List<TicketBatch> batches, double convertRate, String note) {
        super(name, usedBy,maxConsume, leastConsume, maxDeduction, batches, note);
        this.discountType = 0;
        this.discount = 100;
        this.convertRate = convertRate;
    }

    public UseDiscountRule(String name, Company usedBy, Long maxConsume,Long leastConsume, Long maxDeduction, List<TicketBatch> batches, int discount, String note) {
        super(name, usedBy,maxConsume, leastConsume, maxDeduction, batches, note);
        this.discountType = 1;
        this.discount = discount;
        this.convertRate = 1.0d;
    }
}

