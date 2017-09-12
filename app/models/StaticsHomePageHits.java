package models;

import javax.persistence.*;
import java.util.*;

@Entity
@DiscriminatorValue("homepage")
@NamedQueries({
    @NamedQuery(
        name="queryStaticsHomePageHitss",
        query="SELECT sh FROM StaticsHomePageHits sh WHERE sh.date >= :startDate AND sh.date < :endDate AND sh.company = :company ORDER BY sh.date DESC"
    ),
    @NamedQuery(
        name="queryStaticsHomePageHits",
        query="SELECT sh FROM StaticsHomePageHits sh WHERE sh.date = :date AND sh.company = :company"
    ),
    @NamedQuery(
        name="queryTotalHomePageHits",
        query="SELECT SUM (sh.hits) FROM StaticsHomePageHits sh WHERE sh.company = :company"
    ),
})
public class StaticsHomePageHits extends StaticsHits{

    @ManyToOne
    public Company company;

    public StaticsHomePageHits() {

    }

    public StaticsHomePageHits(Date date, Long hits, Long membersHits, Long nonmemberHits, Company company) {
        super(date, hits, membersHits, nonmemberHits);
        this.company = company;
    }
}
