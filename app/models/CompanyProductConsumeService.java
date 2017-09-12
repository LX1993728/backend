package models;

import java.util.*;

import javax.persistence.*;
import org.hibernate.annotations.Index;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
        name="findCustomerCompanyProductConsumeServiceByProduct",
        query="SELECT c FROM CompanyProductConsumeService c WHERE c.companyProductConsume.companyProduct = :companyProduct"
    ),
})
public class CompanyProductConsumeService {

    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    @JsonIgnore
    public CompanyProductConsume companyProductConsume;

    public int serviceType;   // 0: just statement,  1: statement with valid days

    public String statement;
    public int validDays;    // －1: forever

    @OneToOne
    public Company inherit;

    public CompanyProductConsumeService() {
    }

    public CompanyProductConsumeService(CompanyProductConsume companyProductConsume, String statement) {
        this.companyProductConsume = companyProductConsume;
        this.serviceType = 0;
        this.statement = statement;
    }

    public CompanyProductConsumeService(CompanyProductConsume companyProductConsume, String statement, int validDays) {
        this.companyProductConsume = companyProductConsume;
        this.serviceType = 1;
        this.statement = statement;
        this.validDays = validDays;
    }
}

