package models;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("CompanyAliRecharge")
public class CompanyAliRecharge extends CompanyRecharge {
    public CompanyAliRecharge(){

    }
    public CompanyAliRecharge(Company company, String totalFee){
        super(company,totalFee);
    }
}
