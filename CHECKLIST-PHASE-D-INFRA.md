# Phase D — Infrastructure & scalabilité (checklist produit / ops)

Document de travail pour aligner **décisions métier**, **exploitation** et **évolutions techniques**. À adapter à votre contexte (cloud, on-prem, taille d’équipe, contraintes légales).

Références utiles dans ce dépôt : `docker-compose.yml`, `docker-compose.https.yml`, `.env.example`, `DEMARRAGE-HTTPS.md`, `nginx/README-https.md`.

---

## 1. Cible d’hébergement & modèle de déploiement

- [ ] **Environnements** : dev / recette / préprod / prod — lesquels sont obligatoires ?
- [ ] **Orchestration** : une machine Docker Compose, plusieurs VMs, Kubernetes, PaaS (ex. conteneurs managés) ?
- [ ] **Mise à jour** : fréquence des releases, fenêtres de maintenance, rollback attendu.
- [ ] **Région / résidence des données** : contraintes géographiques ou sectorielles (hébergement UE, secteur public, etc.).

---

## 2. Disponibilité & SLA (produit)

- [ ] **SLA cible** : disponibilité annuelle souhaitée (ex. 99 % vs 99,9 %).
- [ ] **RTO / RPO** : en cas d’incident majeur, combien de temps pour reprendre le service (RTO) et quelle perte de données max acceptée (RPO) ?
- [ ] **Point unique de défaillance** : aujourd’hui Compose = souvent **un nœud** ; accepter ou planifier redondance (LB, plusieurs instances, DB managée HA).

---

## 3. Base de données (PostgreSQL)

État actuel : conteneur `postgres` avec volume local `./data/postgres` (voir `docker-compose.yml`).

- [ ] **Production** : conserver PostgreSQL en conteneur sur volume disque vs **service managé** (RDS, Azure Database, Cloud SQL, etc.).
- [ ] **Sauvegardes** : fréquence, rétention, test de **restauration** (au moins une fois avant mise en prod).
- [ ] **Haute dispo** : réplication, bascule automatique — nécessite souvent une offre managée ou un runbook DBA.
- [ ] **Migrations** : Flyway au démarrage du backend — valider le processus de migration en recette avant prod.

---

## 4. Stockage des fichiers (documents OCR)

État actuel : volume `./data/documents` monté sur le backend (`STORAGE_PATH` / `/app/documents` en conteneur).

- [ ] **Une seule instance backend** : volume partagé ou disque local peut suffire.
- [ ] **Plusieurs instances backend** : le stockage **doit** être partagé (NFS, SMB, **S3-compatible**, Azure Blob, GCS) ou un seul service d’écriture — **indispensable** avant scale horizontal.
- [ ] **Sauvegarde des fichiers** : cohérente avec la stratégie BDD (snapshots + object lock si besoin).
- [ ] **Capacité** : croissance estimée (Go/an), alertes sur l’espace disque.

---

## 5. OCR & scalabilité du traitement

État actuel : jobs OCR déclenchés en **asynchrone dans le processus** Spring (`ocrExecutor`), paramètre `OCR_WORKERS`, annulation des jobs `PENDING` côté admin.

- [ ] **Charge CPU** : OCR est coûteux — dimensionner CPU/RAM par worker ou isoler les workers.
- [ ] **Scale horizontal** : plusieurs JVM = plusieurs exécuteurs async ; risque de **traitement concurrent** sur la même logique métier si non conçu pour — à valider (file externe, verrous distribués, workers dédiés).
- [ ] **File de messages** (option future) : Redis, RabbitMQ, SQS pour découpler API et workers OCR si le volume l’exige.
- [ ] **Feature flags** déjà utiles : `OCR_MOCK`, `OCR_IMAGEMAGICK_PREPROCESS_ENABLED` — documenter quand les activer en recette.

---

## 6. Réseau, TLS & exposition

- [ ] **HTTPS** : termin au reverse proxy / LB vs passerelle Nginx du dépôt (`docker-compose.https.yml`, `DEMARRAGE-HTTPS.md`).
- [ ] **Certificats** : Let’s Encrypt, PKI interne, certificats managés — et renouvellement automatisé.
- [ ] **CORS** : `CORS_ALLOWED_ORIGINS` doit refléter **exactement** les origines du navigateur (y compris HTTPS et ports).
- [ ] **Firewall** : qui accède au backend (8080), à PostgreSQL, à ClamAV (3310 aujourd’hui exposé sur l’hôte — à restreindre en prod si besoin).

---

## 7. Sécrets & configuration

- [ ] **Secrets** : `JWT_SECRET`, mots de passe BDD — jamais en clair dans le dépôt ; utiliser coffre (Vault, secrets K8s, paramètres managés).
- [ ] **Rotation** : politique pour JWT, comptes techniques, clés API éventuelles.
- [ ] **`.env`** : modèle dans `.env.example` ; processus de déploiement sans copier-coller de secrets dans les tickets.

---

## 8. Observabilité & exploitation

- [ ] **Healthcheck** : Actuator `health` (déjà utilisé dans le Dockerfile backend) — brancher sur le load balancer ou l’orchestrateur.
- [ ] **Logs** : centralisation (ELK, Loki, CloudWatch, etc.), format et rétention.
- [ ] **Métriques** : CPU, mémoire, disque, durée des requêtes, **taille de la file OCR** (stats déjà exposées côté API admin).
- [ ] **Alerting** : seuils (erreurs 5xx, saturation disque, file OCR qui grossit, échecs OCR massifs).

---

## 9. Antivirus (ClamAV)

État actuel : service `clamav` dans Compose.

- [ ] **Résilience** : comportement si ClamAV indisponible (refus d’upload vs mode dégradé — **décision produit** / config `CLAMAV_ENABLED`).
- [ ] **Ressources** : image `clamav/clamav` peut être lourde ; mise à jour des signatures (freshclam) en prod.

---

## 10. Conformité & exploitation documentaire

- [ ] **Journal d’audit** : conservation, export, accès (rôle AUDITEUR déjà prévu côté app).
- [ ] **Procédures** : installation, sauvegarde/restauration, mise à jour, incident — minimum viable pour l’équipe ops.

---

## Synthèse « go / no-go » scale horizontal (rappel)

| Prérequis | Sans cela |
|-----------|-----------|
| Stockage fichiers **partagé** ou **un seul writer** | Risque d’incohérence ou de fichiers invisibles selon l’instance |
| PostgreSQL **sauvegardé** et **testé** | Perte de données en cas d’incident |
| **Décision** sur workers OCR (in-process vs file externe) | Risque de doublons ou de charge imprévisible |

---

*Dernière mise à jour : checklist générique alignée sur l’état du dépôt ; à réviser après chaque grand choix d’architecture.*
