package models;

import java.util.*;

import javax.persistence.*;

@Entity
@DiscriminatorValue("companyticket")
public class CompanyTicketInfo extends Info {

    @ManyToOne
    public CompanyTicket companyTicket;

    public CompanyTicketInfo() {
    }

    public CompanyTicketInfo(Customer customer, Company company, CompanyTicket companyTicket) {
        super(customer, company);
        this.companyTicket = companyTicket;
    }
}

