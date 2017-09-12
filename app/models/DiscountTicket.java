package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@DiscriminatorValue("discount")
public class DiscountTicket extends FreeTicket {

    public int discount;

    public boolean once;

    public DiscountTicket() {
    }

    public DiscountTicket(Customer customer, Consume consume, IssueRule rule, Company company, Manager issuedBy, StaticsIssue staticsIssue, DiscountBatch batch) {
        super(customer, consume, rule, company, issuedBy, staticsIssue, batch);
        this.discount = batch.discount;
        this.once = batch.once;
        Calendar calendar = Calendar.getInstance();
        if (batch.expired instanceof ExpiredMonths) {
            calendar.add(Calendar.MONTH, ((ExpiredMonths)batch.expired).months);
            this.expiredWhen = calendar.getTime();
        } else if (batch.expired instanceof ExpiredDays) {
            calendar.add(Calendar.DAY_OF_YEAR, ((ExpiredDays)batch.expired).days);
            this.expiredWhen = calendar.getTime();
        } else if (batch.expired instanceof ExpiredDate) {
            this.expiredWhen = ((ExpiredDate)batch.expired).expiredWhen;
        } else {
            calendar.add(Calendar.MONTH, 1200);    // over 100 years
            this.expiredWhen = calendar.getTime();
        }
    }

    public DiscountTicket(Customer customer, CompanyTicket companyTicket, StaticsTicketRequest staticsRequest) {
        super(customer, companyTicket, staticsRequest);
        DiscountBatch batch = (DiscountBatch)companyTicket.batch;
        this.discount = batch.discount;
        this.once = batch.once;
        Calendar calendar = Calendar.getInstance();
        if (batch.expired instanceof ExpiredMonths) {
            calendar.add(Calendar.MONTH, ((ExpiredMonths)batch.expired).months);
            this.expiredWhen = calendar.getTime();
        } else if (batch.expired instanceof ExpiredDays) {
            calendar.add(Calendar.DAY_OF_YEAR, ((ExpiredDays)batch.expired).days);
            this.expiredWhen = calendar.getTime();
        } else if (batch.expired instanceof ExpiredDate) {
            this.expiredWhen = ((ExpiredDate)batch.expired).expiredWhen;
        } else {
            calendar.add(Calendar.MONTH, 1200);    // over 100 years
            this.expiredWhen = calendar.getTime();
        }
    }

    public DiscountTicket(Customer customer, DiscountTicket fromTicket) {
        super(customer, fromTicket);
        this.discount = fromTicket.discount;
        this.once = fromTicket.once;
    }

    public DiscountTicket(Customer customer, Company company, Manager issuedBy, StaticsDirectIssue staticsDirectIssue, DiscountBatch batch) {
        super(customer, company, issuedBy, staticsDirectIssue, batch);
        this.discount = batch.discount;
        this.once = batch.once;
        Calendar calendar = Calendar.getInstance();
        if (batch.expired instanceof ExpiredMonths) {
            calendar.add(Calendar.MONTH, ((ExpiredMonths)batch.expired).months);
            this.expiredWhen = calendar.getTime();
        } else if (batch.expired instanceof ExpiredDays) {
            calendar.add(Calendar.DAY_OF_YEAR, ((ExpiredDays)batch.expired).days);
            this.expiredWhen = calendar.getTime();
        } else if (batch.expired instanceof ExpiredDate) {
            this.expiredWhen = ((ExpiredDate)batch.expired).expiredWhen;
        } else {
            calendar.add(Calendar.MONTH, 1200);    // over 100 years
            this.expiredWhen = calendar.getTime();
        }
    }
}

