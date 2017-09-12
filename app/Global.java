import java.util.*;
import javax.persistence.*;

import play.*;
import play.Logger;
import play.libs.*;
import play.db.jpa.*;

import models.Admin;
import models.Company;
import models.Manager;
import models.IssueRule;
import models.Customer;
import models.Consume;
import models.Ticket;
import models.DiscountTicket;
import models.DeductionTicket;
import models.StaticsIssue;
import models.TicketConfig;

public class Global extends GlobalSettings {
    
    public void onStart(Application app) {
        loadAppSettings();
    }

    private void loadAppSettings() {
        JPA.withTransaction(new F.Callback0() {
            @Override
            public void invoke() throws Throwable {
/*
                EntityManager em = JPA.em();
                Admin a1 = new Admin("lgr", "123456");
                em.persist(a1);

                Company c1 = new Company("北京昊通信盟", 116.336717, 40.062359, "北京昊通信盟", "010-83827323", "test@htxm.com", "test company");
                c1.addManager("htxm", "123456", 0);
                em.persist(c1);

                List<Company> validCompanies = new ArrayList();
                validCompanies.add(c1);
                IssueDeductionRule r1 = new IssueDeductionRule(c1, "满100返20", 10000L, validCompanies, 12, 2000L, 10000L, 2000L);
                em.persist(r1);

                TicketConfig tc = new TicketConfig("CHARGE_RATE", "100");
                em.persist(tc);

                Customer u1 = new Customer("13910079037");
                Consume consume1 = new Consume(u1, c1.managers.get(0), 14000L, 14000L);
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                StaticsIssue staticsIssue = new StaticsIssue(calendar.getTime(), r1);
                Ticket ticket = new DeductionTicket(u1, consume1, r1, c1, c1.managers.get(0), staticsIssue);
                staticsIssue.amount += ((DeductionTicket)ticket).leftDeduction;
                staticsIssue.count += 1L;
                JPA.em().persist(staticsIssue);
                JPA.em().persist(ticket);
                consume1.addTicket(ticket);
                u1.addConsume(consume1);
                u1.addTicket(ticket);
                em.persist(u1);
*/
            }
        });
    }
}
