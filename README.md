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
| POST / PUT / DELETE | `/api/recruitment/candidates[/{id}]` | Super Admin, Admin, Formatrice |
| PATCH | `/api/recruitment/candidates/{id}/status` | Super Admin, Admin, Formatrice |

## Recrutement

Quatre catégories sous **Recrutement › Employés** : **Cadre**, **Direct**, **Indirect**, **External**.

- Fiche candidat : matricule, nom et prénom, CIN, date de naissance, téléphone, e-mail, adresse, poste, département/ligne, date de recrutement, niveau d'étude, shift, formatrice et team leader affectés, remarques.
- Statuts : Candidat → Recruté → En formation → Validé / Rejeté.
- CIN et matricule uniques.
- Super Admin, Admin et Formatrice créent/modifient ; Superviseur et Team Leader consultent en lecture seule.

## Développement frontend

```powershell
cd frontend; npm install; npm run dev   # http://localhost:5173 (proxy vers :8082)
```
