package models;

import java.util.*;
import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
        name="queryLogsByManager",
        query="SELECT l FROM Log l WHERE l.manager = :manager ORDER BY l.logWhen DESC"
    ),
    @NamedQuery(
        name="queryPreviousLog",
        query="SELECT l FROM Log l WHERE l.manager = :manager AND l.logWhen < :logWhen ORDER BY l.logWhen DESC"
    ),
})
public abstract class Log {

    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    @JsonIgnore
    public Manager manager;

    public Date logWhen;

    public Log() {
    }

    public Log(Manager manager) {
        this.manager = manager;
        Calendar calendar = Calendar.getInstance();
        this.logWhen = calendar.getTime();
    }
}

