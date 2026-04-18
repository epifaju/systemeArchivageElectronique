package com.archivage.metadata.entity;

import com.archivage.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "document_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentTypeEntity extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "label_fr", nullable = false, length = 200)
    private String labelFr;

    @Column(name = "label_pt", nullable = false, length = 200)
    private String labelPt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_fields")
    private String requiredFields;

    @Column(nullable = false)
    private Boolean active;
}
