package models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@NamedQueries({
    @NamedQuery(
        name="findCompanyProductConsume",
        query="SELECT c FROM CompanyProductConsume c WHERE c.companyProduct = :companyProduct AND c.companyProductConsumeServices IS NOT EMPTY"
    ),
    @NamedQuery(
        name="findCustomerCompanyProductConsume",
        query="SELECT c FROM CompanyProductConsume c WHERE c.consume.companyCustomer.customer = :customer AND c.companyProductConsumeServices IS NOT EMPTY"
    ),
    @NamedQuery(
         name="findCustomerCompanyProductConsumeForManufacturer",
         query="SELECT c FROM CompanyProductConsume c WHERE c.consume.companyCustomer.customer = :customer AND c.companyProductConsumeServices IS NOT EMPTY " +
                 "AND EXISTS (SELECT p FROM CompanyProduct p where p = c.companyProduct.source AND p.company = :manufacturer)  AND c.consume.consumeWhen >= :startDate " +
                 "AND c.consume.consumeWhen <= :endDate ORDER BY c.consume.consumeWhen"
    ),
    @NamedQuery(
            name = "findCompanyProductConsumeForManufacturerByCompany",
            query = "SELECT c FROM CompanyProductConsume c WHERE c.companyProduct.company = :company AND  c.companyProduct.source =:companyProduct AND  c.companyProductConsumeServices IS NOT EMPTY " +
                    " AND c.consume.consumeWhen >= :startDate AND c.consume.consumeWhen <= :endDate ORDER BY c.consume.consumeWhen"
    ),
    @NamedQuery(
            name = "findcompanyProductDetailsForManufacturer",
            query = "SELECT c FROM CompanyProductConsume c WHERE c.companyProduct.company = :company AND c.companyProductConsumeServices IS NOT EMPTY AND " +
                    " EXISTS (SELECT p  FROM CompanyProduct p  where p = c.companyProduct.source AND p.company = :manufacturer) AND c.consume.consumeWhen >= :startDate " +
                    "AND c.consume.consumeWhen <= :endDate "
    ),
   @NamedQuery( // TODO: 添加按产品查询的接口
            name = "findCompanyProductConsumeForManufacturerByProduct",
            query = "SELECT c FROM CompanyProductConsume c WHERE c.companyProduct.source = :companyProduct AND c.companyProductConsumeServices IS NOT EMPTY " +
                        "AND EXISTS (SELECT p FROM CompanyProduct p where p = c.companyProduct.source AND p.company = :manufacturer)  AND c.consume.consumeWhen >= :startDate " +
                        "AND c.consume.consumeWhen <= :endDate ORDER BY c.consume.consumeWhen"
        )
})
public class CompanyProductConsume {
    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    public CompanyProduct companyProduct;

    @ManyToOne
    @JsonIgnore
    public Consume consume;

    @OneToMany(mappedBy = "companyProductConsume")
    public List<AntiFakePicture> antiFakePictures;

    public String antiFakeNote;

    public int quantity;

    // redundant with consume.consumeWhen here because consume is JsonIgnore
    public Date consumeWhen;

    @OneToMany(mappedBy="companyProductConsume", cascade=CascadeType.PERSIST)
    public List<CompanyProductConsumeService> companyProductConsumeServices;

    public CompanyProductConsume() {
    }

    public CompanyProductConsume(CompanyProduct companyProduct, Consume consume, int quantity) {
        this.companyProductConsumeServices = new ArrayList<CompanyProductConsumeService>();
        this.antiFakePictures = new ArrayList<AntiFakePicture>();
        this.companyProduct = companyProduct;
        this.consume = consume;
        this.quantity = quantity;
        this.consumeWhen = consume.consumeWhen;
    }
}
