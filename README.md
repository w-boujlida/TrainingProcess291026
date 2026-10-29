# Training Process

Application de suivi des formations — authentification, gestion des utilisateurs et recrutement.

## Stack

- **Backend** : Spring Boot 3.3 (Java 21), Spring Security + JWT, JPA, Flyway
- **Base de données** : PostgreSQL 16
- **Frontend** : React 18 + Vite, servi par Nginx (proxy `/api` → backend)

## Rôles

Les nouveaux salariés importés depuis la base RH peuvent recevoir le rôle unique
`JOB_EMPLOYEE` (Employé), sans accès aux modules. Les comptes existants gardent
leurs rôles, mots de passe et identifiants techniques lors d'un rapprochement
par matricule.

La **Fiche RH** liée au compte conserve uniquement les colonnes sélectionnées,
leurs intitulés d'origine et leur valeur Excel (dates en ISO, formules sous forme
de résultat mis en cache). Sa consultation est réservée au **Super Admin**, côté
interface et API, avec réponse non mise en cache. Ces données ne figurent ni dans
la liste des utilisateurs, ni dans les exports ordinaires, ni dans le JWT ou
le journal d'audit. Les libellés et valeurs RH d'origine ne sont pas traduits.
Le fichier source peut contenir salaire, CIN et coordonnées personnelles :
conservez-le et les mots de passe temporaires dans un emplacement privé.

### Mises à jour RH mensuelles

Dans **Utilisateurs**, le Super Admin dispose du bouton **Importer les mises à
jour RH**. Sélectionnez le classeur `.xlsx` (10 Mo maximum), puis **Afficher
l'aperçu** : aucune donnée n'est enregistrée avant confirmation. La feuille doit
s'appeler `RH`, avec les mêmes en-têtes et positions que le fichier RH initial.
Seules ces 62 colonnes sont lues :

```text
A B C D F G H I J K L N O P Q R S T U V W X Y Z AA AB AC AD AE AF AG AH AK AL AM AN AO AP AQ AR AS AT AU BC BD BE BF BG BH BI BJ BK BL BM BR BV BW BX BZ CA CB CC
```

Le matricule B rapproche les comptes. Les champs renseignés actualisent le nom,
le statut actif/inactif, le type de travail et la fiche RH ; les cellules vides
conservent leurs valeurs existantes. Les comptes absents ne sont pas supprimés.
Les rôles, mots de passe et identifiants techniques existants restent inchangés.
Les comptes disposant d'un rôle applicatif de gestion/validation sont ignorés.
Les nouveaux matricules exigent un nom, un statut et un type de travail ; ils
reçoivent le rôle `JOB_EMPLOYEE`, sans accès aux modules, avec changement de mot
de passe obligatoire. Un salarié inactif reste inactif.

Les erreurs de ligne et doublons bloquent tout l'import, qui est transactionnel
(10 000 lignes maximum). Les erreurs Excel sont conservées comme texte ; les
formules ne sont jamais recalculées et un résultat manquant devient
« Non calculé dans Excel ». Si le fichier ou les comptes changent après l'aperçu,
il faut recommencer celui-ci avant de confirmer.
Les archives Excel sont limitées à 2 000 entrées, 64 Mio par entrée et 128 Mio
décompressés au total. Leur compression est normalisée en mémoire pour accepter
les longues plages de mise en forme répétées du classeur RH initial, sans
désactiver les protections Excel globales.

Après l'import, une alerte liste les nouvelles recrues. **Aucun dossier ni
workflow Recrutement n'est créé automatiquement** : utilisez **Ouvrir
Recrutement** pour lancer le parcours manuellement. Téléchargez le CSV privé
des accès temporaires avant de fermer la fenêtre ; les mots de passe ne sont
retournés qu'à la création, jamais affichés dans la liste ni journalisés.
L'alerte reste visible sur la page courante jusqu'à sa fermeture/navigation.
L'ancien import de comptes reste disponible et inchangé.

En cas de réponse interrompue, refaites un aperçu et contrôlez la liste avant
de réessayer : la transaction peut déjà avoir été validée. Si les nouveaux
comptes existent mais que leur fichier d'accès n'a pas été reçu, utilisez la
réinitialisation de mot de passe ; répéter l'import ne restitue pas leurs accès.

Dans Utilisateurs, chaque ligne peut être sélectionnée par sa case ou en cliquant
hors des boutons ; toute la ligne est alors surlignée. **Tout sélectionner**
concerne uniquement les résultats de la recherche et du filtre de rôle.
Changer ces filtres efface la sélection. Les comptes sélectionnés peuvent être
exportés sans mot de passe, ou recevoir un type de travail / une activation /
désactivation après confirmation. Les modifications en lot sont transactionnelles :
une erreur annule tout le lot. Elles refusent le compte connecté, les comptes
hors de ses droits et la désactivation du dernier Super Admin actif.

Les utilisateurs disposent d'un **Type de travail** facultatif : Cadre, Direct ou
Indirect. Il s'affiche dans la liste et se renseigne dans les formulaires de
création/modification. Il est indépendant du rôle et ne change aucun droit
d'accès. Les comptes existants restent sans type : aucun rapprochement ou
remplissage automatique n'est effectué. Le modèle et l'export Excel incluent
`Type de travail` ; cette colonne est facultative à l'import et les anciens
fichiers restent acceptés.

Les 51 rôles métier du fichier utilisateurs sont disponibles avec des codes `JOB_`.
Ils sont distincts des six rôles applicatifs historiques, y compris Formatrice et
Team Leader : aucun droit existant n'est modifié. Seul le Super Admin peut les
attribuer. Ces comptes peuvent se connecter, changer leur mot de passe et se
déconnecter, mais aucun module ni donnée métier ne leur est accessible (403).
Les droits ne sont pas configurables pour ces rôles dans cette version.
Lors de l'import, utilisez le code `JOB_` pour éviter toute confusion entre un
libellé métier et un rôle applicatif historique.

| Rôle | Gestion des utilisateurs |
|------|--------------------------|
| Super Admin | Tous les comptes (y compris Admin et Super Admin) |
| Admin | Comptes Superviseur, Formatrice, Team Leader et Qualité |
| Superviseur | — |
| Formatrice | — |
| Team Leader | — |
| Qualité | — |

## Retrait du module Polyvalence

Le module expérimental Polyvalence a été retiré. La migration V14 supprime ses
quatre tables ainsi que les quatre salariés fictifs et cinq comptes DEMO PV28
créés pour sa simulation. Les utilisateurs réels et leurs fiches de formation
ne sont pas modifiés ; le journal d'audit est conservé. Si un compte DEMO a été
réutilisé dans d'autres données, la migration refuse la suppression pour ne pas
détacher ces données.

La migration V13 reste présente et inchangée uniquement pour conserver la
compatibilité avec l'historique Flyway des bases déjà mises à jour.

## Démarrage local

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

## Langues

- Choisissez **Français, English, Italiano ou العربية** depuis la page de connexion
  ou le bas du menu latéral. Le choix est mémorisé par navigateur ; le français est
  utilisé par défaut. L'arabe active une présentation de droite à gauche (RTL).
- Les menus, formulaires, libellés métier et messages serveur suivent la langue
  sélectionnée, ainsi que les trois documents salariés et leur impression / PDF.
  Le livret s'affiche désormais dans cette langue, sans doublons français/arabe.
- Les modèles et exports Excel utilisent des en-têtes et instructions localisés ;
  les feuilles arabes sont RTL. L'import reconnaît les en-têtes des quatre langues,
  même si une autre langue est sélectionnée au moment de l'import. Les dates à
  saisir dans Excel restent au format **JJ/MM/AAAA**.
- Les noms, identifiants, postes libres, commentaires et signatures restent intacts.
  Seuls les libellés système sont traduits ; les codes métier et les règles de
  validation, droits d'accès et workflows ne changent pas.
- Le catalogue public `GET /api/i18n` fournit les quatre langues. Le client transmet
  `Accept-Language` à l'API ; une langue non prise en charge utilise le français.
  Les traductions sont centralisées dans
  `backend/src/main/resources/i18n/translations.tsv` (FR, EN, IT, AR).

## Sécurité

- L'identifiant de connexion peut être le matricule exact du salarié, de 1 à 50
  caractères. Aucun zéro n'est ajouté aux matricules courts ; les zéros déjà
  présents sont conservés si la cellule Excel est au format texte.
- L'import utilisateurs accepte aussi l'en-tête `Matricule` (ou `Matricule *`)
  à la place d'`Identifiant`. Une cellule numérique entière est lue comme
  identifiant de connexion textuel ; la base et les comptes existants ne sont
  pas convertis en entiers. Les nombres Excel ne conservent pas les zéros en tête.

- Mots de passe hachés en BCrypt ; règles : 8 caractères min., majuscule, minuscule, chiffre, caractère spécial.
- Tout nouveau compte ou mot de passe réinitialisé doit être changé à la connexion suivante (l'API bloque les autres routes).
- Verrouillage du compte 15 min après 5 échecs de connexion.
- Un compte désactivé perd immédiatement l'accès, même avec un jeton valide.
- Impossible de se désactiver/supprimer soi-même ou de retirer le dernier Super Admin actif.

## Journal d’audit

- Menu **Gestion → Journal d’audit**, accessible uniquement au **Super Admin**, avec
  filtres par identifiant exact, IP exacte, action, résultat et intervalle de dates,
  pagination de 50 événements et actualisation manuelle.
- Depuis la migration V10, toutes les requêtes API sont tracées : connexions réussies
  ou échouées, consultations des listes/fiches, créations, modifications, suppressions,
  changements/réinitialisations de mot de passe, signatures, imports, exports et
  téléchargements de modèles. La consultation du journal lui-même est aussi tracée.
  Le journal couvre les requêtes parvenues au backend : les fichiers statiques,
  clics sans appel API et refus par Nginx avant le backend n’y figurent pas.
  Le résultat correspond au statut HTTP : un import partiellement accepté reste une
  requête réussie, avec nombres de lignes traitées/rejetées. Les requêtes mal formées
  avant identification peuvent apparaître comme « Non identifié ».
- Informations conservées : date/heure, identifiant et rôle au moment de l’action,
  identifiant technique du compte, action, route métier (avec les IDs, sans paramètres
  libres ni corps de requête), méthode, statut HTTP, IP, pair réseau, User-Agent et
  nom du poste. Les noms, e-mails, évaluations, mots de passe, jetons, contenus Excel
  et dessins de signatures ne sont pas copiés dans le journal.
- Le nom du poste est **facultatif et déclaré manuellement à la connexion**, mémorisé
  dans ce navigateur et attaché au jeton de session. Ce n’est pas une preuve de
  l’identité matérielle. Les sessions déjà ouvertes fonctionnent sans ce renseignement ;
  se reconnecter pour l’ajouter. Le navigateur ne permet pas de lire l’adresse MAC.
  Le navigateur et le système affichés sont déduits du User-Agent, lui aussi déclaratif.
- L’IP est celle de la connexion réseau, pas nécessairement celle du PC (NAT, VPN,
  proxy ou Docker peuvent la masquer). En Compose, le backend ne lit `X-Real-IP` que
  lorsque le pair correspond au service Docker `frontend`, configuré par
  `AUDIT_TRUSTED_PROXY_HOST`. Nginx remplace les en-têtes IP clients par son adresse
  distante. Hors Compose, laissez cette variable vide pour utiliser l’IP du pair,
  ou configurez uniquement un proxy réellement maîtrisé. Ne pas activer la confiance
  globale dans les en-têtes de transfert.
- Conservation : **90 jours**, exclusion immédiate des événements expirés des
  recherches, purge physique toutes les heures (premier passage après une minute).
  Les événements gardent leur identifiant/nom historique même si un compte est
  supprimé ; aucune reconstruction des actions antérieures à V10 n’est possible.
  Il n’existe pas de bouton de modification/suppression du journal dans l’application.
- En cas d’échec d’écriture du journal, une erreur `AUDIT_WRITE_FAILED` est émise
  dans les logs serveur et l’en-tête `X-Audit-Status: failed` avertit le client ;
  l’interface affiche une alerte persistante pour cette session navigateur.
  Le résultat de l’action déjà exécutée n’est pas remplacé par un faux échec :
  vérifiez les données avant de réessayer. Une consultation ne prouve pas une lecture
  humaine et un statut réussi ne constitue pas une preuve de résultat hors application.
- **Déconnexion** appelle désormais `POST /api/auth/logout` pour journaliser l’action,
  puis supprime le jeton du navigateur. Comme auparavant, les JWT ne sont pas révoqués
  côté serveur avant leur expiration. Fermer un onglet ou laisser expirer une session
  n’est pas enregistré comme une déconnexion explicite.
- Informez les utilisateurs de cette traçabilité (notice également présente à la
  connexion), limitez l’accès aux données et appliquez la politique de confidentialité
  de votre organisation. Les sauvegardes de la base suivent leur propre rétention.

## API

| Méthode | Route | Accès |
|---------|-------|-------|
| POST | `/api/auth/login` | Public |
| GET | `/api/i18n` | Public (catalogues FR, EN, IT, AR) |
| GET | `/api/auth/me` | Connecté |
| POST | `/api/auth/change-password` | Connecté |
| POST | `/api/auth/logout` | Connecté (y compris changement de mot de passe requis) |
| GET | `/api/audit-events` | Super Admin uniquement |
| GET | `/api/users` | Super Admin, Admin |
| GET | `/api/users/roles` | Super Admin, Admin (rôles attribuables) |
| POST | `/api/users` | Super Admin, Admin |
| PUT | `/api/users/{id}` | Super Admin, Admin |
| PATCH | `/api/users/{id}/status` | Super Admin, Admin |
| POST | `/api/users/{id}/reset-password` | Super Admin, Admin |
| DELETE | `/api/users/{id}` | Super Admin, Admin |
| GET | `/api/users/excel/template` | Super Admin, Admin |
| GET | `/api/users/excel/export` | Super Admin, Admin |
| POST | `/api/users/excel/import` (multipart `file`) | Super Admin, Admin |
| GET | `/api/recruitment/candidates?category=` | Connecté |
| GET | `/api/recruitment/staff` | Connecté (formatrices et team leaders actifs) |
| GET | `/api/recruitment/positions` | Connecté (postes imposés par catégorie) |
| POST / PUT / DELETE | `/api/recruitment/candidates[/{id}]` | Super Admin, Admin, Formatrice |
| PATCH | `/api/recruitment/candidates/{id}/status` | Super Admin, Admin, Formatrice |
| GET | `/api/recruitment/excel/export?category=` | Connecté |
| GET | `/api/recruitment/excel/template?category=` | Connecté |
| POST | `/api/recruitment/excel/import?category=` (multipart `file`) | Super Admin, Admin, Formatrice |

## Import / export Excel des utilisateurs

La page **Utilisateurs** propose **Modèle .xlsx**, **Importer** et **Exporter**.
Il s’agit des comptes de connexion, distincts des salariés du recrutement.

- Téléchargez le modèle puis remplissez la feuille **Utilisateurs** : identifiant, nom
  complet, rôle et mot de passe temporaire obligatoires ; e-mail facultatif. La feuille
  **Instructions** précise les règles. La liste déroulante contient uniquement les
  rôles que votre compte peut attribuer (Admin : Superviseur, Formatrice, Team Leader,
  Qualité ; Super Admin : tous).
- L’import **crée uniquement de nouveaux comptes actifs**. Un identifiant déjà présent
  (sans distinction de casse) ou répété dans le fichier est refusé ; aucun compte
  existant n’est modifié. Les lignes valides sont enregistrées indépendamment ; le bilan
  indique le nombre de créations et les erreurs avec leur numéro de ligne Excel.
- Les mêmes validations que la création manuelle s’appliquent : format de l’identifiant,
  e-mail, permissions et mot de passe de 8 à 72 caractères avec majuscule, minuscule,
  chiffre et caractère spécial. Chaque compte importé doit changer son mot de passe à
  sa première connexion. Les formules ne sont pas acceptées.
- Format `.xlsx` uniquement, 10 Mo et 5 000 lignes maximum. Les lignes vides sont ignorées.
  Le fichier contient des mots de passe temporaires : protégez-le puis supprimez-le
  après utilisation ; aucun mot de passe ne figure dans le bilan ni dans l’export.
- **Exporter** télécharge **tous les comptes**, indépendamment des filtres à l’écran,
  avec identifiant, nom complet, e-mail, rôle, état actif/inactif, changement de mot de
  passe requis et dates. Aucun hash ni mot de passe n’est exporté. Utilisez le modèle,
  et non cet export, pour créer des comptes.

## Recrutement

Quatre catégories sous **Recrutement › Employés** : **1. Formation**, **2. Direct**, **3. Indirect**, **4. Cadre** (ordre du menu).

- Fiche candidat : matricule, nom et prénom, CIN, date de naissance, téléphone, e-mail, adresse, poste, département/ligne, date de recrutement, niveau d'étude, shift, formatrice et team leader affectés, remarques.
- Statuts : Candidat → Recruté → En formation → Validé / Rejeté.
- **Direct** : le poste est obligatoire et se choisit dans une liste fixe (Préparation, Assemblage, Retouche / Reprise, Campo, Préparation Campo, Fermeture Invisible, Tresse / Ba2, Profil, Airbag, Surpiqure simple/double joue, Surpiqure simple/double coiffe, Finition Poche / Joue, Finition coiffe, TIEDAWN, GLASSAGE TOILLE AIRBAG, ASSEMBLAGE NON AIRBAG + RENFORT AIRBAG, FERMETURE SAC AIRBAG, Wire, Alimentatrice, Contrôle Qualité), définie dans `RecruitmentCategory` et exposée par `GET /api/recruitment/positions`. Les autres catégories gardent un poste libre.
- **Formation** : niveau obligatoire — **Apprentie** (période d'essai 1 mois et demi), **Junior** et **Confirmée** (2 à 3 semaines, fin calculée à 3 semaines). La fin de période d'essai est calculée à partir de la date de recrutement (`TrainingLevel`).
- Ajout guidé : pour **Direct** et **Formation**, une pop-up « Étape 1 / 2 » fait choisir le poste ou le niveau (cases à cocher) avant d'ouvrir la fiche.
- **Workflow Formation → Direct** : quand une personne en Formation passe au statut **Validé** (liste, fiche ou import Excel), elle est transférée automatiquement en **Direct**. Une pop-up demande le poste Direct ; le statut reste « Validé », le niveau est conservé et la date de validation (`formation_validated_on`) est affichée comme « Issue de Formation » dans la liste Direct. `PATCH /api/recruitment/candidates/{id}/status` accepte `{ "status": "VALIDE", "position": "Wire" }`.
- **Livret d’intégration** : chaque fiche, dans les quatre catégories, dispose d’un livret dans la langue sélectionnée, inspiré des documents SAGE. Il regroupe les informations du salarié (dont le projet / la ligne, repris du champ « Département / ligne »), les dates de formation, six objectifs évalués, l’évaluation finale et les observations. Un candidat dessine sa signature sur l’appareil ; la formatrice affectée signe avec son compte. Toute modification du contenu après signature annule les signatures afin qu’elles soient recueillies à nouveau. L’impression du livret permet de l’enregistrer en PDF depuis le navigateur. Les livrets des fiches déjà présentes sont créés automatiquement ; les dates et signatures historiques restent vides quand elles ne sont pas connues.
- CIN et matricule uniques.
- Super Admin, Admin et Formatrice créent/modifient ; Superviseur et Team Leader consultent en lecture seule.

### Saisie ergonomique des documents salariés

- **Fiche de formation** : navigation entre les cinq semaines et une étape « Avis et validations ». Chaque semaine présente les dates, une progression des notes, les critères regroupés par rubrique et leurs commentaires, puis sa validation.
- **Fiche d’intégration au poste** : choix d’un cycle (formation initiale ou recyclage), puis d’une rubrique (accueil/sécurité, projet/organisation, production/qualité, logistique, bilan/signature). Les résultats affichés restent ceux enregistrés ; ils sont recalculés après sauvegarde.
- **Livret d’intégration** : quatre étapes dans la langue sélectionnée (salarié/dates, objectifs, évaluation finale, signatures).
- Changer d’étape conserve la saisie en cours sans l’enregistrer automatiquement. Le bouton **Enregistrer** reste accessible ; fermer avec des modifications demande confirmation. Les permissions, l’ordre des signatures et leur invalidation restent inchangés.
- **Imprimer / PDF** est disponible après sauvegarde et reprend la nouvelle présentation, en A4 portrait sur plusieurs pages, avec **toutes** les semaines, les deux cycles et toutes les sections du livret, même non affichées à l’écran. Les commentaires et observations sont inclus. Les anciennes grilles papier paysage ne sont plus utilisées.

### Import / export Excel

- **Exporter** (tous les rôles) : télécharge la liste de la catégorie en `.xlsx`.
- **Importer** (éditeurs) : téléchargez le modèle (`Modèle .xlsx`, avec une feuille *Instructions*), remplissez-le puis déposez-le.
  - Colonnes obligatoires : *Nom et prénom*, *CIN*. Dates au format JJ/MM/AAAA ; formatrice / team leader par identifiant ou nom complet.
  - Un CIN déjà présent dans la catégorie **met à jour** la fiche (les cellules vides conservent les valeurs existantes).
  - Les lignes valides sont importées ; les lignes en erreur sont listées avec leur motif.
  - Limites : `.xlsx` uniquement, 10 Mo, 5000 lignes.

## Fiche de formation couture

L'action **Fiche de formation** (icône diplôme) de chaque employé ouvre le modèle
**SAGE-FOR-DRH-34**, révision 00 du 10/07/2023, distinct du livret d'intégration.
Le contenu reprend le document « Suivi formation nouveau-recrue couturière »,
avec une saisie par semaine :

- Poste, matricule, nom, projet / ligne (champ département du salarié), formatrice,
  dates de début et de fin issues des semaines 1 et 5.
- Cinq semaines, jours 1 à 30, et **26 critères par semaine** : comportement (7),
  connaissance (4), machine (4) et couture / process simple (11).
- Notes facultatives pendant la saisie : **1 Faible, 2 Moyen, 3 Satisfaisant,
  4 Bien, 5 Excellent** ; commentaire par note et avis de transfert par rubrique.
- Dates propres à chaque semaine, laissées vides tant qu'elles ne sont pas connues.
  Une semaine datée exige un début et une fin ; les semaines ne se chevauchent pas.
- Super Admin et Admin peuvent renseigner la fiche. La formatrice **ou** le Team Leader
  affecté peut la renseigner et valider chaque semaine (pas de double validation).
  Les 26 notes et les dates sont obligatoires pour valider, dans l'ordre des semaines.
- Après les cinq validations, le **Superviseur**, puis **Qualité**, signent la fiche.
  Les signatures enregistrent le compte et l'horodatage.
- Modifier une semaine annule sa validation, celles des semaines suivantes et les
  signatures finales, sans toucher aux semaines précédentes. Modifier seulement les
  avis annule uniquement les signatures finales. Un enregistrement sans changement
  conserve les signatures. Les mises à jour concurrentes sont refusées explicitement.
- **Imprimer / PDF** produit un document A4 portrait sur plusieurs pages, regroupé
  par semaine, avec toutes les notes, commentaires, avis et signatures.
  Enregistrer les modifications avant de signer ou d'imprimer.

**Migration V8 :** conformément au remplacement demandé, toutes les anciennes
évaluations et validations de fiches de formation sont effacées et remplacées par
ce modèle vierge (5 × 26 critères). Les candidats et livrets d'intégration sont
conservés. Le même modèle est créé automatiquement pour chaque nouveau candidat.

API : `GET /api/training-sheets/candidates/{id}` (connecté), `PUT` sur la même route
(administrateurs ou formatrice / Team Leader affecté), puis `POST` sur
`/periods/{periodId}/trainer-signature` **ou** `/periods/{periodId}/team-leader-signature`,
et enfin `/supervisor-signature`, `/quality-signature`. Le `PUT` attend la `version`,
les cinq `periods` (IDs, dates, critères avec IDs / notes / commentaires) et les quatre
`transferOpinions` (`behavior`, `knowledge`, `machine`, `sewing`). Les libellés et
rubriques sont ceux du modèle, non modifiables par cette API.

## Fiche d'intégration et de formation au poste

Dans les actions de chaque employé : **1. Fiche de formation**, **2. Fiche
d'intégration**, **3. Livret d'intégration**. Les trois documents restent distincts.
La deuxième fiche reprend **SAGE-FOR-DRH-11**, révision 01 du 24/05/2022 :

- Les 23 étapes du modèle, dont quatre sous-rubriques sécurité, soit 26 lignes
  par cycle ; deux cycles indépendants **Formation initiale** et **Recyclage**.
- Indications I / R / E et postes concernés repris du modèle, délai par étape,
  dates de formation et d'évaluation par cycle, mesures à prendre.
- Formation **X / NA**, évaluation **OK / NOK**. NA est exclu du dénominateur.
  La note est `100 × nombre OK / nombre évalué` ; la qualification est obtenue
  à **80 % exactement ou plus**, seulement après évaluation de toutes les étapes
  applicables. Une fiche vide ou entièrement NA n'est jamais déclarée qualifiée.
- Admin et Super Admin renseignent la fiche ; la formatrice ou le Team Leader
  affecté peut renseigner et signer les étapes réalisées avec son compte.
  L'opérateur dessine son émargement sur l'écran après complétion et signature
  des étapes applicables ; le compte qui recueille la signature est tracé.
- Modifier une étape annule sa signature et l'émargement du cycle ; modifier
  les dates annule toutes les signatures de ce cycle. Les mesures annulent
  uniquement l'émargement. L'autre cycle et les autres documents sont préservés.
  Un enregistrement sans changement conserve les signatures ; une version
  périmée est refusée avec une erreur explicite.
- Impression / PDF A4 portrait sur plusieurs pages, avec les deux cycles et toutes
  leurs rubriques, états, notes, signatures et mesures.

La migration V9 ajoute les fiches vierges à tous les salariés existants, sans
effacer les fiches de formation ou les livrets. Un déclencheur crée aussi cette
fiche pour chaque nouveau salarié ; aucune date ou signature historique n'est inventée.

API : `GET` / `PUT /api/workstation-forms/candidates/{id}`, puis
`POST /{kind}/steps/{stepId}/signature` et `POST /{kind}/operator-signature`
sur cette même base, avec `kind` = `INITIAL` ou `REFRESHER`. Les écritures et
signatures attendent la version courante ; l'émargement attend une image PNG.

## Développement frontend

```powershell
cd frontend; npm install; npm run dev   # http://localhost:5173 (proxy vers :8082)
```
