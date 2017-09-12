package models;

import java.util.*;
import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
        name="queryCompanyCustomer",
        query="SELECT c FROM CompanyCustomer c WHERE c.company = :company AND c.customer = :customer"
    ),
    @NamedQuery(
        name="queryCompanyCustomerByName",
        query="SELECT c FROM CompanyCustomer c WHERE c.company = :company AND c.customer.name = :customerName"
    ),
    @NamedQuery(
        name="queryCompanyCustomers",
        query="SELECT c FROM CompanyCustomer c WHERE c.company = :company ORDER BY c.regWhen DESC"
    ),
    @NamedQuery(
        name="queryCompanyCustomerOfCustomer",
        query="SELECT c FROM CompanyCustomer c WHERE c.customer = :customer"
    ),
    @NamedQuery(
        name="queryTotalBalanceOfCompany",
        query="SELECT SUM(c.balance) FROM CompanyCustomer c WHERE c.company = :company"
    ),
})
public class CompanyCustomer {

    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    public Company company;

    @ManyToOne
    public Customer customer;

    public Date regWhen;

    public Long amount;    // 该用户在该商家消费总额
    public Long times;     // 该用户在该商家的消费次数

    public int discount;   // 该用户在该商家的折扣
    public Long balance;   // 该用户在该商家的预存金额

    public Long integralBalance;

    @ManyToOne
    @JsonIgnore
    public StaticsCustomer staticsCustomer;  // 通过消费新注册的用户

    @ManyToOne
    @JsonIgnore
    public StaticsTransfer staticsTransfer;   // 通过转移注册的新用户

    @ManyToOne
    @JsonIgnore
    public StaticsTicketRequest staticsRequest;   // 通过获取免费券/团购券注册的新用户

    public CompanyCustomer() {
    }

    public CompanyCustomer(Company company, Customer customer, Long amount, StaticsCustomer staticsCustomer) {
        this.company = company;
        this.customer = customer;
        this.amount = amount;
        this.times = 1L;
        Calendar calendar = Calendar.getInstance();
        this.regWhen = calendar.getTime();
        this.staticsCustomer = staticsCustomer;
        this.staticsTransfer = null;
        this.staticsRequest = null;
        this.balance = 0L;
        this.integralBalance =0L;
        this.discount = 100;

    }

    public CompanyCustomer(Company company, Customer customer, StaticsTransfer staticsTransfer) {
        this.company = company;
        this.customer = customer;
        this.amount = 0L;
        this.times = 0L;
        Calendar calendar = Calendar.getInstance();
        this.regWhen = calendar.getTime();
        this.staticsCustomer = null;
        this.staticsTransfer = staticsTransfer;
        this.staticsRequest = null;
        this.balance = 0L;
        this.integralBalance =0L;
        this.discount = 100;

    }

    public CompanyCustomer(Company company, Customer customer, StaticsTicketRequest staticsRequest) {
        this.company = company;
        this.customer = customer;
        this.amount = 0L;
        this.times = 0L;
        Calendar calendar = Calendar.getInstance();
        this.regWhen = calendar.getTime();
        this.staticsCustomer = null;
        this.staticsTransfer = null;
        this.staticsRequest = staticsRequest;
        this.balance = 0L;
        this.integralBalance =0L;
        this.discount = 100;
    }

    /*
     * for example, by order product
     */
    public CompanyCustomer(Company company, Customer customer) {
        this.company = company;
        this.customer = customer;
        this.amount = 0L;
        this.times = 0L;
        Calendar calendar = Calendar.getInstance();
        this.regWhen = calendar.getTime();
        this.staticsCustomer = null;
        this.staticsTransfer = null;
        this.staticsRequest = null;
        this.balance = 0L;
        this.integralBalance =0L;
        this.discount = 100;
    }
}

