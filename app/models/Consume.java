package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
        name="queryCompanyCustomerConsumes",
        query="SELECT c FROM Consume c WHERE c.companyCustomer = :companyCustomer ORDER BY c.consumeWhen DESC"
    ),
    @NamedQuery(
        name="queryCustomerConsumes",
        query="SELECT c FROM Consume c WHERE c.companyCustomer.customer.id = :customerId ORDER BY c.consumeWhen DESC"
    ),
})
public class Consume {

    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    public CompanyCustomer companyCustomer;

    public Long amount;   // 应付

    public Long netPaid;   // 实付

    public Long preBalance;
    public Long netDeduction;   // 实扣，预存账户
    public Long postBalance;

    public int type; //0:现金结账，1：团购结账，2：预存结账

    public Long integralIncrease;

    public Date consumeWhen;

    @OneToMany(mappedBy="consume", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<Ticket> tickets;

    @OneToMany(mappedBy="used", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<Ticket> uses;

    @OneToMany(mappedBy="consume", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<CompanyProductConsume> companyProductConsumes;

    @OneToMany(mappedBy="consume", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<Booking> bookings;

    @OneToMany(mappedBy = "consume")
    public List<ConsumeTicket>  consumeTickets;

    @ManyToOne
    @JsonIgnore
    public StaticsConsume staticsConsume;

    public Consume() {
    }

    /* 
     * instance must be created after companyCustomer's balance is persisted
     */
    public Consume(CompanyCustomer companyCustomer, StaticsConsume staticsConsume, Long amount, Long netPaid, Long netDeduction, int type) {
        this.tickets = new ArrayList<Ticket>();
        this.staticsConsume = staticsConsume;
        this.companyProductConsumes = new ArrayList<CompanyProductConsume>();
        this.bookings = new ArrayList<Booking>();
        this.consumeTickets = new ArrayList<ConsumeTicket>();
        this.companyCustomer = companyCustomer;
        this.amount = amount;
        this.netPaid = netPaid;
        this.postBalance = companyCustomer.balance;
        this.netDeduction = netDeduction;
        this.preBalance = companyCustomer.balance + netDeduction;
        this.type = type;
        Calendar calendar = Calendar.getInstance();
        this.consumeWhen = calendar.getTime();
        this.integralIncrease = 0L;
    }

    public Consume addCompanyProductConsume(CompanyProductConsume companyProductConsume) {
        this.companyProductConsumes.add(companyProductConsume);
        return this;
    }

    public Consume addBooking(Booking booking) {
        this.bookings.add(booking);
        return this;
    }
}

