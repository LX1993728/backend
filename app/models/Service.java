package models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.Calendar;
import java.util.Date;

@Entity
@NamedQueries({
    @NamedQuery(
        name = "findServicesOfCompany",
        query = "SELECT s FROM Service s WHERE s.company = :company"
    ),
})
public class Service {

    @Id @GeneratedValue
    public Long id;

    public int serviceType;   // 0: just statement,  1: statement with valid days

    public String statement;
    public int validDays;    // －1: forever
    public Date lastModified;

    @ManyToOne
    public Company company;

    public Service () {

    }

    public Service (Company company, String statement) {
        this.company = company;
        this.serviceType = 0;
        this.statement = statement;
        Calendar calendar = Calendar.getInstance();
        this.lastModified = calendar.getTime();
    }

    public Service (Company company, String statement, int validDays) {
        this.company = company;
        this.serviceType = 1;
        this.statement = statement;
        this.validDays = validDays;
        Calendar calendar = Calendar.getInstance();
        this.lastModified = calendar.getTime();
    }
}
