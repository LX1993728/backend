package models;

import javax.persistence.*;
import java.util.*;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "TYPE", discriminatorType=DiscriminatorType.STRING)
public abstract class StaticsHits {

    @Id @GeneratedValue
    public Long id;

    public Date date;

    public Long hits;
    public Long membersHits; //会员点击量
    public Long nonmemberHits; //非会员点击量

    public StaticsHits() {

    }

    public StaticsHits(Date date, Long hits, Long membersHits, Long nonmemberHits) {
        this.date = date;
        this.hits = hits;
        this.membersHits = membersHits;
        this.nonmemberHits = nonmemberHits;
    }
}
