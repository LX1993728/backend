package models;

import java.util.*;

import javax.persistence.*;

@Entity
@DiscriminatorValue("product")
@NamedQueries({
  @NamedQuery(
     name="queryFavorateProductByCustomerAndCompanyProduct",
     query="SELECT f FROM FavorateProduct f WHERE f.customer = :customer AND f.companyProduct = :companyProduct"),
})
public class FavorateProduct extends Favorate {

    @ManyToOne
    public CompanyProduct companyProduct;

    public FavorateProduct() {
    }

    public FavorateProduct(Customer customer, CompanyProduct companyProduct) {
        super(customer);
        this.companyProduct = companyProduct;
    }
}

