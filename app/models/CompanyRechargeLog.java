package models;

import java.util.*;
import javax.persistence.*;

@Entity
@DiscriminatorValue("companyRecharge")
public class CompanyRechargeLog extends Log {

    @OneToOne
    public CompanyRecharge companyRecharge;

    public CompanyRechargeLog() {
    }

    public CompanyRechargeLog(Manager manager, CompanyRecharge companyRecharge) {
        super(manager);
        this.companyRecharge = companyRecharge;
    }
}

