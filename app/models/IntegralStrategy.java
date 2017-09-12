package models;
import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.Calendar;
import java.util.Date;

@Entity
@NamedQueries({
    @NamedQuery(
        name="queryIntegralStrategyById",
        query="SELECT i FROM IntegralStrategy i WHERE i.id = :id "
    ),
    @NamedQuery(
        name="queryEnabledIntegralStrategies",
        query="SELECT i FROM IntegralStrategy i WHERE i.isEnabled = true AND i.company = :company"
    ),
})
public class IntegralStrategy {

    @Id @GeneratedValue
    public Long id;

    public String name; //100积分兑换一瓶可乐


    public Long discount;   // 积分兑换系数


    public String note;


    public Long minNumber; //最低消费
    public Long maxNumber; //最高消费


    public boolean isEnabled; //false:失效 true: 生效

    @ManyToOne
    @JsonIgnore
    public Company company;

    public IntegralStrategy() {

    }

    public IntegralStrategy(Company company, Long discount, Long minNumber, Long maxNumber, String note, String name) {
        this.company = company;
        this.name = name;
        this.isEnabled = true;
        this.discount = discount;
        this.note = note;
        this.minNumber = minNumber;
        this.maxNumber = maxNumber;
    }
}