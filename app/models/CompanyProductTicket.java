package models;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Calendar;

@Entity
@DiscriminatorValue("companyproduct")
@NamedQueries({
    @NamedQuery(
        name="findCustomerCompanyProductTicketsValidInCompany",
        query="SELECT cpt FROM CompanyProductTicket cpt WHERE cpt.isRemoved = false AND cpt.expiredWhen > now() AND cpt.state = 0 AND cpt.customer.id = :customerId AND :company MEMBER OF cpt.validCompanies ORDER BY cpt.expiredWhen"),
})
public class CompanyProductTicket extends Ticket{

    public CompanyProductTicket() {
    }

    public CompanyProductTicket(Customer customer, CompanyTicket companyTicket, StaticsTicketRequest staticsRequest) {
        super(customer, companyTicket, staticsRequest);
        CompanyProductBatch batch = (CompanyProductBatch) companyTicket.batch;
        this.batch = batch;
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

    public CompanyProductTicket(Customer customer, CompanyProductTicket fromTicket) {
        super(customer, fromTicket);
        this.batch = fromTicket.batch;
    }

    public CompanyProductTicket(Customer customer, Company company, Manager issuedBy, StaticsDirectIssue staticsDirectIssue, CompanyProductBatch batch) {
        super(customer, company, issuedBy, staticsDirectIssue);
        this.batch = batch;
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
