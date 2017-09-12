package models;

import java.util.*;
import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
         name="queryStaticsAcceptWithRule",
         query="SELECT s FROM StaticsAccept s WHERE s.date = :date AND s.usedIn = :usedIn AND s.issuedBy = :issuedBy AND s.useRule = :useRule"
    ),
    @NamedQuery(
         name="queryStaticsAcceptsByAccept",
         query="SELECT s FROM StaticsAccept s WHERE s.date >= :startDate AND s.date < :endDate AND s.usedIn = :usedIn OR s.issuedBy = :issuedBy ORDER BY s.usedIn, s.issuedBy, s.date DESC "
    ),
    @NamedQuery(
         name="queryStaticsAcceptsByCompany",
         query="SELECT s FROM StaticsAccept s WHERE s.date >= :startDate AND s.date < :endDate AND s.issuedBy = :issuedBy AND s.usedIn = :usedIn ORDER BY s.usedIn, s.issuedBy,s.date DESC "
    ),
})
public class StaticsAccept {

    @Id @GeneratedValue
    public Long id;

    public Date date;

    @ManyToOne
    public Company usedIn;

    @ManyToOne
    public Company issuedBy;

    @ManyToOne
    public UseRule useRule;

    @OneToMany(mappedBy="staticsAccept", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<Ticket> tickets;

    public Long count;

    public StaticsAccept() {
    }

    public StaticsAccept(Date date, Company usedIn, Company issuedBy, UseRule useRule) {
        this.date = date;
        this.usedIn = usedIn;
        this.issuedBy = issuedBy;
        this.useRule = useRule;
        this.tickets = new ArrayList<Ticket>();
        this.count = 0L;
    }
}

