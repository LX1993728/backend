package models;

import java.util.*;
import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
         name="queryCompanyAccounts",
         query="SELECT a FROM CompanyAccount a WHERE a.time >= :startDate AND a.time < :endDate AND a.company = :company ORDER BY a.time"
    ),
    @NamedQuery(
        name="queryAllCompanyAccounts",
        query="SELECT a FROM CompanyAccount a WHERE a.company = :company ORDER BY a.time DESC"
    ),
})
public class CompanyAccount {

    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    @JsonIgnore
    public Company company;

    public Date time;
    public Long preBalance;
    public Long amount;
    public Long postBalance;

    public String note;

    // for recharge
    @ManyToOne
    @JsonIgnore
    public Admin admin;
    @ManyToOne
    public Manager manager;

    // for ticket use
    @OneToOne
    public Ticket ticket;

    public CompanyAccount() {
    }

    public CompanyAccount(Company company, Admin admin, Long amount, String note) {
        this.company = company;
        Calendar calendar = Calendar.getInstance();
        this.time = calendar.getTime();

        this.admin = admin;
        this.amount = amount;
        this.preBalance = company.balance;
        this.postBalance = company.balance + amount;
        company.balance += amount;
        this.note = note;
    }

    public CompanyAccount(Company company, Manager manager, Long amount, String note) {
        this.company = company;
        Calendar calendar = Calendar.getInstance();
        this.time = calendar.getTime();

        this.manager = manager;
        this.amount = amount;
        this.preBalance = company.balance;
        this.postBalance = company.balance + amount;
        company.balance += amount;
        this.note = note;
    }

    public CompanyAccount(Company company, Long amount, String note) {
        this.company = company;
        Calendar calendar = Calendar.getInstance();
        this.time = calendar.getTime();

        this.manager = null;
        this.amount = amount;
        this.preBalance = company.balance;
        this.postBalance = company.balance + amount;
        company.balance += amount;
        this.note = note;
    }

    public CompanyAccount(Company company, Ticket ticket, Long amount, String note) {
        this.company = company;
        Calendar calendar = Calendar.getInstance();
        this.time = calendar.getTime();

        this.ticket = ticket;
        this.amount = amount;
        this.preBalance = company.balance;
        this.postBalance = company.balance + amount;
        company.balance += amount;
        this.note = note;
    }
}

