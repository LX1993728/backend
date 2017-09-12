package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@DiscriminatorValue("deduction")
public class DeductionTicket extends FreeTicket {

    public Long deduction;

    public DeductionTicket() {
    }

    // make sure batch is rule.batch
    public DeductionTicket(Customer customer, Consume consume, IssueRule rule, Company company, Manager issuedBy, StaticsIssue staticsIssue, DeductionBatch batch) {
        super(customer, consume, rule, company, issuedBy, staticsIssue, batch);
        this.deduction = batch.faceValue;
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

    public DeductionTicket(Customer customer, CompanyTicket companyTicket, StaticsTicketRequest staticsRequest) {
        super(customer, companyTicket, staticsRequest);
        DeductionBatch batch = (DeductionBatch)companyTicket.batch;
        this.deduction = batch.faceValue;
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

    public DeductionTicket(Customer customer, DeductionTicket fromTicket) {
        super(customer, fromTicket);
        this.deduction = fromTicket.deduction;
    }

    // make sure batch is rule.batch
    public DeductionTicket(Customer customer, Company company, Manager issuedBy, StaticsDirectIssue staticsDirectIssue, DeductionBatch batch) {
        super(customer, company, issuedBy, staticsDirectIssue, batch);
        this.deduction = batch.faceValue;
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

