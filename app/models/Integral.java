package models;



import javax.persistence.Entity;
import javax.persistence.*;
import java.util.Calendar;
import java.util.Date;


@Entity
@NamedQueries({
    @NamedQuery(
        name="queryCompanyCustomerIntegrals",
        query="SELECT  i FROM Integral i WHERE i.companyCustomer = :companyCustomer ORDER BY i.integralWhen DESC"
    ),
})
public class Integral {

    @Id @GeneratedValue
    public Long id;

    public Date integralWhen;
    public Long amount;    // 消费积分金额

    public Long postBalance;   // 该用户在该商家的积分余额
    public Long preBalance; //上一次的积分余额



    @ManyToOne
    public CompanyCustomer companyCustomer;


    public Integral() {

    }

    public Integral(CompanyCustomer companyCustomer, Long reduce, Long iamount) {
        this.companyCustomer = companyCustomer;
        Calendar calendar = Calendar.getInstance();
        this.integralWhen = calendar.getTime();
        this.amount = iamount;
        this.preBalance = companyCustomer.integralBalance;
        this.postBalance = companyCustomer.integralBalance - reduce;
        companyCustomer.integralBalance -= reduce;
    }

    public Integral(CompanyCustomer companyCustomer, Long increase) {
        this.companyCustomer = companyCustomer;
        Calendar calendar = Calendar.getInstance();
        this.integralWhen = calendar.getTime();
        this.amount = 0L;

        this.preBalance = companyCustomer.integralBalance;
        this.postBalance = companyCustomer.integralBalance + increase;
        companyCustomer.integralBalance += increase;
    }
}
