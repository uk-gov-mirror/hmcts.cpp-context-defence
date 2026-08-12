package uk.gov.moj.cpp.defence.persistence.entity;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "defence_case")
public class DefenceCase {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "urn")
    private String urn;

    @Column(name = "prosecution_authority_code")
    private String prosecutionAuthorityCode;

    @Column(name = "is_civil")
    private Boolean isCivil = false;

    @Column(name = "is_group_member")
    private Boolean isGroupMember;

    public DefenceCase() {
    }

    public DefenceCase(final UUID id, final String urn, final String prosecutionAuthorityCode, final Boolean isCivil, final Boolean isGroupMember) {
        requireNonNull(id, "id must not be null");
        requireNonNull(urn, "urn must not be null");
        requireNonNull(prosecutionAuthorityCode, "prosecutionAuthorityCode must not be null");

        this.id = id;
        this.urn = urn.toUpperCase();
        this.prosecutionAuthorityCode = prosecutionAuthorityCode;
        setIsCivil(isCivil);
        setIsGroupMember(isGroupMember);
    }

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public String getUrn() {
        return urn;
    }

    public void setUrn(final String urn) {
        this.urn = urn.toUpperCase();
    }

    public String getProsecutionAuthorityCode() {
        return prosecutionAuthorityCode;
    }

    public void setProsecutionAuthorityCode(final String prosecutionAuthorityCode) {
        this.prosecutionAuthorityCode = prosecutionAuthorityCode;
    }

    public Boolean getIsCivil() {
        return isCivil;
    }

    public void setIsCivil(final Boolean isCivil) {
        this.isCivil = nonNull(isCivil) ? isCivil: false;
    }

    public Boolean getIsGroupMember() {
        return isGroupMember;
    }

    public void setIsGroupMember(final Boolean isGroupMember) {
        this.isGroupMember = nonNull(isGroupMember) ? isGroupMember: false;
    }
}
