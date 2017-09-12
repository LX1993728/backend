package models;

import java.util.*;
import javax.persistence.*;

@Entity
@DiscriminatorValue("recharge")
public class RechargeLog extends Log {

    @OneToOne
    public Recharge recharge;

    public RechargeLog() {
    }

    public RechargeLog(Manager manager, Recharge recharge) {
        super(manager);
        this.recharge = recharge;
    }
}

