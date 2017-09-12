package models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;

@Entity
public class ConsumeTicket {

    @Id @GeneratedValue
    public Long id;

    public int type; //0:收券; 1:发券

    @ManyToOne
    @JsonIgnore
    public Consume consume;

    @OneToOne
    @JoinColumn(name = "ticket_id")
    public Ticket ticket;

    public ConsumeTicket() {

    }

    public ConsumeTicket(Consume consume, Ticket ticket, int type) {
        this.consume = consume;
        this.ticket = ticket;
        this.type = type;
    }
}
