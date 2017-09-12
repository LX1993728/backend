package models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;


@Entity
@NamedQueries({
    @NamedQuery(
        name="queryCompanyAccountConfig",
        query="SELECT ca FROM CompanyAccountConfig ca WHERE ca.company = :company"
    ),
})
public class CompanyAccountConfig {

    @Id@GeneratedValue
    public Long id;

    public boolean isAccount; //是否计费

    public boolean useticketPoint;

    public boolean prestorePoint;

    public boolean confirmcodePoint;

    public String accountValue; //费率

    @OneToOne
    @JsonIgnore
    public Company company; //所属公司



    public CompanyAccountConfig() {

    }

    public CompanyAccountConfig(Company company, boolean isAccount) {
        this.company = company;
        this.isAccount = isAccount;
        this.useticketPoint = false;
        this.prestorePoint = false;
        this.confirmcodePoint = false;
        this.accountValue = "0";
    }

    public CompanyAccountConfig(Company company, boolean isAccount, boolean useticketPoint, boolean prestorePoint, boolean confirmcodePoint, String accountValue) {
        this.company = company;
        this.isAccount = isAccount;
        this.useticketPoint = useticketPoint;
        this.prestorePoint = prestorePoint;
        this.confirmcodePoint = confirmcodePoint;
        this.accountValue = accountValue;
    }
}
