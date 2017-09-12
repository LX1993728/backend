package models;

import java.util.*;
import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
         name="queryStaticsCustomer",
         query="SELECT s FROM StaticsCustomer s WHERE s.company = :company AND s.date = :date"
    ),
    @NamedQuery(
         name="queryStaticsCustomers",
         query="SELECT s FROM StaticsCustomer s WHERE s.date >= :startDate AND s.date < :endDate AND s.company = :company ORDER BY s.date DESC"
    ),
})
public class StaticsCustomer {

    @Id @GeneratedValue
    public Long id;

    public Date date;

    @ManyToOne
    @JsonIgnore
    public Company company;

    public Long regs;     // only by consume, reg by transfer in StaticsTransfer.countNew
    public Long amount;
    public Long netPaid;

    @OneToMany(mappedBy="staticsCustomer", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<CompanyCustomer> companyCustomers;

    public StaticsCustomer() {
    }

    public StaticsCustomer(Date date, Company company) {
        this.date = date;
        this.company = company;
        this.regs = 0L;
        this.amount = 0L;
        this.netPaid = 0L;
    }
}

