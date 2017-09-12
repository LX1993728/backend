package models;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.util.List;

@Entity
@DiscriminatorValue("deduction")
public class DeductionBatch extends FreeBatch {
    public Long faceValue;
    public DeductionBatch() {
    }

    public DeductionBatch(String name, String note, Company company, Manager issuedBy, List<Company> validCompanies, Long maxNumber, int maxTransfer, Expired expired, Long faceValue, CompanyGroup belongGroup) {
        super(name, note, company, issuedBy, validCompanies, maxNumber, maxTransfer, expired, belongGroup);
        this.faceValue = faceValue;
    }
    public DeductionBatch(String name, String note, Company company, Manager issuedBy, List<Company> validCompanies, Long maxNumber, int maxTransfer, Expired expired, Long faceValue, CompanyGroup belongGroup, Long sourceBatchId) {
       this(name,note,company,issuedBy,validCompanies, maxNumber, maxTransfer,expired,faceValue, belongGroup);
       this.sourceBatchId = sourceBatchId;
    }
}
