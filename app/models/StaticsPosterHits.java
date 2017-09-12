package models;

import javax.persistence.*;
import java.util.Date;

@Entity
@NamedQueries({
    @NamedQuery(
        name="queryStaticsPosterHitss",
        query="SELECT sp FROM StaticsPosterHits sp WHERE sp.date >= :startDate AND sp.date < :endDate AND sp.companyTicket = :companyTicket OR sp.advertisement = :advertisement ORDER BY sp.date DESC"
    ),
})
public abstract class StaticsPosterHits extends StaticsHits {

    public StaticsPosterHits() {

    }

    public StaticsPosterHits(Date date, Long hits, Long membersHits, Long nonmemberHits) {
        super(date, hits, membersHits, nonmemberHits);
    }
}
