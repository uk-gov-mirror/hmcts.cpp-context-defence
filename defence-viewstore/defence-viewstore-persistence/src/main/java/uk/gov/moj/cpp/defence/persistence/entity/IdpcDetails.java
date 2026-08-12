package uk.gov.moj.cpp.defence.persistence.entity;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idpc_details")
public class IdpcDetails {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "defence_client_id")
    private UUID defenceClientId;

    @Column(name = "material_id")
    private UUID materialId;

    @Column(name = "size")
    private String size;

    @Column(name = "document_name")
    private String documentName;

    @Column(name = "published_date")
    private LocalDate publishedDate;

    @Column(name = "page_count")
    private Integer pageCount;

    //Required for hibernate
    public IdpcDetails() {

    }

    public IdpcDetails(final UUID id, final UUID defClientId, final uk.gov.moj.cpp.defence.IdpcDetails idpcDetailsVo, final String documentName) {
        this.id = id;
        this.defenceClientId = defClientId;
        this.materialId = idpcDetailsVo.getMaterialId();
        this.pageCount = idpcDetailsVo.getPageCount();
        this.size = idpcDetailsVo.getSize();
        this.publishedDate = idpcDetailsVo.getPublishedDate();
        this.documentName = documentName;

    }

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getDefenceClientId() {
        return defenceClientId;
    }

    public void setDefenceClientId(final UUID defenceClientId) {
        this.defenceClientId = defenceClientId;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public void setMaterialId(final UUID materialId) {
        this.materialId = materialId;
    }

    public String getSize() {
        return size;
    }

    public void setSize(final String size) {
        this.size = size;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(final String documentName) {
        this.documentName = documentName;
    }

    public LocalDate getPublishedDate() {
        return publishedDate;
    }

    public void setPublishedDate(final LocalDate publishedDate) {
        this.publishedDate = publishedDate;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(final int pageCount) {
        this.pageCount = pageCount;
    }
}
