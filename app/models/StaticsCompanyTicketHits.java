package models;

import javax.persistence.*;
import java.util.Date;

@Entity
@DiscriminatorValue("companyticket")
@NamedQueries({
    @NamedQuery(
        name= "queryTotalCompanyTicketHits",
        query="SELECT SUM (sc.hits) FROM StaticsCompanyTicketHits sc WHERE sc.companyTicket = :companyTicket"
    ),
    @NamedQuery(
        name="queryStaticsCompanyTicketHits",
        query="SELECT sc FROM StaticsCompanyTicketHits sc WHERE sc.date = :date AND sc.companyTicket = :companyTicket"
    ),
})
public class StaticsCompanyTicketHits extends StaticsPosterHits{

    @ManyToOne
    public CompanyTicket companyTicket;

    public StaticsCompanyTicketHits() {

    }

    public StaticsCompanyTicketHits(Date date, Long hits, Long membersHits, Long nonmemberHits, CompanyTicket companyTicket) {
        super(date, hits, membersHits, nonmemberHits);
        this.companyTicket = companyTicket;
    }
}
