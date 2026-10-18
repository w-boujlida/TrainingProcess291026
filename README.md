# Training Process

Application de suivi des formations — module d'authentification et de gestion des utilisateurs.

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

## Développement frontend

```powershell
cd frontend; npm install; npm run dev   # http://localhost:5173 (proxy vers :8082)
```
