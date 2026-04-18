-- Mot de passe initial : "password" (BCrypt strength 10 — à changer en production)
INSERT INTO departments (code, name_fr, name_pt)
VALUES ('DEFAULT', 'Direction générale', 'Direção geral');

INSERT INTO document_types (code, label_fr, label_pt, active)
VALUES
    ('COURRIER', 'Courrier', 'Correspondência', TRUE),
    ('CONTRAT', 'Contrat', 'Contrato', TRUE),
    ('COMPTE_RENDU', 'Compte rendu', 'Ata', TRUE),
    ('AUTRE', 'Autre', 'Outro', TRUE);

INSERT INTO users (username, password_hash, email, full_name, role, department_id, active)
VALUES (
    'admin',
    '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
    'admin@localhost',
    'Administrateur',
    'ADMIN',
    (SELECT id FROM departments WHERE code = 'DEFAULT' LIMIT 1),
    TRUE
);
