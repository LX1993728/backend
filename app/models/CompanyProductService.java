package models;

import java.util.*;

import javax.persistence.*;
import org.hibernate.annotations.Index;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
        name = "findCompanyProductServices",
        query = "SELECT c FROM CompanyProductService c WHERE c.companyProduct = :companyProduct"
    ),
    @NamedQuery(
        name = "findCompanyProductServicesByInherit",
        query = "SELECT c FROM CompanyProductService c WHERE c.inherit = :inherit"
    ),
})
public class CompanyProductService {

    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    @JsonIgnore
    public CompanyProduct companyProduct;

    public int serviceType;   // 0: just statement,  1: statement with valid days

    public String statement;
    public int validDays;    // －1: forever
    public Date lastModified;

    @ManyToOne(targetEntity = CompanyProductService.class)
    public CompanyProductService inherit;

    @OneToMany(mappedBy = "inherit", cascade = CascadeType.REMOVE)
    @JsonIgnore
    public List<CompanyProductService> inheritedes;

    public CompanyProductService() {
    }

    public CompanyProductService(CompanyProduct companyProduct, String statement) {
        this.companyProduct = companyProduct;
        this.serviceType = 0;
        this.statement = statement;
        Calendar calendar = Calendar.getInstance();
        this.lastModified = calendar.getTime();
    }
    public CompanyProductService(CompanyProduct companyProduct, String statement, int validDays) {
        this.companyProduct = companyProduct;
        this.serviceType = 1;
        this.statement = statement;
        this.validDays = validDays;
        Calendar calendar = Calendar.getInstance();
        this.lastModified = calendar.getTime();
    }
}

