package models;

import java.util.*;
import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
         name="queryStaticsTransfer",
         query="SELECT s FROM StaticsTransfer s WHERE s.date = :date AND s.company = :company"
    ),
    @NamedQuery(
         name="queryStaticsTransfers",
         query="SELECT s FROM StaticsTransfer s WHERE s.date >= :startDate AND s.date < :endDate AND s.company = :company ORDER BY s.date"
    ),
})
public class StaticsTransfer {

    @Id @GeneratedValue
    public Long id;

    public Date date;

    @ManyToOne
    @JsonIgnore
    public Company company;

    public Long regs;   // 通过转移注册的新用户数
    public Long count;  // 转移次数

    @OneToMany(mappedBy="staticsTransfer", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<CompanyCustomer> companyCustomers;   // 通过转移注册的新用户列表

    public StaticsTransfer() {
    }

    public StaticsTransfer(Date date, Company company) {
        this.date = date;
        this.company = company;
        this.regs = 0L;
        this.count = 0L;
    }
}
