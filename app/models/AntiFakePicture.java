package models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;

@Entity
@NamedQueries(
        @NamedQuery(
              name = "queryPicturesOfConsume",
              query = "SELECT p FROM AntiFakePicture p WHERE p.companyProductConsume = :cpConsume"
        )
)
public class AntiFakePicture {

    @Id
    @GeneratedValue
    public Long id;

    public String contentType;

    @Lob
    @Basic(fetch= FetchType.LAZY)
    @JsonIgnore
    public byte[] antiFakePicture;

    @ManyToOne
    @JsonIgnore
    public CompanyProductConsume companyProductConsume;

    public AntiFakePicture () {

    }
}

