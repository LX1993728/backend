package models;

import java.util.*;
import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
         name="queryStaticsIssue",
         query="SELECT s FROM StaticsIssue s WHERE s.date = :date AND s.issueRule = :issueRule"
    ),
    @NamedQuery(
         name="queryStaticsIssues",
         query="SELECT s FROM StaticsIssue s WHERE s.date >= :startDate AND s.date < :endDate AND s.issueRule.company = :company ORDER BY s.date, s.issueRule"
    ),
})
public class StaticsIssue {

    @Id @GeneratedValue
    public Long id;

    public Date date;

    @ManyToOne
    public IssueRule issueRule;

    @OneToMany(mappedBy="staticsIssue", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<Ticket> issuedTickets;

    public Long amount;   // for deduction ticket
    public Long count;

    public StaticsIssue() {
    }

    public StaticsIssue(Date date, IssueRule issueRule) {
        this.date = date;
        this.issueRule = issueRule;
        this.issuedTickets = new ArrayList<Ticket>();
        this.amount = 0L;
        this.count = 0L;
    }

    public StaticsIssue addTicket(Ticket ticket) {
        if (ticket.issueRule.id == this.issueRule.id) {
            this.issuedTickets.add(ticket);
        }
        return this;
    }
}

