package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@DiscriminatorValue("groupon")
@NamedQueries({
  @NamedQuery(
     name="findCustomerGrouponTicketsValidInCompany",
     query="SELECT t FROM GrouponTicket t WHERE t.isRemoved = false AND t.expiredWhen > now() AND t.state = 0 AND t.customer.id = :customerId AND :company MEMBER OF t.validCompanies ORDER BY t.expiredWhen"),
})
public class GrouponTicket extends Ticket {

    public Long cost;

    public Long deduction;

    public GrouponTicket() {
    }

    public GrouponTicket(Customer customer, CompanyTicket companyTicket, StaticsTicketRequest staticsRequest) {
        super(customer, companyTicket, staticsRequest);
        GrouponBatch batch = (GrouponBatch)companyTicket.batch;
        this.batch = batch;
        this.cost = batch.cost;
        this.deduction = batch.deduction;
        this.transferLeft = batch.maxTransfer;
        this.validCompanies = new ArrayList<Company>(batch.validCompanies);

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

    public GrouponTicket(Customer customer, GrouponTicket fromTicket) {
        super(customer, fromTicket);
        this.cost = fromTicket.cost;
        this.deduction = fromTicket.deduction;
        this.batch = fromTicket.batch;
    }

    public GrouponTicket(Customer customer, Company company, Manager issuedBy, StaticsDirectIssue staticsDirectIssue, GrouponBatch batch) {
        super(customer, company, issuedBy, staticsDirectIssue);
        this.batch = batch;
        this.cost = batch.cost;
        this.deduction = batch.deduction;
        this.transferLeft = batch.maxTransfer;
        this.validCompanies = new ArrayList<Company>(batch.validCompanies);

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

