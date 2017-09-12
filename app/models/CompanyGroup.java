package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import play.Logger;
import play.db.jpa.JPA;

import javax.persistence.*;
import java.util.*;

@Entity
@NamedQueries({
    @NamedQuery(
        name="findEnabledCompanyGroupBygId",
        query="SELECT cg FROM CompanyGroup cg WHERE cg.isEnabled = true AND cg.gId = :gId"
    ),
    @NamedQuery(
        name="findEnabledCompanyGroupsOfCompany",
        query="SELECT cg FROM CompanyGroup cg WHERE cg.isEnabled = true AND cg.master = :master OR :company MEMBER OF cg.members OR :company MEMBER OF cg.disAgreeMembers"
    ),
})
public class CompanyGroup {

    @Id @GeneratedValue
    public Long id;

//    @JsonIgnore
    @Column(unique = true, nullable = false)
    public Long gId;

    public Long number;

    public boolean isEnabled;

    public String name;

    public Long master;

    @ManyToMany(targetEntity = Company.class, cascade = CascadeType.PERSIST)
    public List<Company> members;

    @ManyToMany(targetEntity = Company.class,cascade = CascadeType.PERSIST)
    @JoinTable(name = "companygroup_discompany")
    @JsonIgnore
    public List<Company> disAgreeMembers;

    public CompanyGroup () {

    }

    public CompanyGroup (Long master, String name) {
        this.master = master;
        this.name = name;
        this.gId = new Long(new Random().nextInt(9999));
        this.isEnabled = true;
        this.members = null;
        this.disAgreeMembers = null;
        this.number = 0L;
    }

    public CompanyGroup addMember (Company company) {
        this.members.add(company);
        return this;
    }

    public CompanyGroup setEnable (boolean isEnabled) {
        this.isEnabled = isEnabled;
        return this;
    }


}
