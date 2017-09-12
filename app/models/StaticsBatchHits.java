package models;

import javax.persistence.*;
import java.util.Date;

@Entity
@DiscriminatorValue("batch")
@NamedQueries({
    @NamedQuery(
        name="queryStaticsBatchHitses",
        query="SELECT sb FROM StaticsBatchHits sb WHERE sb.date >= :startDate AND sb.date < :endDate AND sb.batch = :batch ORDER BY sb.date DESC"
    ),
    @NamedQuery(
        name="queryStaticsBatchHits",
        query="SELECT sb FROM StaticsBatchHits sb WHERE sb.date = :date AND sb.batch = :batch"
    ),
    @NamedQuery(
        name="queryTotalBatchHits",
        query="SELECT SUM (sb.hits) FROM StaticsBatchHits sb WHERE sb.batch = :batch"
    ),
})
public class StaticsBatchHits extends StaticsHits{

    @ManyToOne
    public Batch batch;

    public StaticsBatchHits() {

    }

    public StaticsBatchHits(Date date, Long hits, Long membersHits, Long nonmemberHits, Batch batch) {
        super(date, hits, membersHits, nonmemberHits);
		this.batch = batch;
    }
}
