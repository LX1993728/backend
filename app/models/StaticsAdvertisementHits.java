package models;

import javax.persistence.*;
import java.util.Date;


@Entity
@DiscriminatorValue("advertisement")
@NamedQueries({
    @NamedQuery(
        name= "queryTotalAdevertisementHits",
        query="SELECT SUM (sa.hits) FROM StaticsAdvertisementHits sa WHERE sa.advertisement = :advertisement"
    ),
    @NamedQuery(
        name="queryStaticsAdvertisementHits",
        query="SELECT sa FROM StaticsAdvertisementHits sa WHERE sa.date = :date AND sa.advertisement = :advertisement"
    ),
})
public class StaticsAdvertisementHits extends StaticsPosterHits{

    @ManyToOne
    public Advertisement advertisement;

    public StaticsAdvertisementHits() {

    }

    public StaticsAdvertisementHits(Date date, Long hits, Long membersHits, Long nonmemberHits, Advertisement advertisement) {
        super(date, hits, membersHits, nonmemberHits);
        this.advertisement = advertisement;
    }
}
