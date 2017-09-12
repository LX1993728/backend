package models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.List;

@Entity
@NamedQueries({
    @NamedQuery(
        name="findDiningProductsOfCompany",
        query="SELECT cp FROM CompanyProduct cp WHERE cp.company = :company AND cp.isRemoved = false AND cp.product.class = DiningProduct"
    ),
    @NamedQuery(
        name="findVehicleProductsOfCompany",
        query="SELECT cp FROM CompanyProduct cp WHERE cp.company = :company AND cp.isRemoved = false AND cp.product.class = VehicleProduct"
    ),
    @NamedQuery(
        name="findCompanyProductsByProductName",
        query="SELECT cp FROM CompanyProduct cp WHERE cp.product.name LIKE :productName AND cp.isRemoved = false AND cp.isPublished = true"
    ),
    @NamedQuery(
        name="findPublishedProductsOfCompany",
        query="SELECT cp FROM CompanyProduct cp WHERE cp.company = :company AND cp.isRemoved = false AND cp.isPublished = true"
    ),
    @NamedQuery(
        name="findSourceProductsOfCompany",
        query="SELECT cp.source FROM CompanyProduct cp WHERE cp.company = :company AND cp.isRemoved = false"
    ),
    @NamedQuery(
        name="findCompanyProductsOfSource",
        query="SELECT cp FROM CompanyProduct cp WHERE cp.company = :company  AND cp.source = :source"
    ),
    @NamedQuery(
                name="findCompanyProductsOfManufacturerById",
                query="SELECT cp FROM CompanyProduct cp WHERE cp.company = :company  AND cp.id = :companyProductId"
     ),
})
public class CompanyProduct {

    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    public Company company;

    @ManyToOne
    public Product product;

    @JsonIgnore
    public int quantity;

    @OneToOne
    public CompanyProductPrice currentPrice;

    @OneToMany(mappedBy="companyProduct")
    @JsonIgnore
    public List<CompanyProductPrice> historyPrices;

    public boolean isPublished;

    public boolean canBook;

    @JsonIgnore
    public boolean isRemoved;

    @OneToOne
    public CompanyProduct source;

    @OneToMany(mappedBy="companyProduct")
    public List<CompanyProductService> postSaleServices;

    @JsonIgnore
    @Transient
    public int count; //临时计算产品的数量
    public CompanyProduct() {
    }

    public CompanyProduct(Company company, Product product, int quantity) {
        this.company = company;
        this.product = product;
        this.quantity = quantity;
        this.isPublished = false;
        this.isRemoved = false;
        this.canBook = true;
        this.source = null;
    }

    public CompanyProduct(Company company, CompanyProduct source, int quantity) {
        this.company = company;
        this.product = source.product;
        source.quantity -= quantity;
        this.quantity = quantity;
        this.isPublished = false;
        this.isRemoved = false;
        this.canBook = true;
        this.source = source;
    }

    public void setPrice(CompanyProductPrice price) {
        this.currentPrice = price;
    }
}

