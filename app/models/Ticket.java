package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "TYPE", discriminatorType=DiscriminatorType.STRING)
@NamedQueries({
  @NamedQuery(
     name="findCustomerAllTicketsValidInCompany",
     query="SELECT t FROM Ticket t WHERE t.isRemoved = false AND t.customer.id = :customerId AND :company MEMBER OF t.validCompanies ORDER BY t.expiredWhen"),

  @NamedQuery(
     name="findCustomerValidTicketsValidInCompany",
     query="SELECT t FROM Ticket t WHERE t.isRemoved = false AND t.expiredWhen > now() AND t.state = 0 AND t.customer.id = :customerId AND :company MEMBER OF t.validCompanies ORDER BY t.expiredWhen"),

  @NamedQuery(
     name="findCustomerAllTickets",
     query="SELECT t FROM Ticket t WHERE t.isRemoved = false AND t.expiredWhen > now() AND t.customer.id = :customerId ORDER BY t.expiredWhen"),

  @NamedQuery(
     name="findCustomerValidTicketsIncompany",
     query="SELECT t FROM Ticket t WHERE t.isRemoved = false AND t.expiredWhen > now() AND t.state = 0 AND t.customer.id = :customerId AND t.company = :company ORDER BY t.expiredWhen"),
  @NamedQuery(
     name="findCustomerValidTickets",
     query="SELECT t FROM Ticket t WHERE t.isRemoved = false AND t.expiredWhen > now() AND t.state = 0 AND t.customer.id = :customerId ORDER BY t.expiredWhen"),

  @NamedQuery(
     name="findCustomerValidTicketsIssuedByCompany",
     query="SELECT t FROM Ticket t WHERE t.isRemoved = false AND t.expiredWhen > now() AND t.state = 0 AND t.customer.id = :customerId AND t.company = :company ORDER BY t.expiredWhen"),

  @NamedQuery(
     name="findCustomerUsedupTickets",
     query="SELECT t FROM Ticket t WHERE t.isRemoved = false AND t.state = 1 AND t.customer.id = :customerId ORDER BY t.expiredWhen"),

  @NamedQuery(
     name="findCustomerExpiredTickets",
     query="SELECT t FROM Ticket t WHERE t.isRemoved = false AND t.expiredWhen < now() AND t.customer.id = :customerId ORDER BY t.expiredWhen"),

  @NamedQuery(
     name="findCustomerTicketsByCompanyTicket",
     query="SELECT t FROM Ticket t WHERE t.customer = :customer AND t.companyTicket = :companyTicket"),

  @NamedQuery(
     name="findCustomerTransferredOutTickets",
     query="SELECT t FROM Ticket t WHERE t.isRemoved = false AND t.state = 2 AND t.customer.id = :customerId"),
})
public abstract class Ticket {

    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    @JsonIgnore
    public Customer customer;

    @ManyToOne
    @JsonIgnore
    public Consume consume;   // this ticket is created by consume

    @ManyToOne
    @JsonIgnore
    public IssueRule issueRule;

    @ManyToOne
    @JsonIgnore
    public CompanyTicket companyTicket;  // this ticket is created by companyTicket, no consume and no issueRule

    @ManyToMany(targetEntity=Company.class, cascade=CascadeType.PERSIST)
    public List<Company> validCompanies;

    @ManyToOne
    public Company company;

    @ManyToOne
    public TicketBatch batch;

    @ManyToOne
    @JsonIgnore
    public Manager issuedBy;

    public Date issuedWhen;

    public Date expiredWhen;

    public int state;    // 0: normal, 1: used up, 2: transferred out

    @ManyToOne
    @JsonIgnore
    public Consume used;   // this ticket is used in consume

    @ManyToOne
    @JsonIgnore
    public StaticsIssue staticsIssue;

    @ManyToOne
    @JsonIgnore
    public StaticsDirectIssue staticsDirectIssue;

    @ManyToOne
    @JsonIgnore
    public StaticsTicketRequest staticsRequest;

    public int transferLeft;

    @ManyToOne
    @JsonIgnore
    public StaticsAccept staticsAccept;

    @ManyToOne
    @JsonIgnore
    public Direct directUse;   // this ticket is used directly

    @ManyToOne
    @JsonIgnore
    public Direct directIssue;   // this ticket is issued directly

    public boolean isRemoved;

    public Ticket() {
    }

    public Ticket(Customer customer, Consume consume, IssueRule rule, Company company, Manager issuedBy, StaticsIssue staticsIssue) {
        this.customer = customer;
        this.consume = consume;
        this.issueRule = rule;
        this.companyTicket = null;
        this.company = company;
        Calendar calendar = Calendar.getInstance();
        this.issuedWhen = calendar.getTime();
        this.issuedBy = issuedBy;
        this.state = 0;
        this.staticsIssue = staticsIssue;
        this.staticsRequest = null;
        this.used = null;
        this.isRemoved = false;
    }

    public Ticket(Customer customer, CompanyTicket companyTicket, StaticsTicketRequest staticsRequest) {
        this.customer = customer;
        this.consume = null;
        this.issueRule = null;
        this.companyTicket = companyTicket;
        this.company = companyTicket.company;
        Calendar calendar = Calendar.getInstance();
        this.issuedWhen = calendar.getTime();
        this.state = 0;
        this.staticsIssue = null;
        this.staticsRequest = staticsRequest;
        this.used = null;
        this.isRemoved = false;
    }

    public Ticket(Customer customer, Ticket fromTicket) {
        this.customer = customer;
        this.consume = null;
        this.issueRule = null;
        this.validCompanies = new ArrayList<Company>(fromTicket.validCompanies);
        this.company = fromTicket.company;
        this.issuedBy = fromTicket.issuedBy;
        this.issuedWhen = fromTicket.issuedWhen;
        this.expiredWhen = fromTicket.expiredWhen;
        this.state = 0;
        this.transferLeft = fromTicket.transferLeft - 1;
        this.used = null;
        this.isRemoved = false;
    }

    public Ticket(Customer customer, Company company, Manager issuedBy, StaticsDirectIssue staticsDirectIssue) {
        this.customer = customer;
        this.consume = null;
        this.issueRule = null;
        this.companyTicket = null;
        this.company = company;
        Calendar calendar = Calendar.getInstance();
        this.issuedWhen = calendar.getTime();
        this.issuedBy = issuedBy;
        this.state = 0;
        this.staticsDirectIssue = staticsDirectIssue;
        this.staticsIssue = null;
        this.staticsRequest = null;
        this.used = null;
        this.isRemoved = false;
    }

}

