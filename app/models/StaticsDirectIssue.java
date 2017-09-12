package models;

import java.util.*;
import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
         name="queryStaticsDirectIssue",
         query="SELECT s FROM StaticsDirectIssue s WHERE s.date = :date AND s.batch = :batch"
    ),
    @NamedQuery(
         name="queryStaticsDirectIssues",
         query="SELECT s FROM StaticsDirectIssue s WHERE s.date >= :startDate AND s.date < :endDate AND s.batch.company = :company ORDER BY s.date, s.batch"
    ),
})
public class StaticsDirectIssue {

    @Id @GeneratedValue
    public Long id;

    public Date date;

    @ManyToOne
    public TicketBatch batch;

    @OneToMany(mappedBy="staticsDirectIssue", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<Ticket> issuedTickets;

    public Long amount;   // for deduction ticket
    public Long count;

    public StaticsDirectIssue() {
    }

    public StaticsDirectIssue(Date date, TicketBatch batch) {
        this.date = date;
        this.batch = batch;
        this.issuedTickets = new ArrayList<Ticket>();
        this.amount = 0L;
        this.count = 0L;
    }
}

