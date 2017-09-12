package models;

import java.util.*;

import javax.persistence.*;
import org.hibernate.annotations.Index;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@DiscriminatorValue("ads")
@NamedQueries({
    @NamedQuery(
        name="findAdvertisementsOfCompany",
        query="SELECT cp FROM Advertisement cp WHERE cp.company = :company"
    ),
})
public class Advertisement extends Poster {

    public String content;

    public Advertisement() {
    }

    public Advertisement(Company company, String content, int scope) {
        super(company, scope);
        this.content = content;
    }
}

