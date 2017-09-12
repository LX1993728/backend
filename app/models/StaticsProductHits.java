package models;

import javax.persistence.*;
import java.util.*;


@Entity
@DiscriminatorValue("product")
@NamedQueries({
    @NamedQuery(
        name="queryStaticsProductHites",
        query="SELECT sp FROM StaticsProductHits sp WHERE sp.date >= :startDate AND sp.date < :endDate AND sp.product = :product ORDER BY sp.date DESC"
    ),
    @NamedQuery(
        name="queryStaticsProductHits",
        query="SELECT sp FROM StaticsProductHits sp WHERE sp.date = :date AND sp.product = :product"
    ),
    @NamedQuery(
        name="queryTotalProductHits",
        query="SELECT SUM (sp.hits) FROM StaticsProductHits sp WHERE sp.product = :product"
    ),
})
public class StaticsProductHits extends StaticsHits{

    @ManyToOne
    public Product product;

    public StaticsProductHits() {

    }

    public StaticsProductHits(Date date, Long hits, Long membersHits, Long nonmemberHits, Product product) {
        super(date, hits, membersHits, nonmemberHits);
        this.product = product;
    }
}
