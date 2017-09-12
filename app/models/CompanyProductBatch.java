package models;

import javax.persistence.*;
import java.util.List;

@Entity
@DiscriminatorValue("companyproduct")
@NamedQueries({
    @NamedQuery(
        name="queryEnabledCompanyProductBatches",
        query="SELECT cpb FROM CompanyProductBatch cpb WHERE cpb.isEnabled = true AND :company = cpb.company"
    ),
    @NamedQuery(
        name="queryEnabledCompanyProductBatchesByCompanyProduct",
        query="SELECT cpb FROM CompanyProductBatch cpb WHERE cpb.isEnabled = true AND :companyProduct = cpb.companyProduct"
    ),
    @NamedQuery(
        name="queryEnabledCompanyProductBatchesAvaiableInCompany",
        query="SELECT cpb FROM CompanyProductBatch cpb WHERE cpb.isEnabled = true AND :company = cpb.company OR :company MEMBER OF cpb.validCompanies"
    ),
})
public class CompanyProductBatch extends TicketBatch{

    @ManyToOne
    public CompanyProduct companyProduct;

    public CompanyProductBatch() {

    }
    public CompanyProductBatch(String name, String note, Company company, Manager issuedBy, List<Company> validCompanies, Long maxNumber, int maxTransfer, Expired expired, CompanyProduct companyProduct) {
        super(name, note, company, issuedBy, validCompanies, maxNumber, maxTransfer, expired);
        this.companyProduct = companyProduct;
    }

}
