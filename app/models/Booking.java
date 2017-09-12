package models;

import java.util.*;
import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@NamedQueries({
    @NamedQuery(
        name="queryBookingForCompanyCustomer",
        query="SELECT b FROM Booking b WHERE b.companyCustomer = :companyCustomer AND status = :status AND bookingWhen > :after"
    ),
    @NamedQuery(
        name="queryBookingForCustomerName",
        query="SELECT b FROM Booking b WHERE b.companyCustomer.customer.name = :name AND b.companyCustomer.company = :company AND b.status = :status AND bookingWhen > :after"
    ),
    @NamedQuery(
        name="queryLatestBooking",
        query="SELECT b FROM Booking b WHERE b.companyCustomer.company = :company AND b.bookingWhen > :someTime"
    ),
    @NamedQuery(
        name="queryBookingsOfToday",
        query="SELECT b FROM Booking b WHERE b.companyCustomer.company = :company AND b.bookingTime > :earlier AND b.bookingTime < :later"
    ),
    @NamedQuery(
        name="queryAllBookingForCustomerName",
        query="SELECT b FROM Booking b WHERE b.companyCustomer.customer.name = :name AND b.companyCustomer.company = :company AND bookingWhen > :after"
    ),
    @NamedQuery(
        name="queryAllBookingForCustomer",
        query="SELECT b FROM Booking b WHERE b.companyCustomer.customer = :customer AND bookingWhen > :after"
    ),
    @NamedQuery(
        name="queryBookingForCustomer",
        query="SELECT b FROM Booking b WHERE b.companyCustomer.customer = :customer AND status = :status AND bookingWhen > :after"
    ),
    @NamedQuery(
        name="queryNewOrAcceptedBooking",
        query="SELECT b FROM Booking b WHERE b.status < 2 "
    ),
})
public class Booking {

    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    public CompanyCustomer companyCustomer;

    @OneToMany(mappedBy="booking", cascade=CascadeType.PERSIST)
    public List<BookingProduct> bookingProducts;

    public Date prevTime;      // previous booking time, used for make change;
    public Date bookingTime;   // latest booking time;

    public Date bookingWhen;

    public int status;    // 0: new; 1: accepted; 2: reject; 3: cancelled; 4: consumed
    public int payStatus; // 0: not pay     1: has pay
    public String customer_note; // customer summit booking can add note
    public String company_note;  // company manager accept booking can add note
    @ManyToOne
    public Consume consume;    // this booking consumed by
    public Booking() {
    }

    public Booking(CompanyCustomer companyCustomer, Date bookingTime) {
        this.bookingProducts = new ArrayList<BookingProduct>();
        this.companyCustomer = companyCustomer;
        this.prevTime = bookingTime;
        this.bookingTime = bookingTime;
        Calendar calendar = Calendar.getInstance();
        this.bookingWhen = calendar.getTime();
        this.status = 0;
        this.consume = null;
        this.payStatus = 0;
    }

    public Booking addBookingProduct(CompanyProduct companyProduct, int quantity) {
        BookingProduct bookingProduct = new BookingProduct(companyProduct, quantity, this);
        this.payStatus = 0;
        this.bookingProducts.add(bookingProduct);
        return this;
    }
}

