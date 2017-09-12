package models;

import java.util.*;

import javax.persistence.*;
import org.hibernate.annotations.Index;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "TYPE", discriminatorType=DiscriminatorType.STRING)
@NamedQueries({
    @NamedQuery(
        name="queryBroadcastOfCompany",
        query="SELECT p FROM Poster p WHERE p.company = :company AND p.scope = 0 ORDER BY posterWhen DESC"
    ),
    @NamedQuery(
        name="queryNotificationOfCompany",
        query="SELECT p FROM Poster p WHERE p.company = :company AND p.scope = 1 ORDER BY posterWhen DESC"
    ),
    @NamedQuery(
        name="queryBroadcastByLocation",
        query="SELECT p FROM Poster p WHERE p.company.status = 1 AND p.scope = 0 AND abs(p.company.longitude - :longitude) < 5 AND abs(p.company.latitude - :latitude) < 5 AND p.posterWhen IN(SELECT MAX(t.posterWhen) FROM Poster t GROUP BY t.company.id)"
    ),
})
public abstract class Poster {

    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    public Company company;

    public Date posterWhen;

    @JsonIgnore
    public int scope;    // 0: broadcast; 1: notification to member only

    public Poster() {
    }

    public Poster(Company company, int scope) {
        this.company = company;
        Calendar calendar = Calendar.getInstance();
        this.posterWhen = calendar.getTime();
        this.scope = scope;
    }
}

