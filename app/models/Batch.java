package models;

import java.util.*;
import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "TYPE", discriminatorType=DiscriminatorType.STRING)
@NamedQueries({
    @NamedQuery(
         name="findEnabledBatchesOfCompany",
         query="SELECT b FROM Batch b WHERE b.isEnabled = true AND b.company = :company"
    ),
})
public abstract class Batch {

    @Id @GeneratedValue
    public Long id;

    public String name;

    public String note;

    @ManyToOne
    public Manager issuedBy;

    @ManyToOne
    public Company company;

    public boolean isEnabled;

    public Batch() {
    }

    public Batch(String name, String note, Company company, Manager issuedBy) {
        this.name = name;
        this.note = note;
        this.company = company;
        this.issuedBy = issuedBy;
        this.isEnabled = true;
    }
}

