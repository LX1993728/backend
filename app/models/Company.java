package models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

@Entity
@NamedQueries({
        @NamedQuery(
                name="findAllCompany",
                query="SELECT c FROM Company c"
        ),
        @NamedQuery(
                name="findCompanyByCId",
                query="SELECT c FROM Company c WHERE c.status = 1 AND c.cId = :cId"
        ),
        @NamedQuery(
                name="findCompanyById",
                query="SELECT c FROM Company c WHERE c.status = 1 AND c.companyType <> 0 AND c.id = :companyId "
        ),
        @NamedQuery(
                name="findCompanyByKey",
                query="SELECT c FROM Company c WHERE c.status = 1 AND c.companyType <> 0 AND c.name LIKE :key "
        ),
        @NamedQuery(
                name="findManufacturerById",
                query="SELECT c FROM Company c WHERE c.status = 1 AND c.companyType = 0 AND :company = c "
        ),
        @NamedQuery(
                name="findManufacturerByKey",
                query="SELECT c FROM Company c WHERE c.status = 1 AND c.companyType = 0 AND c.name LIKE :key"
        ),
        @NamedQuery(
                name="findCompanyByLocation",
                query="SELECT c FROM Company c WHERE c.companyType <> 0 AND c.status = 1 AND abs(c.longitude - :longitude) < 5 AND abs(c.latitude - :latitude) < 5 ORDER BY (c.longitude - :longitude) * (c.longitude - :longitude) + (c.latitude - :latitude) * (c.latitude - :latitude)"
        ),
        @NamedQuery(
                name="findAllActiveCompany",
                query="SELECT c FROM Company c WHERE c.status = 1 AND c.companyType <> 0"
        ),
        @NamedQuery(
                name="findCompaniesAcceptDiscountTicket",
                query="SELECT c FROM Company c, DiscountTicket t, UseDiscountRule r WHERE c.companyType <> 0 AND c.status = 1 AND t = :ticket AND c MEMBER OF t.validCompanies AND r.usedBy = c AND r.isEnabled = true AND t.batch MEMBER OF r.batches"
        ),
        @NamedQuery(
                name="findCompaniesAcceptDeductionTicket",
                query="SELECT c FROM Company c, DeductionTicket t, UseDeductionRule r WHERE c.companyType <> 0 AND c.status = 1 AND t = :ticket AND c MEMBER OF t.validCompanies AND r.usedBy = c AND r.isEnabled = true AND t.batch MEMBER OF r.batches"
        ),
        @NamedQuery(
                name="checkAssociateCompany",
                query="SELECT c FROM Company c WHERE c.companyType <> 0 AND :company = c AND :issuedBy MEMBER OF c.associateCompanies"
        ),
})
public class Company {

    @Id @GeneratedValue
    public Long id;

//    @JsonIgnore
    @Column(unique = true, nullable = false)
    public Long cId;

    public String name;

    public double longitude;
    public double latitude;
    public String address;
    public String telephone;
    public String email;
    public String description;

    public String contentType;

    @JsonIgnore
    @Transient
    public int counts; // 临时计算一个专卖店某个原厂商授权产品的总销量

    @Lob
    @Basic(fetch=FetchType.LAZY)
    @JsonIgnore
    public byte[] logo;

    public int status;  //0:forbidden, 1:normal, 2:wait approve...
    public int companyType;   //0:manufacturer, 1:company

    @OneToOne(mappedBy = "company")
    public CompanyAccountConfig companyAccountConfig;

    @OneToMany(mappedBy="company", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<Manager> managers;

    @OneToMany(mappedBy="usedBy", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<UseRule> useRules;

    @OneToMany(mappedBy="company", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<Batch> allBatches;

    @OneToMany(mappedBy="company")
    @JsonIgnore
    public List<Info> infos;

    @ManyToMany(targetEntity= TicketBatch.class, mappedBy="validCompanies")
    @JsonIgnore
    public List<TicketBatch> batches;

    @ManyToMany(targetEntity = CompanyGroup.class, mappedBy = "members")
    @JsonIgnore
    public List<CompanyGroup> groups;

    @ManyToMany(targetEntity = CompanyGroup.class, mappedBy = "disAgreeMembers")
    @JsonIgnore
    public List<CompanyGroup> disgroups;

    @ManyToMany(targetEntity= Company.class)
    @JsonIgnore
    public List<Company> associateCompanies;

    @ManyToMany(mappedBy="associateCompanies")
    @JsonIgnore
    public List<Company> associatedCompanies;

    @ManyToMany(targetEntity = Company.class)
    @JoinTable(name = "manufacturer_company")
    @JsonIgnore
    public List<Company> manufacturers;

    @ManyToMany(mappedBy = "manufacturers")
    public List<Company> exclusiveCompanies;

    @ManyToMany(targetEntity= Ticket.class, mappedBy="validCompanies")
    @JsonIgnore
    public List<Ticket> validTickets;

    @OneToMany(mappedBy="company")
    @JsonIgnore
    public List<Ticket> issuedTickets;

    @OneToMany(mappedBy="company", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<CompanyAccount> accounts;

    @OneToMany(mappedBy="company")
    @JsonIgnore
    public List<CompanyCustomer> companyCustomers;

    @OneToMany(mappedBy = "company")
    @JsonIgnore
    public List<IntegralStrategy> integralStrategies;

    @JsonIgnore
    public Long balance;

    public Company() {
    }
    public Company(String name, String address, String telephone, String email) {
        this.name = name;
        this.longitude = 0L;
        this.latitude = 0L;
        this.address = address;
        this.telephone = telephone;
        this.email = email;
        this.description = null;
        this.status = 0;
        this.balance = 0L;
        this.companyType = 1;
        SimpleDateFormat sdf = new SimpleDateFormat("yyMM");
        Long strDate = Long.parseLong(sdf.format(new Date()));
        this.cId = strDate + (new Random().nextInt(9999));
    }
    public Company(String name, double longitude, double latitude, String address, String telephone, String email, String description) {
        this.managers = new ArrayList<Manager>();
        this.companyCustomers = new ArrayList<CompanyCustomer>();
        this.name = name;
        this.longitude = longitude;
        this.latitude = latitude;
        this.address = address;
        this.telephone = telephone;
        this.email = email;
        this.description = description;
        this.status = 1;
        this.balance = 0L;
        this.companyType = 1;
        SimpleDateFormat sdf = new SimpleDateFormat("yyMM");
        String strData = sdf.format(new Date()) + new Random().nextInt(9999);
        this.cId = Long.parseLong(strData);
    }

    public Company addManager(String name, String passwd, int level) {
        Manager newManager = new Manager(this, name, passwd, level);
        this.managers.add(newManager);
        return this;
    }

    public Company addAccount(CompanyAccount account) {
        this.accounts.add(account);
        return this;
    }
}

