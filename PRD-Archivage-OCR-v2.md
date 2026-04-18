# Système d'Archivage Électronique avec OCR — PRD v2.0

> **Product Requirements Document — Version 2.0**
> Optimisé pour Cursor AI · Spring Boot · React JS · PostgreSQL · Tesseract OCR

| Champ | Valeur |
|---|---|
| Version | 2.0 — PRD Optimisé Cursor AI |
| Statut | Prêt pour développement |
| Stack Backend | Java 21 · Spring Boot 3.x · PostgreSQL · JWT |
| Stack Frontend | React JS · Vite · Tailwind CSS · i18next |
| OCR | Tesseract + OCRmyPDF · PDF/A |
| Déploiement | Docker Compose · Local / Dell OptiPlex |
| Langues | Français (fr) · Portugais (pt) |
| Architecture | Monolithe modulaire + Worker OCR séparé |

---

## Table des Matières

1. [Contexte et Problème](#1-contexte-et-problème)
2. [Utilisateurs Cibles](#2-utilisateurs-cibles)
3. [Exigences Fonctionnelles Détaillées](#3-exigences-fonctionnelles-détaillées)
4. [Architecture Technique](#4-architecture-technique)
5. [API REST — Référence Complète](#5-api-rest--référence-complète)
6. [Internationalisation (i18n)](#6-internationalisation-i18n)
7. [Déploiement Local — Docker Compose](#7-déploiement-local--docker-compose)
8. [Exigences de Sécurité](#8-exigences-de-sécurité)
9. [Écrans Principaux & UX](#9-écrans-principaux--ux)
10. [Roadmap & Critères d'Acceptation](#10-roadmap--critères-dacceptation)
11. [Risques & Mitigation](#11-risques--mitigation)
12. [Prompts Cursor AI — Guide de Développement](#12-prompts-cursor-ai--guide-de-développement)
13. [Recommandations Finales](#13-recommandations-finales)

---

## 1. Contexte et Problème

Dans de nombreuses structures, les anciens documents papiers sont dispersés, difficiles à rechercher, sujets à la détérioration, et dépendants d'une connaissance humaine implicite du classement. Cette situation entraîne pertes de temps, risques documentaires et manque de traçabilité.

**Problèmes résolus par le système :**
- Recherche manuelle lente dans des archives physiques
- Risque de détérioration ou de perte de documents originaux
- Absence d'indexation et de recherche plein texte
- Traçabilité des accès inexistante
- Classement incohérent selon les opérateurs
- Impossibilité d'accès multi-postes simultanés

### Vision Produit

Remplacer une gestion papier lente et risquée par une plateforme numérique locale capable de centraliser les documents, accélérer la recherche, fiabiliser le classement, et améliorer la traçabilité des accès. Le système transforme des scans ou imports de documents papier en documents indexés et consultables par recherche plein texte.

---

## 2. Utilisateurs Cibles

| Rôle | Profil | Besoins principaux | Permissions |
|---|---|---|---|
| Administrateur | IT / DSI | Gérer comptes, rôles, configuration système | Toutes fonctions, paramétrage |
| Archiviste | Référent documentaire | Valider docs, corriger métadonnées, superviser qualité | Upload, validation, édition |
| Agent de saisie | Opérateur terrain | Scanner, importer, préparer les documents | Upload, saisie métadonnées |
| Utilisateur métier | Collaborateur interne | Rechercher et consulter les documents autorisés | Consultation, recherche |
| Auditeur | Conformité / Direction | Consulter journaux et historiques d'accès | Lecture audit log uniquement |

---

## 3. Exigences Fonctionnelles Détaillées

### 3.1 Authentification & Autorisation

Implémentation avec Spring Security + JWT (access token court + refresh token).

| Fonctionnalité | Description | Priorité |
|---|---|---|
| Login / Logout | Authentification identifiant + mot de passe, émission JWT | P0 — MVP |
| Refresh Token | Token de durée courte (15 min), refresh sécurisé (7 jours) | P0 — MVP |
| Gestion des rôles | ADMIN, ARCHIVISTE, AGENT, LECTEUR, AUDITEUR | P0 — MVP |
| RBAC fin | Contrôle par rôle + service + niveau de confidentialité | P0 — MVP |
| Audit connexions | Journalisation des tentatives réussies et échouées | P0 — MVP |
| Expiration session | Déconnexion automatique après inactivité configurable | P1 |

#### 🤖 Prompt Cursor — Module Auth

```
Génère le module d'authentification Spring Boot avec :
- SecurityConfig.java : Spring Security, désactivation CSRF, stateless sessions
- JwtService.java : génération, validation, extraction claims
- AuthController.java : POST /api/auth/login, /refresh, /logout
- UserDetailsServiceImpl.java : chargement utilisateur depuis PostgreSQL
- RefreshToken : entité JPA avec expiration et révocation
- DTOs : LoginRequest, AuthResponse (accessToken + refreshToken + expiresIn)
- Tests unitaires JUnit 5 + Mockito pour JwtService et AuthController
```

---

### 3.2 Capture & Import Documentaire

Multi-canaux d'entrée : upload manuel, drag-and-drop, import par lot, dossier surveillé (optionnel).

| Fonctionnalité | Description | Priorité |
|---|---|---|
| Upload unitaire | Upload via formulaire web, drag-and-drop | P0 — MVP |
| Import par lot | ZIP ou sélection multiple, traitement asynchrone | P0 — MVP |
| Formats supportés | PDF, JPG/JPEG, PNG, TIFF | P0 — MVP |
| Validation fichier | Vérification MIME, taille max configurable, anti-virus ClamAV | P0 — MVP |
| Prévisualisation | Aperçu avant confirmation d'upload | P1 |
| Dossier surveillé | Surveillance d'un répertoire local pour ingestion automatique | P2 |
| Détection doublons | SHA-256 pour éviter les doublons à l'import | P1 |

#### 🤖 Prompt Cursor — Module Upload

```
Crée le service d'upload documentaire Spring Boot :
- DocumentUploadService.java : validation MIME (Tika), calcul SHA-256, stockage FileSystem/MinIO
- DocumentController.java : POST /api/documents/upload (multipart), POST /api/documents/import-batch
- Entité Document : id, uuid, titre, originalPath, ocrPath, mimeType, sha256, status, createdAt
- Enum DocumentStatus : PENDING, PROCESSING, OCR_SUCCESS, OCR_PARTIAL, OCR_FAILED, VALIDATED, ARCHIVED
- FileStorageService.java : stockage dans répertoire configurable + sous-arborescence par date
- Validation : @Valid, BindingResult, retour 400 avec erreurs structurées
- Intégration ClamAV optionnelle via socket UNIX ou TCP
```

---

### 3.3 Pipeline OCR Asynchrone

Le pipeline OCR est le cœur technique du système. Il doit être robuste, monitored et non-bloquant pour l'interface utilisateur.

> **Flux OCR nominal :** Document reçu → Validation format/taille → Prétraitement → OCR Tesseract (fra/por) → OCRmyPDF PDF/A → Extraction texte → Indexation → Statut SUCCESS

| Étape | Technologie | Description |
|---|---|---|
| Réception job | Spring @Async / Quartz | Création OCRJob, status PENDING, enqueue |
| Validation | Apache Tika | Vérification MIME réel vs déclaré, taille |
| Prétraitement | ImageMagick (optionnel) | Débruitage, binarisation si qualité faible |
| OCR Tesseract | tesseract-ocr + packs fra/por | Reconnaissance texte multi-langues |
| Post-traitement | OCRmyPDF | Génération PDF searchable + PDF/A, rotation auto, deskew |
| Extraction texte | PDFBox / pdftotext | Texte brut pour indexation PostgreSQL full-text |
| Indexation | PostgreSQL tsvector | Index GIN pour recherche full-text rapide |
| Statut & logs | OCRJob entity | Mise à jour statut, durée, nb pages, erreurs |

**Statuts des jobs OCR :**

| Statut | Code | Description | Action possible |
|---|---|---|---|
| En attente | PENDING | Job créé, pas encore démarré | Annuler |
| En cours | PROCESSING | OCR en cours d'exécution | Monitorer |
| Succès | OCR_SUCCESS | OCR complet, texte extrait | Valider métadonnées |
| Succès partiel | OCR_PARTIAL | OCR incomplet (pages illisibles) | Revalider ou relancer |
| Échec | OCR_FAILED | Erreur technique OCR | Relancer / Rejeter |
| À revalider | NEEDS_REVIEW | Qualité OCR insuffisante | Corriger manuellement |

#### 🤖 Prompt Cursor — Pipeline OCR

```
Génère le pipeline OCR complet Spring Boot :
- OCRJobService.java : création job, orchestration, mise à jour statut
- OCRWorker.java : @Async, appel ProcessBuilder pour ocrmypdf CLI
- OCRmyPDF command : ocrmypdf --lang fra+por --deskew --rotate-pages --output-type pdfa input.pdf output.pdf
- OCRJob entity : id, documentId, status, startedAt, completedAt, errorMessage, nbPages, ocrEngine
- TextExtractionService.java : extraction texte brut depuis PDF OCRisé
- DocumentSearchIndex : mise à jour tsvector PostgreSQL après OCR
- Gestion erreurs : retry 3x avec backoff, statut FAILED + log détaillé
- Health check : endpoint /api/admin/ocr-queue avec stats jobs en cours/en attente
```

---

### 3.4 Gestion des Métadonnées

Chaque document est enrichi par des métadonnées structurées garantissant classement fiable et recherche efficace.

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| title | String (255) | Oui | Titre ou intitulé du document |
| documentType | Enum / FK | Oui | Type documentaire paramétrable |
| folderNumber | String (100) | Oui | Numéro de dossier de référence |
| documentDate | Date | Oui | Date du document original |
| language | Enum | Oui | FRENCH, PORTUGUESE, OTHER |
| confidentialityLevel | Enum | Oui | PUBLIC, INTERNAL, CONFIDENTIAL, SECRET |
| department | FK | Non | Service / département propriétaire |
| externalReference | String (100) | Non | Référence externe (courrier, contrat...) |
| tags | List\<String\> | Non | Étiquettes libres pour catégorisation |
| author | String (200) | Non | Auteur ou signataire du document |
| notes | Text | Non | Notes internes sur le document |

#### 🤖 Prompt Cursor — Métadonnées

```
Crée le système de métadonnées documentaires :
- Document entity avec tous les champs ci-dessus + JPA annotations
- DocumentType entity : id, code, labelFr, labelPt, requiredFields (JSON), active
- MetadataTemplate : modèles de saisie par type documentaire
- DocumentUpdateDTO : validation @NotBlank, @NotNull, @Size sur champs obligatoires
- PUT /api/documents/{id}/metadata : mise à jour partielle des métadonnées
- Suggestions automatiques : extraction NLP simple (dates, références) depuis texte OCR
```

---

### 3.5 Recherche & Consultation

La recherche est l'axe central du produit. Elle combine filtres métier et recherche plein texte sur le contenu OCR.

| Fonctionnalité | Implémentation | Priorité |
|---|---|---|
| Recherche globale | Full-text PostgreSQL (tsvector/tsquery) | P0 — MVP |
| Filtres avancés | Type, date, nom, numéro dossier, service, langue, statut | P0 — MVP |
| Tri multicritère | Pertinence, date doc, date ajout, type | P0 — MVP |
| Pagination | Page + size + tri, réponse PageDto | P0 — MVP |
| Mise en surbrillance | ts_headline() PostgreSQL dans les résultats | P1 |
| Recherches sauvegardées | Entité SavedSearch par utilisateur | P2 |
| Export résultats | Export CSV/Excel de la liste filtrée | P2 |

#### 🤖 Prompt Cursor — Recherche

```
Génère le module de recherche documentaire :
- SearchController.java : GET /api/search?q=...&type=...&dateFrom=...&dateTo=...&folderNumber=...
- POST /api/search/advanced : body SearchRequest avec critères multiples
- SearchService.java : construction dynamique de requête JPA Specification
- Index PostgreSQL :
  CREATE INDEX idx_document_search ON documents
  USING GIN(to_tsvector('french', coalesce(title,'') || ' ' || coalesce(ocr_text,'')));
- SearchResultDto : id, uuid, title, documentType, folderNumber, documentDate, status, highlight, score
- PageResponseDto<T> : content, page, size, totalElements, totalPages
```

---

### 3.6 Viewer Documentaire

| Fonctionnalité | Description | Priorité |
|---|---|---|
| Viewer PDF intégré | PDF.js dans React, sans téléchargement obligatoire | P0 — MVP |
| Aperçu image | Affichage direct JPG/PNG dans navigateur | P0 — MVP |
| Métadonnées sidebar | Panneau latéral avec tous les champs du document | P0 — MVP |
| Historique accès | Dernières consultations et modifications | P1 |
| Téléchargement original | GET /api/documents/{id}/download/original | P0 — MVP |
| Téléchargement OCR | GET /api/documents/{id}/download/ocr (PDF/A) | P0 — MVP |
| Zoom / navigation | Contrôles PDF.js : zoom, page précédente/suivante | P1 |

---

## 4. Architecture Technique

### 4.1 Vue d'ensemble

| Composant | Technologie | Version | Rôle |
|---|---|---|---|
| Frontend | React JS + Vite + Tailwind CSS | React 18 / Vite 5 | Interface utilisateur responsive |
| Backend API | Spring Boot | 3.x / Java 21 | API métier, sécurité, orchestration |
| Sécurité | Spring Security + JWT | 6.x | Authentification & autorisation |
| Base de données | PostgreSQL | 16 | Métadonnées, audit, index full-text |
| OCR Worker | Service Java séparé | Spring Boot | Pipeline OCR isolé, jobs asynchrones |
| OCR Engine | Tesseract + OCRmyPDF | 5.x / 16.x | Reconnaissance texte, PDF/A |
| File Storage | FileSystem local / MinIO | — | Documents originaux et OCRisés |
| Message Queue | RabbitMQ ou Quartz | — | File de jobs OCR asynchrones |
| Reverse Proxy | Nginx ou Caddy | — | HTTPS local, routing frontend/backend |
| Orchestration | Docker Compose | — | Déploiement mono-machine simplifié |

---

### 4.2 Structure du Projet Backend

#### 🤖 Prompt Cursor — Structure Spring Boot

```
Génère la structure de projet Spring Boot monolithe modulaire :

src/main/java/com/archivage/
├── ArchivageApplication.java
├── config/           # SecurityConfig, WebConfig, SwaggerConfig, AsyncConfig
├── auth/             # AuthController, JwtService, UserDetailsServiceImpl
├── user/             # User, Role, UserService, UserController
├── document/         # Document, DocumentService, DocumentController, DocumentRepository
├── metadata/         # DocumentType, MetadataField, MetadataService
├── ocr/              # OCRJob, OCRWorker, OCRJobService, OCRJobRepository
├── search/           # SearchService, SearchController, SearchSpecification
├── storage/          # FileStorageService, StorageConfig
├── audit/            # AuditLog, AuditService, AuditInterceptor
├── admin/            # AdminController, SystemStatsService
└── common/           # PageResponseDto, ErrorResponse, BaseEntity, Constants
```

---

### 4.3 Structure du Projet Frontend

#### 🤖 Prompt Cursor — Structure React

```
Génère la structure React + Vite + Tailwind CSS :

src/
├── main.jsx / App.jsx
├── i18n/             # config i18next, locales/fr.json, locales/pt.json
├── api/              # axios instance, authApi, documentApi, searchApi, adminApi
├── store/            # Zustand ou Context : authStore, uiStore
├── router/           # React Router v6, ProtectedRoute, routes.jsx
├── pages/            # LoginPage, DashboardPage, UploadPage, SearchPage,
│                       DocumentPage, AdminPage, AuditPage, OcrQueuePage
├── components/
│   ├── layout/       # AppLayout, Sidebar, TopBar, Breadcrumb
│   ├── document/     # DocumentCard, DocumentViewer, MetadataPanel, StatusBadge
│   ├── search/       # SearchBar, FilterPanel, SearchResults, AdvancedSearch
│   ├── upload/       # DropZone, UploadProgress, BatchImportModal
│   ├── admin/        # UserTable, DocumentTypeForm, SystemStats
│   └── ui/           # Button, Input, Select, Modal, Toast, Spinner, Badge
├── hooks/            # useAuth, useDocuments, useSearch, useOcrJobs, useI18n
└── utils/            # formatDate, formatFileSize, downloadFile, cn (classnames)
```

---

### 4.4 Schéma de Base de Données

#### 🤖 Prompt Cursor — PostgreSQL Schema

```sql
-- Migration Flyway : V1__init.sql

-- Utilisateurs & Sécurité
CREATE TABLE users (
  id           BIGSERIAL PRIMARY KEY,
  uuid         UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
  username     VARCHAR(100) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  email        VARCHAR(255) UNIQUE,
  full_name    VARCHAR(200),
  role         VARCHAR(50) NOT NULL,
  department_id BIGINT REFERENCES departments(id),
  active       BOOLEAN DEFAULT true,
  created_at   TIMESTAMPTZ DEFAULT NOW(),
  updated_at   TIMESTAMPTZ DEFAULT NOW()
);

-- Documents
CREATE TABLE documents (
  id                   BIGSERIAL PRIMARY KEY,
  uuid                 UUID UNIQUE NOT NULL DEFAULT gen_random_uuid(),
  title                VARCHAR(500) NOT NULL,
  document_type_id     BIGINT REFERENCES document_types(id),
  folder_number        VARCHAR(100),
  document_date        DATE,
  archive_date         TIMESTAMPTZ,
  status               VARCHAR(50) NOT NULL DEFAULT 'PENDING',
  language             VARCHAR(10),
  confidentiality_level VARCHAR(20) DEFAULT 'INTERNAL',
  original_path        TEXT,
  ocr_path             TEXT,
  mime_type            VARCHAR(100),
  ocr_text             TEXT,
  search_vector        TSVECTOR,
  file_size            BIGINT,
  page_count           INT,
  sha256               VARCHAR(64),
  uploaded_by          BIGINT REFERENCES users(id),
  validated_by         BIGINT REFERENCES users(id),
  is_deleted           BOOLEAN DEFAULT false,
  deleted_at           TIMESTAMPTZ,
  created_at           TIMESTAMPTZ DEFAULT NOW(),
  updated_at           TIMESTAMPTZ DEFAULT NOW()
);

-- Index full-text
CREATE INDEX idx_documents_search   ON documents USING GIN(search_vector);
CREATE INDEX idx_documents_type     ON documents(document_type_id);
CREATE INDEX idx_documents_folder   ON documents(folder_number);
CREATE INDEX idx_documents_date     ON documents(document_date);
CREATE INDEX idx_documents_status   ON documents(status);

-- OCR Jobs
CREATE TABLE ocr_jobs (
  id             BIGSERIAL PRIMARY KEY,
  document_id    BIGINT NOT NULL REFERENCES documents(id),
  status         VARCHAR(50) NOT NULL DEFAULT 'PENDING',
  started_at     TIMESTAMPTZ,
  completed_at   TIMESTAMPTZ,
  duration_ms    BIGINT,
  ocr_engine     VARCHAR(50) DEFAULT 'tesseract',
  ocr_lang       VARCHAR(50),
  error_message  TEXT,
  log_output     TEXT,
  page_count     INT,
  created_at     TIMESTAMPTZ DEFAULT NOW(),
  updated_at     TIMESTAMPTZ DEFAULT NOW()
);

-- Audit Log
CREATE TABLE audit_logs (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT REFERENCES users(id),
  action        VARCHAR(100) NOT NULL,
  resource_type VARCHAR(50),
  resource_id   BIGINT,
  details       JSONB,
  ip_address    VARCHAR(45),
  user_agent    TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user     ON audit_logs(user_id);
CREATE INDEX idx_audit_resource ON audit_logs(resource_type, resource_id);
CREATE INDEX idx_audit_date     ON audit_logs(created_at);

-- Trigger mise à jour search_vector
CREATE OR REPLACE FUNCTION update_document_search_vector()
RETURNS TRIGGER AS $$
BEGIN
  NEW.search_vector :=
    setweight(to_tsvector('french', coalesce(NEW.title, '')), 'A') ||
    setweight(to_tsvector('french', coalesce(NEW.folder_number, '')), 'B') ||
    setweight(to_tsvector('french', coalesce(NEW.ocr_text, '')), 'C');
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_document_search_vector
BEFORE INSERT OR UPDATE ON documents
FOR EACH ROW EXECUTE FUNCTION update_document_search_vector();
```

---

## 5. API REST — Référence Complète

| Méthode | Endpoint | Rôle requis | Description |
|---|---|---|---|
| POST | /api/auth/login | Public | Connexion, retourne access + refresh token |
| POST | /api/auth/refresh | Public | Rafraîchir l'access token |
| POST | /api/auth/logout | Authentifié | Révoquer le refresh token |
| POST | /api/documents/upload | AGENT, ARCHIVISTE | Upload document unique |
| POST | /api/documents/import-batch | AGENT, ARCHIVISTE | Import de lot (multipart ou ZIP) |
| GET | /api/documents | Authentifié | Liste paginée avec filtres |
| GET | /api/documents/{id} | Authentifié | Fiche document complète |
| PUT | /api/documents/{id}/metadata | AGENT, ARCHIVISTE | Mise à jour métadonnées |
| PUT | /api/documents/{id}/status | ARCHIVISTE | Changer le statut (valider, archiver...) |
| POST | /api/documents/{id}/reprocess-ocr | ARCHIVISTE, ADMIN | Relancer l'OCR |
| GET | /api/documents/{id}/download/original | Authentifié | Télécharger fichier original |
| GET | /api/documents/{id}/download/ocr | Authentifié | Télécharger PDF OCRisé |
| GET | /api/documents/{id}/preview | Authentifié | Stream pour viewer PDF |
| GET | /api/search | Authentifié | Recherche simple par mot-clé + filtres |
| POST | /api/search/advanced | Authentifié | Recherche multicritère avancée |
| GET | /api/admin/users | ADMIN | Liste des utilisateurs |
| POST | /api/admin/users | ADMIN | Créer un utilisateur |
| PUT | /api/admin/users/{id} | ADMIN | Modifier un utilisateur |
| GET | /api/admin/document-types | ADMIN | Types documentaires |
| POST | /api/admin/document-types | ADMIN | Créer un type documentaire |
| GET | /api/admin/ocr-queue | ADMIN, ARCHIVISTE | File de jobs OCR + stats |
| GET | /api/admin/audit-logs | ADMIN, AUDITEUR | Journal d'audit paginé |
| GET | /api/admin/dashboard | ADMIN | Statistiques système |
| GET | /actuator/health | Public | Health check applicatif |

---

## 6. Internationalisation (i18n)

L'interface est disponible en français (fr) et portugais (pt). L'OCR supporte également les deux langues via les packs Tesseract correspondants.

### 6.1 Interface Utilisateur

#### 🤖 Prompt Cursor — i18n React

```
Configure i18next dans React pour FR et PT :
- i18n/index.js : initialisation i18next avec react-i18next, détection langue navigateur
- locales/fr.json : toutes les clés de traduction en français
- locales/pt.json : toutes les clés de traduction en portugais
- Exemple clés : auth.login, auth.password, document.upload, document.status.*,
                  search.placeholder, errors.required, errors.fileTooBig
- Sélecteur de langue : composant LanguageSelector dans la TopBar
- Persistance : langue sauvegardée dans localStorage
```

### 6.2 OCR Multilingue

> ⚠️ **Configuration OCR Tesseract — Langues supportées V1**
> - Français : paquet apt `tesseract-ocr-fra`
> - Portugais : paquet apt `tesseract-ocr-por`
> - Commande OCRmyPDF : `--lang fra+por` (détection automatique ou choix utilisateur)
> - Paramètre document : `language = FRENCH | PORTUGUESE | MULTILINGUAL`
> - En cas de langue OCR non installée : statut `OCR_FAILED` + message fonctionnel clair

---

## 7. Déploiement Local — Docker Compose

### 7.1 Configuration Matérielle Recommandée

| Composant | Minimum | Recommandé | Commentaire |
|---|---|---|---|
| CPU | 4 cœurs | 8 cœurs | Traitement OCR parallèle bénéficie de plus de cœurs |
| RAM | 8 Go | 16 Go | Spring Boot (1 Go) + PostgreSQL (1 Go) + OCR (2-4 Go) |
| Stockage OS | SSD 50 Go | SSD 100 Go | Système, Docker, logs |
| Stockage documents | 500 Go HDD | 1 To SSD | SSD améliore fortement les perfs d'accès |
| Réseau | 100 Mbps LAN | Gigabit LAN | Pour usage multi-postes |
| OS | Ubuntu 22.04 LTS | Ubuntu 22.04/24.04 LTS | Support long terme, apt packages |

### 7.2 Structure Docker Compose

#### 🤖 Prompt Cursor — docker-compose.yml

```yaml
# Génère docker-compose.yml pour déploiement local complet

services:

  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - ./data/postgres:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER}"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build: ./backend
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      STORAGE_PATH: /app/documents
      OCR_WORKERS: 2
    volumes:
      - ./data/documents:/app/documents
    ports:
      - "8080:8080"

  frontend:
    build: ./frontend
    restart: unless-stopped
    ports:
      - "3000:80"

  nginx:
    image: nginx:alpine
    restart: unless-stopped
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/certs:/etc/nginx/certs:ro
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - backend
      - frontend

  # Optionnel : worker OCR séparé
  ocr-worker:
    build: ./ocr-worker
    restart: unless-stopped
    volumes:
      - ./data/documents:/app/documents
    environment:
      BACKEND_URL: http://backend:8080
      OCR_CONCURRENCY: 2
```

### 7.3 Dockerfile Backend

#### 🤖 Prompt Cursor — Dockerfile multi-stage Spring Boot

```dockerfile
# Stage 1 : Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# Stage 2 : Runtime
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache \
    tesseract-ocr \
    tesseract-ocr-data-fra \
    tesseract-ocr-data-por \
    ghostscript \
    curl

RUN pip3 install ocrmypdf

# Utilisateur non-root
RUN addgroup -S archivage && adduser -S archivage -G archivage
USER archivage

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

HEALTHCHECK --interval=30s --timeout=10s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]
```

---

## 8. Exigences de Sécurité

| Exigence | Implémentation | Priorité |
|---|---|---|
| Mots de passe hashés | BCrypt strength 12 via Spring Security | P0 |
| JWT access token | Durée 15 min, signature HS256 ou RS256 | P0 |
| Refresh token | 7 jours, stocké en base, révocable | P0 |
| HTTPS local | Nginx + certificat self-signed ou Let's Encrypt local | P1 |
| Validation MIME | Apache Tika vérification type réel vs extension déclarée | P0 |
| Taille max fichier | Configurable, défaut 50 Mo par fichier | P0 |
| Audit trail complet | Toutes actions créent une AuditLog entry | P0 |
| Suppression logique | Soft delete uniquement (isDeleted + deletedAt) | P0 |
| ClamAV antivirus | Scan à l'import, rejet si menace détectée | P1 |
| Contrôle accès fichiers | Vérification ownership + rôle avant chaque download | P0 |
| CORS configuré | Origins whitelist via configuration Spring | P0 |
| Rate limiting | Bucket4j sur endpoints auth, configurable | P1 |

---

## 9. Écrans Principaux & UX

| Écran | Route | Composants clés | Description |
|---|---|---|---|
| Connexion | /login | LoginForm, LanguageSelector | Auth JWT, sélection langue |
| Tableau de bord | /dashboard | StatsCards, RecentDocuments, OcrQueueWidget | Vue synthétique + activité récente |
| Upload / Scan | /upload | DropZone, BatchImport, MetadataForm, UploadProgress | Upload + saisie métadonnées initiales |
| Liste documents | /documents | DocumentTable, FilterPanel, StatusBadge, Pagination | Liste filtrée et triable |
| Recherche avancée | /search | SearchBar, AdvancedFilters, ResultsList, Highlight | Full-text + filtres combinés |
| Fiche document | /documents/:id | PdfViewer, MetadataPanel, OcrStatus, AuditHistory | Viewer + métadonnées + actions |
| File OCR | /admin/ocr-queue | JobTable, JobStatusBadge, RetryButton, Stats | Monitoring des jobs OCR |
| Administration | /admin | UserTable, DocTypeConfig, SystemSettings | Gestion utilisateurs et config |
| Journal d'audit | /admin/audit | AuditTable, DateRangePicker, ActionFilter | Historique actions système |

### 9.1 Design System

| Élément | Spécification |
|---|---|
| Police | Inter (system-ui fallback) — tailles : 12/14/16/20/24/32px |
| Couleurs primaires | Bleu corporate `#1E3A5F` (dark), `#2E6DA4` (mid), `#4A90D9` (accent) |
| Couleurs états | Vert `#1A7A4A` (succès), Orange `#C05621` (warning), Rouge `#C53030` (erreur) |
| Mode | Clair prioritaire — mode sombre optionnel via classe `dark` Tailwind |
| Responsive | Desktop first (1280px+) — tablette secondaire (768px+) |
| Accessibilité | WCAG AA — focus visible, aria-labels, keyboard nav |
| Composants | Headless UI ou Radix UI + Tailwind classes custom |

#### 🤖 Prompt Cursor — Composant DocumentCard

```
Crée le composant React DocumentCard avec Tailwind CSS :
Props : document { id, uuid, title, documentType, folderNumber, documentDate,
                   status, language, confidentialityLevel }

- Badge de statut coloré :
    PENDING=gris, PROCESSING=bleu, OCR_SUCCESS=vert, OCR_FAILED=rouge, VALIDATED=violet
- Badge de confidentialité :
    PUBLIC=vert, INTERNAL=bleu, CONFIDENTIAL=orange, SECRET=rouge
- Icône de type documentaire
- Date formatée selon locale (fr/pt via i18n)
- Bouton action rapide : Voir / Télécharger / Relancer OCR
- Hover effect et focus accessible (ring-2 ring-blue-500)
```

---

## 10. Roadmap & Critères d'Acceptation

### 10.1 Phases de Développement

| Phase | Contenu | Durée estimée | Livrables clés |
|---|---|---|---|
| Phase 0 — Cadrage | Architecture, BDD schema, Design system, Docker base | 1 semaine | Repo structuré, Docker Compose fonctionnel |
| Phase 1 — MVP | Auth JWT, Upload, OCR async, Recherche, Viewer, i18n | 4-6 semaines | Système fonctionnel de bout en bout |
| Phase 2 — Consolidation | Scan intégré, Admin avancé, Audit détaillé, Sauvegarde | 3-4 semaines | Prêt pour exploitation réelle |
| Phase 3 — Évolution | OpenSearch, Classification auto, Workflows, Reporting | Variable | Montée en charge et intelligence |

### 10.2 Critères d'Acceptation MVP

**Critères métier :**
- Un document téléversé peut être OCRisé et consulté dans l'application
- Le contenu OCR est recherchable par mot-clé
- Les filtres type, date, nom et numéro de dossier fonctionnent
- Les rôles restreignent correctement l'accès aux documents
- L'interface est disponible en français et portugais et commutable
- Un administrateur peut créer des utilisateurs et des types documentaires

**Critères techniques :**
- Déploiement Docker Compose documenté et reproductible
- Démarrage stable sur machine 8 Go RAM / 4 cœurs
- OCR français et portugais fonctionnel avec packs Tesseract installés
- Recherche simple < 2 secondes sur corpus de 1000 documents
- Journal d'audit présent sur toutes les actions sensibles
- Sauvegarde PostgreSQL + documents validée et documentée
- Tous les endpoints protégés par JWT correctement

---

## 11. Risques & Mitigation

| Risque | Impact | Probabilité | Mitigation |
|---|---|---|---|
| Qualité scan insuffisante | OCR dégradé | Élevée | Standardiser 300 dpi, former opérateurs, profils OCR optimisés |
| Matériel sous-dimensionné | Lenteur traitement lots | Moyenne | Worker séparé, jobs nocturnes, SSD obligatoire, monitoring CPU/RAM |
| Métadonnées mal saisies | Recherche dégradée | Élevée | Champs obligatoires, validation, modèles par type documentaire |
| Volume croissant | Saturation recherche | Faible V1 | Index GIN PostgreSQL, évolution vers OpenSearch planifiée en Phase 3 |
| Langues OCR manquantes | Échec traitement | Faible | Dockerfile inclut fra+por, vérification au démarrage + health check |
| Perte de données | Critique | Faible | Sauvegardes automatiques quotidiennes, SHA-256 intégrité, restauration testée |
| Sécurité accès fichiers | Fuite documents | Faible | RBAC sur tous les endpoints, vérification ownership, pas d'URL devinable |

---

## 12. Prompts Cursor AI — Guide de Développement

Cette section regroupe les prompts optimisés à utiliser dans Cursor AI pour générer chaque module du système. Suivre l'ordre recommandé pour un développement itératif cohérent.

### 12.1 Ordre de Développement Recommandé

| Ordre | Module | Dépendances |
|---|---|---|
| 1 | Setup projet | Aucune |
| 2 | BDD & Migrations Flyway | Setup projet |
| 3 | Entités JPA | BDD |
| 4 | Sécurité JWT | Entités JPA |
| 5 | Upload & Storage | Sécurité JWT |
| 6 | Pipeline OCR | Upload |
| 7 | Recherche full-text | OCR Pipeline |
| 8 | Admin & Audit | Recherche |
| 9 | Frontend base | Backend complet |
| 10 | Pages & Composants UI | Frontend base |
| 11 | Docker Compose + scripts | Application complète |

### 12.2 Prompts détaillés par module

#### 🤖 Prompt 1 — Setup Projet Spring Boot

```
Initialise un projet Spring Boot 3.x avec Java 21 et Maven.

Dépendances :
- spring-boot-starter-web
- spring-boot-starter-security
- spring-boot-starter-data-jpa
- spring-boot-starter-validation
- spring-boot-starter-actuator
- postgresql driver
- flyway-core
- lombok
- mapstruct + mapstruct-processor
- springdoc-openapi-starter-webmvc-ui
- jjwt-api + jjwt-impl + jjwt-jackson

Génère :
- application.yml avec profils dev et prod
- Toutes les propriétés externalisées en variables d'environnement
- Application.java avec @SpringBootApplication
- Structure des packages : config, auth, user, document, metadata, ocr, search, storage, audit, admin, common
```

#### 🤖 Prompt 2 — Migrations Flyway

```
Génère les migrations Flyway pour PostgreSQL 16 :

V1__create_users.sql       : tables users, roles, departments, refresh_tokens
V2__create_documents.sql   : tables documents, document_types, document_tags, folders
V3__create_ocr_jobs.sql    : table ocr_jobs avec tous les statuts
V4__create_audit.sql       : table audit_logs avec index sur user_id, resource, date
V5__create_indexes.sql     : index GIN full-text, trigger tsvector auto-update
V6__seed_data.sql          : données initiales (admin user, types documentaires de base)

Respecter les conventions de nommage snake_case.
Inclure les contraintes FK, NOT NULL, DEFAULT, CHECK appropriées.
```

#### 🤖 Prompt 3 — Entités JPA

```
Génère toutes les entités JPA pour le système d'archivage :

BaseEntity abstraite :
- id (BIGSERIAL), uuid (UUID auto-généré), createdAt, updatedAt (@PrePersist/@PreUpdate)

Entités :
- User : username, passwordHash, email, fullName, role (Enum), department (FK), active
- Document : tous les champs du modèle de données + relation vers DocumentType, User
- DocumentType : code, labelFr, labelPt, requiredFields (JSON), active
- OCRJob : documentId, status (Enum), timings, errorMessage, ocrLang
- AuditLog : userId, action, resourceType, resourceId, details (JSON), ipAddress
- Department : code, nameFr, namePt

Utiliser Lombok @Data, @Builder, @NoArgsConstructor, @AllArgsConstructor.
Soft delete via @Where(clause = "is_deleted = false") sur Document.
```

#### 🤖 Prompt 4 — Sécurité JWT complète

```
Génère le module de sécurité Spring Boot complet :

JwtService :
- generateAccessToken(UserDetails) : expire dans 15 min, claims : sub, roles, userId
- generateRefreshToken(UserDetails) : expire dans 7 jours
- validateToken(token) : vérification signature + expiration
- extractUsername(token), extractClaims(token)

SecurityConfig (@Configuration @EnableWebSecurity) :
- Filtre JwtAuthenticationFilter extends OncePerRequestFilter
- Endpoints publics : /api/auth/**, /actuator/health, /swagger-ui/**
- Tous les autres : authentifiés
- Stateless sessions, CSRF désactivé
- Password encoder : BCryptPasswordEncoder(12)

AuthController :
- POST /api/auth/login : LoginRequest → AuthResponse(accessToken, refreshToken, expiresIn, user)
- POST /api/auth/refresh : RefreshRequest → AuthResponse
- POST /api/auth/logout : révocation du refresh token en base

Tests unitaires JUnit 5 + Mockito pour JwtService (génération, validation, expiration).
```

#### 🤖 Prompt 5 — Upload & Stockage

```
Génère le service d'upload et de stockage documentaire :

FileStorageService :
- store(MultipartFile, documentId) → String path
- Arborescence : /documents/{year}/{month}/{uuid}/{filename}
- Calcul SHA-256 du contenu
- Vérification MIME réel avec Apache Tika

DocumentUploadService :
- upload(MultipartFile, UploadRequest, User) → DocumentDto
- Détection doublons par SHA-256 (lever DuplicateDocumentException)
- Validation taille (max configurable via properties)
- Création Document en base avec status PENDING
- Déclenchement OCRJob via OCRJobService

DocumentController :
- POST /api/documents/upload (@RequestPart file + metadata)
- POST /api/documents/import-batch (@RequestParam List<MultipartFile>)
- Retour 201 Created avec DocumentDto
- Gestion erreurs : 400 validation, 409 doublon, 413 fichier trop grand
```

#### 🤖 Prompt 6 — Pipeline OCR

```
Génère le pipeline OCR asynchrone complet :

OCRJobService :
- createJob(documentId) → OCRJob
- executeJob(OCRJob) : @Async("ocrExecutor"), ThreadPoolTaskExecutor configurable
- updateStatus(jobId, status, details)
- retryJob(jobId) : max 3 tentatives avec backoff exponentiel
- getQueueStats() → OcrQueueStatsDto

OCRWorker :
- process(OCRJob) :
  1. Récupérer le fichier original via FileStorageService
  2. Appel ProcessBuilder : ocrmypdf --lang {lang} --deskew --rotate-pages --output-type pdfa input.pdf output.pdf
  3. Capturer stdout/stderr dans log_output
  4. En cas de succès : extraire texte avec PDFBox, mettre à jour ocr_path + ocr_text
  5. Déclencher mise à jour tsvector PostgreSQL
  6. Mettre à jour status + timestamps

Gestion erreurs :
- Fichier corrompu → OCR_FAILED + message "Fichier PDF illisible"
- Langue manquante → OCR_FAILED + message "Pack langue {lang} non installé"
- Timeout (configurable, défaut 10 min) → OCR_FAILED

Admin endpoint : GET /api/admin/ocr-queue → pending, processing, failed counts + liste jobs récents
```

#### 🤖 Prompt 7 — Recherche Full-Text

```
Génère le module de recherche documentaire :

SearchService :
- search(SearchRequest) → PageResponseDto<SearchResultDto>
- Utiliser JPA Specification pour construction dynamique :
  - Filtre q : recherche full-text via tsvector (nativeQuery)
  - Filtres : documentTypeId, dateFrom, dateTo, folderNumber, language, status, departmentId, confidentialityLevel
  - Tri : RELEVANCE (ts_rank), DATE_DESC, DATE_ASC, TITLE_ASC
- Highlight : ts_headline() PostgreSQL sur title et ocr_text

SearchController :
- GET /api/search?q=&type=&dateFrom=&dateTo=&folderNumber=&page=0&size=20&sort=RELEVANCE
- POST /api/search/advanced : body SearchRequest JSON

SearchResultDto : id, uuid, title, documentType, folderNumber, documentDate, status, highlightTitle, highlightContent, score

PageResponseDto<T> : content, currentPage, pageSize, totalElements, totalPages, hasNext, hasPrevious
```

#### 🤖 Prompt 8 — Administration & Audit

```
Génère le module d'administration et d'audit :

AdminController (/api/admin/**) :
- GET/POST/PUT/DELETE /users : CRUD utilisateurs
- GET/POST/PUT /document-types : gestion types documentaires
- GET /dashboard : DocumentStats, OcrStats, StorageStats, RecentActivity
- GET /ocr-queue : liste jobs avec filtres statut + pagination
- GET /audit-logs : journal avec filtres date, user, action, resource + pagination

AuditService :
- log(userId, action, resourceType, resourceId, details, request) → AuditLog
- Méthodes utilitaires : logDocumentView, logDocumentUpload, logDocumentModify, logLogin, logLogout

AuditInterceptor (@Aspect) :
- @Around sur méthodes annotées @Auditable(action = "DOCUMENT_VIEW")
- Extraction automatique user depuis SecurityContext + IP depuis request

SystemStatsService :
- getDocumentStats() : total, par statut, par type, par mois (12 derniers mois)
- getOcrStats() : success rate, avg duration, failed count
- getStorageStats() : espace utilisé, nb fichiers, taille moyenne
```

#### 🤖 Prompt 9 — Frontend Base React

```
Initialise le projet React 18 + Vite 5 + Tailwind CSS :

Installation :
npm create vite@latest archivage-frontend -- --template react
npm install tailwindcss @tailwindcss/forms @tailwindcss/typography
npm install react-router-dom@6 @tanstack/react-query axios
npm install react-hook-form @hookform/resolvers zod
npm install i18next react-i18next i18next-browser-languagedetector
npm install react-pdf @react-pdf-viewer/core
npm install @radix-ui/react-dialog @radix-ui/react-select @radix-ui/react-toast
npm install zustand lucide-react date-fns

Configurer :
- tailwind.config.js avec thème custom (couleurs #1E3A5F, #2E6DA4, #4A90D9)
- i18n/index.js avec détection automatique langue + fallback français
- api/axiosInstance.js : baseURL env, interceptor ajout Bearer token, interceptor refresh auto
- store/authStore.js (Zustand) : user, tokens, login(), logout(), isAuthenticated
- router/routes.jsx : ProtectedRoute, routes publiques (login) et protégées
```

#### 🤖 Prompt 10 — Pages & Composants UI

```
Génère les pages principales avec Tailwind CSS (design sobre, professionnel) :

LoginPage :
- Formulaire centré, logo application, sélecteur langue FR/PT
- Validation Zod, gestion erreur 401, redirection post-login

DashboardPage :
- 4 cards statistiques (documents total, en traitement, validés, échecs OCR)
- Widget file OCR (jobs en cours/en attente)
- Liste 10 derniers documents ajoutés

UploadPage :
- DropZone drag-and-drop (react-dropzone)
- Preview fichier avant upload
- Formulaire métadonnées (titre obligatoire, type, numéro dossier, date, langue)
- Barre de progression upload
- Support upload par lot

DocumentListPage :
- Table avec colonnes : titre, type, numéro dossier, date, statut (badge coloré), actions
- Panneau filtres latéral (type, date range, statut, langue)
- Tri par colonne, pagination

DocumentViewerPage :
- Viewer PDF.js intégré (2/3 largeur)
- Sidebar métadonnées (1/3 largeur) : tous les champs éditables
- Boutons : Télécharger original, Télécharger OCR, Relancer OCR
- Historique accès en bas de sidebar

Composants UI réutilisables :
- StatusBadge : couleurs selon statut document et statut OCR
- ConfidentialityBadge : couleurs selon niveau
- FilterPanel : composant filtres générique avec React Hook Form
- Toast notifications : succès/erreur/info
- ConfirmModal : dialog confirmation pour actions destructives
```

### 12.3 Prompt Général de Contexte

> Copier ce prompt au début de chaque session Cursor pour donner le contexte global :

```
Je développe un système d'archivage électronique avec OCR local.

Stack technique :
- Backend  : Java 21 + Spring Boot 3.x + Spring Security JWT + PostgreSQL 16 + Flyway
- Frontend : React 18 + Vite 5 + Tailwind CSS + TanStack Query + i18next
- OCR      : Tesseract OCR + OCRmyPDF · Déploiement : Docker Compose local (Dell OptiPlex)

Architecture : Monolithe modulaire Spring Boot + Worker OCR séparé (@Async)
Langues interface : Français (fr) et Portugais (pt) via i18next
Langues OCR : fra + por (packs Tesseract installés dans le Dockerfile)

Conventions à respecter :
- DTOs séparés des entités (MapStruct pour mapping)
- Services = logique métier uniquement, Controllers = HTTP uniquement
- Gestion exceptions globale : @ControllerAdvice + ErrorResponse DTO standardisé
- Tous les endpoints API sécurisés par JWT sauf /api/auth/* et /actuator/health
- Soft delete uniquement (is_deleted BOOLEAN + deleted_at TIMESTAMPTZ)
- Tests unitaires JUnit 5 + Mockito sur chaque service
- Validation Bean Validation (@Valid) sur tous les DTOs d'entrée
- Logs structurés SLF4J + Logback (JSON en prod, lisible en dev)
```

---

## 13. Recommandations Finales

**Points clés pour une implémentation réussie :**

- Commencer par la Phase 0 : structure projet + Docker Compose + BDD fonctionnel avant tout code métier
- Tester le pipeline OCR sur vrais documents dès la Phase 1 pour identifier les problèmes de qualité
- Standardiser les scans à **300 dpi minimum** (formation opérateurs obligatoire)
- Mettre en place la sauvegarde PostgreSQL + documents dès le premier déploiement réel
- Valider les packs Tesseract `fra` et `por` dans le Dockerfile avant déploiement
- Utiliser les profils Spring Boot (`dev`/`prod`) pour séparer configurations locales et déploiement
- Documenter la procédure d'installation et de restauration dès la Phase 1
- Planifier les lots OCR en dehors des heures de bureau sur matériel limité

La meilleure approche pour cette initiative est un **monolithe modulaire Spring Boot** avec un worker OCR séparé, un stockage documentaire hors base, PostgreSQL pour les métadonnées et index full-text, et une interface React/Tailwind orientée productivité.

Le couple **Tesseract + OCRmyPDF** constitue une base open source solide pour produire des documents recherchables, exploiter plusieurs langues et viser une conservation durable grâce au support du PDF/A. Avec une UX bien pensée, une file de traitement robuste, une recherche multicritère efficace et une gouvernance documentaire claire, ce système constitue une **GED/SAE locale solide et évolutive**.

---

*PRD v2.0 — Système d'Archivage Électronique avec OCR — Optimisé Cursor AI*
