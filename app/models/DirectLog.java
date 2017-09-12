package models;

import java.util.*;
import javax.persistence.*;

@Entity
@DiscriminatorValue("direct")
public class DirectLog extends Log {

    @OneToOne
    public Direct direct;

    public DirectLog() {
    }

    public DirectLog(Manager manager, Direct direct) {
        super(manager);
        this.direct = direct;
    }
}

