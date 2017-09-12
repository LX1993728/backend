package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
        name="queryCustomerTransfersIn",
        query="SELECT t FROM Transfer t WHERE t.dstTicket.customer.id = :customerId ORDER BY t.transferWhen DESC"
    ),
    @NamedQuery(
        name="queryCustomerTransfersOut",
        query="SELECT t FROM Transfer t WHERE t.srcTicket.customer.id = :customerId ORDER BY t.transferWhen DESC"
    ),
    @NamedQuery(
        name="queryTransferBySrc",
        query="SELECT t FROM Transfer t WHERE t.srcTicket =  :srcTicket"
    ),
})
public class Transfer {

    @Id @GeneratedValue
    public Long id;

    public String src;
    public String too;


    @OneToOne
    public Ticket srcTicket;

    @OneToOne
    public Ticket dstTicket;

    public Date transferWhen;

    public Transfer() {
    }

    public Transfer(Ticket srcTicket, Ticket dstTicket, String src, String to) {
        this.srcTicket = srcTicket;
        this.dstTicket = dstTicket;
        this.src = src;
        this.too = to;
        Calendar calendar = Calendar.getInstance();
        this.transferWhen = calendar.getTime();
    }
}

