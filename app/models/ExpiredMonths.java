package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@DiscriminatorValue("months")
@NamedQueries({
  @NamedQuery(
     name="queryExpiredMonths",
     query="SELECT e FROM ExpiredMonths e WHERE e.months = :months"),
})
public class ExpiredMonths extends Expired {
    public int months;

    public ExpiredMonths() {
    }

    public ExpiredMonths(int months) {
        super();
        this.months = months;
    }
}

