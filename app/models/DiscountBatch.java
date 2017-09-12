package models;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.util.List;

@Entity
@DiscriminatorValue("discount")
public class DiscountBatch extends FreeBatch {
    public int discount;
    public boolean once;

    public DiscountBatch() {
    }

    public DiscountBatch(String name, String note, Company company, Manager issuedBy, List<Company> validCompanies, Long maxNumber, int maxTransfer, Expired expired, int discount, boolean once, CompanyGroup belongGroup) {
        super(name, note, company, issuedBy, validCompanies, maxNumber, maxTransfer, expired, belongGroup);
        this.discount = discount;
        this.once = once;
    }
    public DiscountBatch(String name, String note, Company company, Manager issuedBy, List<Company> validCompanies, Long maxNumber, int maxTransfer, Expired expired, int discount, boolean once, CompanyGroup belongGroup, Long sourceBatchId) {
       this(name, note, company, issuedBy, validCompanies, maxNumber, maxTransfer, expired, discount, once, belongGroup);
       this.sourceBatchId = sourceBatchId;
    }

}
