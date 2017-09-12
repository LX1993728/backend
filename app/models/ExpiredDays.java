package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@DiscriminatorValue("days")
@NamedQueries({
  @NamedQuery(
     name="queryExpiredDays",
     query="SELECT e FROM ExpiredDays e WHERE e.days = :days"),
})
public class ExpiredDays extends Expired {
    public int days;

    public ExpiredDays() {
    }

    public ExpiredDays(int days) {
        super();
        this.days = days;
    }
}

