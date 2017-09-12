package models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;
import java.util.List;

@Entity
@NamedQueries({
    @NamedQuery(
        name="queryFreeBatches",
        query="SELECT fb FROM FreeBatch fb WHERE fb.isEnabled = true AND :company = fb.company"
    ),
    @NamedQuery(
        name="queryFreeBatchesAvaiableInCompany",
        query="SELECT fb FROM FreeBatch fb, Company c WHERE fb.isEnabled = true AND (:company = c AND fb.company MEMBER OF c.associateCompanies AND :company MEMBER OF fb.validCompanies OR :company = c AND fb.company = :company AND :company MEMBER OF fb.validCompanies) "
    ),
    @NamedQuery(
        name="queryFreeBatchesAvaiableInCompanyAndGroup",
        query="SELECT fb FROM FreeBatch fb WHERE fb.isEnabled = true AND fb.company = :company AND (fb.belongGroup IS NOT NULL AND fb.belongGroup = :companyGroup AND fb.belongGroup.isEnabled = true)"
    ),
    @NamedQuery(
        name="queryOtherFreeBatchesAvaiableInCompany",
        query="SELECT fb FROM FreeBatch fb WHERE fb.isEnabled = true AND fb.company = :company AND (fb.belongGroup IS NOT NULL AND fb.belongGroup <> :companyGroup OR fb.belongGroup IS NULL)"
    ),
    @NamedQuery(
        name="queryOtherFreeBatchesAvaiableInGroup",
        query="SELECT fb FROM FreeBatch fb WHERE fb.isEnabled = true AND (fb.belongGroup IS NOT NULL AND fb.belongGroup = :companyGroup AND fb.belongGroup.isEnabled = true)"
    ),
    @NamedQuery(
            name = "queryOneFreeBatchForCompanyGroupById",
            query = "SELECT fb FROM FreeBatch fb WHERE fb.isEnabled = true And fb.company = :company AND fb.belongGroup IS NOT NULL AND fb.id = :batchId "
    ),
    @NamedQuery(
            name = "queryOneFreeBatchForCompanyGroupByGroupAndSourceBatch",
            query = "SELECT fb FROM FreeBatch fb WHERE fb.isEnabled = true AND fb.company = :company AND fb.belongGroup IS NOT NULL AND" +
                    " (fb.sourceBatchId = :sourceBatchId OR (fb.sourceBatchId IS NULL AND fb.id = :sourceBatchId ))"
    )
})
public abstract class FreeBatch extends TicketBatch {
    @JsonIgnore
    public Long  sourceBatchId = null;

    public FreeBatch() {
    }

    @OneToOne
    public CompanyGroup belongGroup;

    public FreeBatch(String name, String note, Company company, Manager issuedBy, List<Company> validCompanies, Long maxNumber, int maxTransfer, Expired expired, CompanyGroup belongGroup) {
        super(name, note, company,  issuedBy, validCompanies, maxNumber, maxTransfer, expired);
        this.belongGroup = belongGroup;
    }
}
