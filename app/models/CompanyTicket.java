package models;

import java.util.*;

import javax.persistence.*;
import org.hibernate.annotations.Index;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@DiscriminatorValue("companyticket")
@NamedQueries({
    @NamedQuery(
        name="queryCompanyFreeTickets",
        query="SELECT ct FROM CompanyTicket ct JOIN ct.batch b WHERE ct.company = :company AND TYPE(b) IN (DeductionBatch, DiscountBatch)"
    ),
    @NamedQuery(
        name="queryCompanyGrouponTickets",
        query="SELECT ct FROM CompanyTicket ct JOIN ct.batch b WHERE ct.company = :company AND TYPE(b) = GrouponBatch"
    ),
})
public class CompanyTicket extends Poster {

    public String name;

    @ManyToOne
    public TicketBatch batch;

    public CompanyTicket() {
    }

    public CompanyTicket(Company company, String name, TicketBatch batch, int scope) {
        super(company, scope);
        this.name = name;
        this.batch = batch;
    }
}

