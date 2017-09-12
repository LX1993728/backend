package models;

import java.util.*;
import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
         name="queryStaticsTicketRequest",
         query="SELECT s FROM StaticsTicketRequest s WHERE s.date = :date AND s.companyTicket = :companyTicket"
    ),
    @NamedQuery(
        name="queryStaticsTicketRequestByManufacturerTicket",
        query="SELECT s FROM StaticsTicketRequest s WHERE s.date = :date AND s.companyTicket = :companyTicket"
    ),
    @NamedQuery(
         name="queryStaticsTicketRequests",
         query="SELECT s FROM StaticsTicketRequest s WHERE s.date >= :startDate AND s.date < :endDate AND s.companyTicket.company = :company ORDER BY s.date, s.companyTicket"
    ),
})
public class StaticsTicketRequest {

    @Id @GeneratedValue
    public Long id;

    public Date date;

    @ManyToOne
    public CompanyTicket companyTicket;

    @OneToMany(mappedBy="staticsRequest", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<Ticket> requestedTickets;

    public Long deductionAmount;   // for deduction ticket
    public Long deductionCount;

    public Long discountCount;

    public Long grouponCost;
    public Long grouponAmount;
    public Long grouponCount;

    public Long regs;   // 通过获取免费／团购券而注册的新用户数

    @OneToMany(mappedBy="staticsRequest", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<CompanyCustomer> companyCustomers;   // 通过获取免费／团购券注册的新用户列表

    public StaticsTicketRequest() {
    }

    public StaticsTicketRequest(Date date, CompanyTicket companyTicket) {
        this.date = date;
        this.companyTicket = companyTicket;
        this.requestedTickets = new ArrayList<Ticket>();
        this.deductionAmount = 0L;
        this.deductionCount = 0L;
        this.discountCount = 0L;
        this.grouponCost = 0L;
        this.grouponAmount = 0L;
        this.grouponCount = 0L;
        this.regs = 0L;
    }
}

