package ci.itechciv.sigdep.hub.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "patients", schema = "core")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "source_uuid", nullable = false)
    private UUID sourceUuid;

    @Column(length = 1)
    private String sex;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "birth_date_estimated")
    private Boolean birthDateEstimated;

    @Column(name = "birth_place")
    private String birthPlace;

    private String profession;

    @Column(name = "education_level")
    private String educationLevel;

    @Column(name = "marital_status")
    private String maritalStatus;

    private String religion;

    private Boolean voided = false;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public Site getSite() { return site; }
    public void setSite(Site site) { this.site = site; }
    public UUID getSourceUuid() { return sourceUuid; }
    public void setSourceUuid(UUID sourceUuid) { this.sourceUuid = sourceUuid; }
    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public Boolean getBirthDateEstimated() { return birthDateEstimated; }
    public void setBirthDateEstimated(Boolean v) { this.birthDateEstimated = v; }
    public String getBirthPlace() { return birthPlace; }
    public void setBirthPlace(String birthPlace) { this.birthPlace = birthPlace; }
    public String getProfession() { return profession; }
    public void setProfession(String profession) { this.profession = profession; }
    public String getEducationLevel() { return educationLevel; }
    public void setEducationLevel(String educationLevel) { this.educationLevel = educationLevel; }
    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }
    public String getReligion() { return religion; }
    public void setReligion(String religion) { this.religion = religion; }
    public Boolean getVoided() { return voided; }
    public void setVoided(Boolean voided) { this.voided = voided; }
    public Instant getVoidedAt() { return voidedAt; }
    public void setVoidedAt(Instant voidedAt) { this.voidedAt = voidedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
