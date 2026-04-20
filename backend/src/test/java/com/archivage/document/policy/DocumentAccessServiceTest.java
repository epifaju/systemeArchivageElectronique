package com.archivage.document.policy;

import com.archivage.common.domain.ConfidentialityLevel;
import com.archivage.common.domain.Role;
import com.archivage.common.exception.ApiException;
import com.archivage.common.domain.DocumentStatus;
import com.archivage.document.entity.Document;
import com.archivage.metadata.entity.DocumentTypeEntity;
import com.archivage.user.entity.Department;
import com.archivage.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentAccessServiceTest {

    private DocumentAccessService service;

    private Department dept1;
    private Department dept2;

    @BeforeEach
    void setUp() {
        service = new DocumentAccessService();
        dept1 = Department.builder().code("D1").nameFr("A").namePt("A").build();
        dept1.setId(1L);
        dept2 = Department.builder().code("D2").nameFr("B").namePt("B").build();
        dept2.setId(2L);
    }

    @Test
    void admin_canRead_alwaysTrue() {
        User reader = user(Role.ADMIN, dept1);
        Document doc = doc(ConfidentialityLevel.SECRET, dept2);
        assertThat(service.canRead(reader, doc)).isTrue();
    }

    @Test
    void auditeur_canRead_alwaysFalse() {
        User reader = user(Role.AUDITEUR, null);
        Document doc = doc(ConfidentialityLevel.PUBLIC, null);
        assertThat(service.canRead(reader, doc)).isFalse();
    }

    @Test
    void lecteur_secretDenied() {
        User reader = user(Role.LECTEUR, dept1);
        Document doc = doc(ConfidentialityLevel.SECRET, dept1);
        assertThat(service.canRead(reader, doc)).isFalse();
    }

    @Test
    void lecteur_publicOk() {
        User reader = user(Role.LECTEUR, dept1);
        Document doc = doc(ConfidentialityLevel.PUBLIC, dept1);
        assertThat(service.canRead(reader, doc)).isTrue();
    }

    @Test
    void agent_confidentialOk_sameDepartment() {
        User reader = user(Role.AGENT, dept1);
        Document doc = doc(ConfidentialityLevel.CONFIDENTIAL, dept1);
        assertThat(service.canRead(reader, doc)).isTrue();
    }

    @Test
    void agent_departmentMismatchDenied() {
        User reader = user(Role.AGENT, dept1);
        Document doc = doc(ConfidentialityLevel.INTERNAL, dept2);
        assertThat(service.canRead(reader, doc)).isFalse();
    }

    @Test
    void userWithoutDepartment_onlyNullDepartmentDoc() {
        User reader = user(Role.LECTEUR, null);
        Document docNoDept = doc(ConfidentialityLevel.PUBLIC, null);
        Document docWithDept = doc(ConfidentialityLevel.PUBLIC, dept1);
        assertThat(service.canRead(reader, docNoDept)).isTrue();
        assertThat(service.canRead(reader, docWithDept)).isFalse();
    }

    @Test
    void assertCanRead_throwsWhenDenied() {
        User reader = user(Role.LECTEUR, dept1);
        Document doc = doc(ConfidentialityLevel.SECRET, dept1);
        assertThatThrownBy(() -> service.assertCanRead(reader, doc))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void appendReadScope_admin_noExtraPredicate() {
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        service.appendReadScope(where, params, user(Role.ADMIN, dept1));
        assertThat(where.toString()).isEmpty();
        assertThat(params).isEmpty();
    }

    @Test
    void appendReadScope_auditeur_impossible() {
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        service.appendReadScope(where, params, user(Role.AUDITEUR, null));
        assertThat(where.toString()).contains("1 = 0");
    }

    @Test
    void appendReadScope_lecteur_confidentialityFilter() {
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        service.appendReadScope(where, params, user(Role.LECTEUR, dept1));
        assertThat(where.toString()).contains("PUBLIC");
        assertThat(where.toString()).contains("INTERNAL");
        assertThat(params).containsKey("accessUserDeptId");
    }

    private static User user(Role role, Department dept) {
        User u = User.builder()
                .username("u")
                .passwordHash("x")
                .role(role)
                .active(true)
                .build();
        u.setId(1L);
        u.setDepartment(dept);
        return u;
    }

    private static Document doc(ConfidentialityLevel level, Department dept) {
        DocumentTypeEntity type = DocumentTypeEntity.builder()
                .code("T")
                .labelFr("L")
                .labelPt("L")
                .active(true)
                .build();
        type.setId(1L);
        Document d = Document.builder()
                .title("t")
                .documentType(type)
                .folderNumber("F1")
                .documentDate(java.time.LocalDate.now())
                .status(DocumentStatus.VALIDATED)
                .language(com.archivage.common.domain.DocumentLanguage.FRENCH)
                .confidentialityLevel(level)
                .build();
        d.setId(99L);
        d.setDepartment(dept);
        return d;
    }
}
