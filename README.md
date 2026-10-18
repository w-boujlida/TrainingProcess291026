# Training Process

Application de suivi des formations — authentification, gestion des utilisateurs et recrutement.

## Stack

- **Backend** : Spring Boot 3.3 (Java 21), Spring Security + JWT, JPA, Flyway
- **Base de données** : PostgreSQL 16
- **Frontend** : React 18 + Vite, servi par Nginx (proxy `/api` → backend)

## Rôles

| Rôle | Gestion des utilisateurs |
|------|--------------------------|
| Super Admin | Tous les comptes (y compris Admin et Super Admin) |
| Admin | Comptes Superviseur, Formatrice et Team Leader |
| Superviseur | — |
| Formatrice | — |
| Team Leader | — |

## Démarrage

```powershell
Copy-Item .env.example .env   # puis renseigner les secrets
docker compose up -d --build
```

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3002 |
| API | http://localhost:8082/api |
| PostgreSQL | localhost:5433 |

Au premier démarrage, un compte Super Admin est créé avec `SUPERADMIN_USERNAME` / `SUPERADMIN_PASSWORD` (fichier `.env`). Le changement de mot de passe est obligatoire à la première connexion.

## Sécurité

- Mots de passe hachés en BCrypt ; règles : 8 caractères min., majuscule, minuscule, chiffre, caractère spécial.
- Tout nouveau compte ou mot de passe réinitialisé doit être changé à la connexion suivante (l'API bloque les autres routes).
- Verrouillage du compte 15 min après 5 échecs de connexion.
- Un compte désactivé perd immédiatement l'accès, même avec un jeton valide.
- Impossible de se désactiver/supprimer soi-même ou de retirer le dernier Super Admin actif.

## API

| Méthode | Route | Accès |
|---------|-------|-------|
| POST | `/api/auth/login` | Public |
| GET | `/api/auth/me` | Connecté |
| POST | `/api/auth/change-password` | Connecté |
| GET | `/api/users` | Super Admin, Admin |
| GET | `/api/users/roles` | Super Admin, Admin (rôles attribuables) |
| POST | `/api/users` | Super Admin, Admin |
| PUT | `/api/users/{id}` | Super Admin, Admin |
| PATCH | `/api/users/{id}/status` | Super Admin, Admin |
| POST | `/api/users/{id}/reset-password` | Super Admin, Admin |
| DELETE | `/api/users/{id}` | Super Admin, Admin |
| GET | `/api/recruitment/candidates?category=` | Connecté |
| GET | `/api/recruitment/staff` | Connecté (formatrices et team leaders actifs) |
| GET | `/api/recruitment/positions` | Connecté (postes imposés par catégorie) |
| POST / PUT / DELETE | `/api/recruitment/candidates[/{id}]` | Super Admin, Admin, Formatrice |
| PATCH | `/api/recruitment/candidates/{id}/status` | Super Admin, Admin, Formatrice |
| GET | `/api/recruitment/excel/export?category=` | Connecté |
| GET | `/api/recruitment/excel/template?category=` | Connecté |
| POST | `/api/recruitment/excel/import?category=` (multipart `file`) | Super Admin, Admin, Formatrice |

## Recrutement

Quatre catégories sous **Recrutement › Employés** : **1. Formation**, **2. Direct**, **3. Indirect**, **4. Cadre** (ordre du menu).

- Fiche candidat : matricule, nom et prénom, CIN, date de naissance, téléphone, e-mail, adresse, poste, département/ligne, date de recrutement, niveau d'étude, shift, formatrice et team leader affectés, remarques.
- Statuts : Candidat → Recruté → En formation → Validé / Rejeté.
- **Direct** : le poste est obligatoire et se choisit dans une liste fixe (Préparation, Assemblage, Retouche / Reprise, Campo, Préparation Campo, Fermeture Invisible, Tresse / Ba2, Profil, Airbag, Surpiqure simple/double joue, Surpiqure simple/double coiffe, Finition Poche / Joue, Finition coiffe, TIEDAWN, GLASSAGE TOILLE AIRBAG, ASSEMBLAGE NON AIRBAG + RENFORT AIRBAG, FERMETURE SAC AIRBAG, Wire, Alimentatrice, Contrôle Qualité), définie dans `RecruitmentCategory` et exposée par `GET /api/recruitment/positions`. Les autres catégories gardent un poste libre.
- **Formation** : niveau obligatoire — **Apprentie** (période d'essai 1 mois et demi), **Junior** (2 à 3 semaines, fin calculée à 3 semaines), **Confirmée** (15 jours). La fin de période d'essai est calculée à partir de la date de recrutement (`TrainingLevel`).
- Ajout guidé : pour **Direct** et **Formation**, une pop-up « Étape 1 / 2 » fait choisir le poste ou le niveau (cases à cocher) avant d'ouvrir la fiche.
- **Workflow Formation → Direct** : quand une personne en Formation passe au statut **Validé** (liste, fiche ou import Excel), elle est transférée automatiquement en **Direct**. Une pop-up demande le poste Direct ; le statut reste « Validé », le niveau est conservé et la date de validation (ormation_validated_on) est affichée comme « Issue de Formation » dans la liste Direct. `PATCH /api/recruitment/candidates/{id}/status` accepte `{ "status": "VALIDE", "position": "Wire" }`.
- CIN et matricule uniques.
- Super Admin, Admin et Formatrice créent/modifient ; Superviseur et Team Leader consultent en lecture seule.

### Import / export Excel

- **Exporter** (tous les rôles) : télécharge la liste de la catégorie en `.xlsx`.
- **Importer** (éditeurs) : téléchargez le modèle (`Modèle .xlsx`, avec une feuille *Instructions*), remplissez-le puis déposez-le.
  - Colonnes obligatoires : *Nom et prénom*, *CIN*. Dates au format JJ/MM/AAAA ; formatrice / team leader par identifiant ou nom complet.
  - Un CIN déjà présent dans la catégorie **met à jour** la fiche (les cellules vides conservent les valeurs existantes).
  - Les lignes valides sont importées ; les lignes en erreur sont listées avec leur motif.
  - Limites : `.xlsx` uniquement, 10 Mo, 5000 lignes.

## Développement frontend

```powershell
cd frontend; npm install; npm run dev   # http://localhost:5173 (proxy vers :8082)
```
