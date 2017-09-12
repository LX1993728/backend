package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
         name="queryConfirmCodeToken",
         query="SELECT c FROM ConfirmCode c WHERE c.manager = :manager AND c.customer = :customer AND c.token = :token AND c.expiredWhen > now()"
    ),
})
public class ConfirmCode {

    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    @JsonIgnore
    public Manager manager;

    @ManyToOne
    @JsonIgnore
    public Customer customer;

    public String token;

    @JsonIgnore
    public Date expiredWhen;

    public ConfirmCode() {
    }

    public ConfirmCode(Manager manager, Customer customer, String token, Date expiredWhen) {
        this.manager = manager;
        this.customer = customer;
        this.token = token;
        this.expiredWhen = expiredWhen;
    }
}

