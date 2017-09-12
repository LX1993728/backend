package models;

import java.util.*;

import javax.persistence.*;

@Entity
@DiscriminatorValue("company")
@NamedQueries({
  @NamedQuery(
     name="queryFavorateCompanyByCustomerAndCompany",
     query="SELECT f FROM FavorateCompany f WHERE f.customer = :customer AND f.company = :company"),
})
public class FavorateCompany extends Favorate {

    @ManyToOne
    public Company company;

    public FavorateCompany() {
    }

    public FavorateCompany(Customer customer, Company company) {
        super(customer);
        this.company = company;
    }
}

