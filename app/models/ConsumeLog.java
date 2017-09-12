package models;

import java.util.*;
import javax.persistence.*;

@Entity
@DiscriminatorValue("consume")
public class ConsumeLog extends Log {

    @OneToOne
    public Consume consume;

    public ConsumeLog() {
    }

    public ConsumeLog(Manager manager, Consume consume) {
        super(manager);
        this.consume = consume;
    }
}

