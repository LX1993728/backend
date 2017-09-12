package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@DiscriminatorValue("date")
public class ExpiredDate extends Expired {
    public Date expiredWhen;

    public ExpiredDate() {
    }

    public ExpiredDate(Date expiredWhen) {
        super();
        this.expiredWhen = expiredWhen;
    }
}

