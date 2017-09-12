package models;

import javax.persistence.*;

@Entity
@NamedQueries({
    @NamedQuery(
        name = "findManufacturerAndCompany",
        query = "SELECT mc FROM ManufacturerAndCompany mc WHERE mc.manufacturer = :manufacturer AND mc.exclusive = :exclusive"
    ),
    @NamedQuery(
        name = "findVaildManufacturerAndCompany",
        query = "SELECT mc FROM ManufacturerAndCompany mc WHERE mc.manufacturer = :manufacturer AND mc.exclusive = :exclusive AND mc.status = 1"
    ),
    @NamedQuery(
        name = "findAllExclusiveCompanies",
        query = "SELECT mc FROM ManufacturerAndCompany mc WHERE mc.manufacturer = :manufacturer AND mc.isRemoved = false"
    ),
    @NamedQuery(
        name = "findAllManufacturers",
        query = "SELECT mc FROM ManufacturerAndCompany mc WHERE mc.exclusive = :exclusive AND mc.status > 0 AND mc.isRemoved = false"
    ),
})
public class ManufacturerAndCompany {

    @Id@GeneratedValue
    public Long id;

    @OneToOne
    public Company manufacturer;

    @OneToOne
    public Company exclusive;


    public int status; // 0: 已拒绝, 1：审核通过, 2：已申请, 3: 已邀请
    public boolean isRemoved; //已删除

    public ManufacturerAndCompany () {

    }

    public ManufacturerAndCompany (Company manufacturer, Company exclusive, int status) {
        this.manufacturer = manufacturer;
        this.exclusive = exclusive;
        this.status = status;
        this.isRemoved = false;
    }
}
