package com.archivage.document.policy;

import com.archivage.common.domain.ConfidentialityLevel;
import com.archivage.common.domain.Role;
import com.archivage.common.exception.ApiException;
import com.archivage.document.entity.Document;
import com.archivage.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Règles de lecture documentaire : rôle + service + niveau de confidentialité.
 * <ul>
 *   <li>{@code ADMIN}, {@code ARCHIVISTE} : accès à tous les documents.</li>
 *   <li>{@code AUDITEUR} : aucun accès aux documents (journal d’audit seul).</li>
 *   <li>{@code AGENT} : même périmètre service que {@code LECTEUR}, jusqu’à {@link ConfidentialityLevel#CONFIDENTIAL}.</li>
 *   <li>{@code LECTEUR} : {@link ConfidentialityLevel#PUBLIC} et {@link ConfidentialityLevel#INTERNAL} uniquement.</li>
 * </ul>
 * Périmètre service : document sans service ({@code department_id} NULL) visible par tous les profils restreints ;
 * sinon le service du document doit coïncider avec celui de l’utilisateur. Utilisateur sans service : uniquement
 * documents sans service affecté.
 */
@Service
public class DocumentAccessService {

    public void assertCanRead(User reader, Document doc) {
        if (!canRead(reader, doc)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Accès au document refusé");
        }
    }

    public boolean canRead(User reader, Document doc) {
        Role r = reader.getRole();
        if (r == Role.ADMIN || r == Role.ARCHIVISTE) {
            return true;
        }
        if (r == Role.AUDITEUR) {
            return false;
        }
        if (r != Role.AGENT && r != Role.LECTEUR) {
            return false;
        }
        if (!matchesDepartment(reader, doc)) {
            return false;
        }
        return matchesConfidentiality(r, doc.getConfidentialityLevel());
    }

    private static boolean matchesDepartment(User reader, Document doc) {
        Long docDeptId = doc.getDepartment() != null ? doc.getDepartment().getId() : null;
        Long userDeptId = reader.getDepartment() != null ? reader.getDepartment().getId() : null;
        if (userDeptId == null) {
            return docDeptId == null;
        }
        return docDeptId == null || docDeptId.equals(userDeptId);
    }

    private static boolean matchesConfidentiality(Role role, ConfidentialityLevel level) {
        if (level == null) {
            return true;
        }
        if (level == ConfidentialityLevel.SECRET) {
            return false;
        }
        if (role == Role.LECTEUR) {
            return level == ConfidentialityLevel.PUBLIC || level == ConfidentialityLevel.INTERNAL;
        }
        // AGENT
        return level != ConfidentialityLevel.SECRET;
    }

    /**
     * Ajoute des prédicats SQL (alias table {@code d}) alignés sur {@link #canRead(User, Document)}.
     */
    public void appendReadScope(StringBuilder where, Map<String, Object> params, User reader) {
        Role r = reader.getRole();
        if (r == Role.ADMIN || r == Role.ARCHIVISTE) {
            return;
        }
        if (r == Role.AUDITEUR) {
            where.append(" AND 1 = 0 ");
            return;
        }
        if (r != Role.AGENT && r != Role.LECTEUR) {
            where.append(" AND 1 = 0 ");
            return;
        }

        Long userDeptId = reader.getDepartment() != null ? reader.getDepartment().getId() : null;
        params.put("accessUserDeptId", userDeptId);
        if (userDeptId == null) {
            where.append(" AND d.department_id IS NULL ");
        } else {
            where.append(" AND (d.department_id IS NULL OR d.department_id = :accessUserDeptId) ");
        }

        if (r == Role.LECTEUR) {
            where.append(" AND d.confidentiality_level IN ('PUBLIC','INTERNAL') ");
        } else {
            where.append(" AND d.confidentiality_level IN ('PUBLIC','INTERNAL','CONFIDENTIAL') ");
        }
    }
}
