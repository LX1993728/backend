package models;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("CompanyWXRecharge")
public class CompanyWXRecharge extends CompanyRecharge{
    public CompanyWXRecharge(){

    }
    public CompanyWXRecharge(Company company, String totalFee){
        super(company,totalFee);
    }
}
