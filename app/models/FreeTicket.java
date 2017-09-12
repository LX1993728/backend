package models;

import java.util.*;

import javax.persistence.*;
import org.hibernate.annotations.Index;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
  @NamedQuery(
     name="findCustomerFreeTicketsValidInCompany",
     query="SELECT t FROM FreeTicket t WHERE t.isRemoved = false AND t.expiredWhen > now() AND t.state = 0 AND t.customer.id = :customerId AND :company MEMBER OF t.validCompanies ORDER BY t.expiredWhen"),
})
public abstract class FreeTicket extends Ticket {

    public FreeTicket() {
    }

    public FreeTicket(Customer customer, Consume consume, IssueRule rule, Company company, Manager issuedBy, StaticsIssue staticsIssue, FreeBatch batch) {
        super(customer, consume, rule, company, issuedBy, staticsIssue);
        this.batch = batch;
        this.transferLeft = batch.maxTransfer;
        this.validCompanies = new ArrayList<Company>(batch.validCompanies);
    }

    public FreeTicket(Customer customer, CompanyTicket companyTicket, StaticsTicketRequest staticsRequest) {
        super(customer, companyTicket, staticsRequest);
        this.batch = (FreeBatch)companyTicket.batch;
        this.validCompanies = new ArrayList<Company>(companyTicket.batch.validCompanies);
        this.transferLeft = companyTicket.batch.maxTransfer;
    }

    public FreeTicket(Customer customer, FreeTicket fromTicket) {
        super(customer, fromTicket);
        this.batch = fromTicket.batch;
    }

    public FreeTicket(Customer customer, Company company, Manager issuedBy, StaticsDirectIssue staticsDirectIssue, FreeBatch batch) {
        super(customer, company, issuedBy, staticsDirectIssue);
        this.batch = batch;
        this.transferLeft = batch.maxTransfer;
        this.validCompanies = new ArrayList<Company>(batch.validCompanies);
    }

}
