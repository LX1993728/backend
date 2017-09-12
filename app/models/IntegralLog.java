package models;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.OneToOne;

@Entity
@DiscriminatorValue("integral")
public class IntegralLog extends Log{

    @OneToOne
    public Integral integral;

    public IntegralLog() {
    }

    public IntegralLog(Manager manager, Integral integral) {
        super(manager);
        this.integral = integral;
    }
}
