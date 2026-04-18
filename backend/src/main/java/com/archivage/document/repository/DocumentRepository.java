package com.archivage.document.repository;

import com.archivage.document.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document>, DocumentRepositoryCustom {

    @Query("SELECT COUNT(d) FROM Document d WHERE d.isDeleted = false")
    long countActiveDocuments();

    @Query("SELECT d FROM Document d WHERE d.sha256 = :sha256 AND d.isDeleted = false")
    Optional<Document> findBySha256(@Param("sha256") String sha256);

    @EntityGraph(attributePaths = {"documentType", "tags", "department"})
    @Query("SELECT d FROM Document d WHERE d.id = :id AND d.isDeleted = false")
    Optional<Document> findDetailById(@Param("id") Long id);

    @Query("SELECT d FROM Document d WHERE d.uuid = :uuid AND d.isDeleted = false")
    Optional<Document> findByUuid(@Param("uuid") UUID uuid);

    @Query("SELECT d FROM Document d WHERE d.id = :id AND d.isDeleted = false")
    Optional<Document> findActiveById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"documentType", "tags", "department"})
    @Query("SELECT d FROM Document d WHERE d.id = :id AND d.isDeleted = true")
    Optional<Document> findDeletedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"documentType", "tags", "department"})
    @Query(
            value = "SELECT d FROM Document d WHERE d.isDeleted = true",
            countQuery = "SELECT count(d) FROM Document d WHERE d.isDeleted = true"
    )
    Page<Document> findDeletedPage(Pageable pageable);

    @EntityGraph(attributePaths = {"documentType", "tags", "department"})
    @Query("SELECT d FROM Document d WHERE d.id IN :ids AND d.isDeleted = false")
    List<Document> findDetailsByIds(@Param("ids") List<Long> ids);
}
