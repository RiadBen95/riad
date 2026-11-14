# Applications Android de Riad

Deux applications Kotlin + Jetpack Compose, compilées automatiquement par GitHub Actions à chaque push.

| Application | Dossier | APK |
|---|---|---|
| ✈️ **Envolée** : vols et sorties au meilleur prix | `voyage/` | `envolee.apk` |
| 🎸 **Accordeur Guitare** | `app/` | `accordeur-guitare.apk` |

Les APK sont publiés dans les [Releases](../../releases/latest) du dépôt.
Sur le téléphone, ouvrez le fichier téléchargé (autorisez les « sources inconnues » si Android le demande).

---

# ✈️ Envolée

<img src="docs/logo.svg" width="96" alt="Logo Envolée">

Comparateur de vols et guide des sorties. L'app trouve les meilleurs prix, puis envoie vers le site du vendeur
pour payer : aucune donnée bancaire ne passe par l'application.

## Fonctionnalités

- **Accueil** : recherche rapide, raccourcis Vols / Sorties / Alertes, recherches récentes à relancer
  d'un geste, destinations populaires en photo.
- **Vols** : aller-retour ou aller simple, autocomplétion des villes et aéroports, passagers, vols directs.
- **Recherche rapide** : le dernier départ est mémorisé, aller et retour se choisissent dans un seul calendrier,
  idées de destinations en un geste, logos des compagnies dans les résultats.
- **Dates flexibles** : cherche le meilleur prix sur tout le mois.
- **Calendrier des prix** : le tarif aller le plus bas jour par jour ; touchez un jour pour relancer la recherche.
- **Tri** par prix, durée ou nombre d'escales ; le meilleur prix est mis en évidence.
- **Sorties** : filtre par type (musées, monuments, attractions, parcs, plages, restaurants, cafés, bars,
  boîtes de nuit, shopping, visites). Les lieux de la ville s'affichent dans l'app (photo, description,
  adresse, horaires, distance) grâce à OpenStreetMap et Wikipédia. « Réserver des billets » ouvre GetYourGuide ou
  Tiqets pour ce lieu ; « Itinéraire » ouvre Google Maps. Les visites guidées renvoient vers GetYourGuide, Viator, Klook.
- **Expérience** : un questionnaire de 6 questions (ville, durée, avec qui, envies, rythme, budget) puis un
  programme heure par heure qui enchaîne visites, balades, restaurants, cafés et bars proches les uns des autres,
  avec parcours à pied dans Google Maps et « Autre proposition » en un geste.
- **Blogue** : articles sur les lieux emblématiques (textes Wikipédia), adaptés aux recherches de la personne
  (destination du vol, ville des sorties, expérience) avec un filtre « Pour vous » selon ses goûts ; sans recherche
  préalable, icônes des villes les plus emblématiques ; barre de recherche ville / monument / lieu ; lecture
  complète de l'article dans l'app.
- **Hôtels** : recherche avec prix réels pour les dates et le nombre de voyageurs (LiteAPI, clé gratuite dans
  Réglages) : photo, étoiles, note et avis, distance, annulation gratuite, prix total et par nuit, tri ;
  réservation chez Booking.com, Hotels.com, Expedia, Agoda ou Google Hôtels avec hôtel et dates pré-remplis.
  Accès depuis la barre du bas, l'accueil, les résultats de vol, l'Expérience (« Où dormir ? ») et Mes voyages.
  Filtres (étoiles, note 8+, annulation gratuite, petit-déjeuner). Page de l'hôtel : galerie de photos,
  description, équipements, heures d'arrivée et de départ, avis des clients avec analyse des points forts et
  faibles, toutes les chambres disponibles (repas, conditions d'annulation, prix) ; réservation directe dans l'app
  en mode test (clé « sandbox ») : vérification de l'offre, coordonnées, confirmation ajoutée à Mes voyages.
- **Expérience sur tout le séjour** : demi-journée ou 1 à 30 jours ; chaque étape peut être retirée ou
  remplacée par un lieu proposé dans une liste, les horaires sont recalculés.
- **Météo** (Open-Meteo, gratuit) : prévisions jusqu'à 16 jours prises en compte dans l'Expérience (musées
  s'il pleut, parcs et plages au soleil, point de vue au coucher du soleil), météo affichée par jour.
- **Vol → séjour** : depuis les résultats, « Planifier mon séjour » ouvre l'Expérience avec la ville, les dates
  et la durée du voyage ; Sorties et Blogue suivent la même destination.
- **Mes voyages** : carnet par destination (vols gardés, programme Expérience, lieux favoris ♥, articles du
  blogue), enregistré sur le téléphone et lisible hors connexion, partageable par messagerie ; prochain voyage
  affiché sur l'accueil.
- **Vol + sorties** : depuis les résultats, « Que faire sur place » ouvre les sorties de la destination.
- **Alertes de prix** : suivez un trajet ; l'app vérifie toutes les ~12 h et envoie une notification si le prix baisse.
- **Mode démo** avec des prix fictifs tant qu'aucune clé API n'est saisie.

## Langues

Français, anglais, arabe (affichage de droite à gauche), espagnol, chinois, japonais et hindi. L'app suit la
langue du téléphone, avec un choix dans **Réglages**. Les articles du blogue et les descriptions Wikipédia
s'affichent dans la langue choisie quand l'article existe (sinon en anglais).

Les traductions sont dans `tools/i18n/part*.py` (une ligne par texte, le français sert de clé) ;
`python3 tools/i18n/build.py` génère `voyage/src/main/res/raw/translations.tsv`.

## Activer les vrais prix (gratuit)

Les prix des vols viennent de l'API de données Aviasales, via le programme d'affiliation **Travelpayouts**.
Ce sont les tarifs trouvés par les voyageurs ces derniers jours : le prix final est confirmé sur le site du vendeur.

1. Créez un compte sur https://www.travelpayouts.com et rejoignez le programme **Aviasales**.
2. Dans le profil, section API, copiez le **token**.
3. Dans l'app : onglet **Réglages**, collez le token (et votre **marker** pour toucher une commission), puis Enregistrer.

La clé reste enregistrée sur le téléphone ; elle n'est jamais dans le code du dépôt.

## Notes des restaurants, cafés et bars (facultatif)

Dans **Réglages**, ajoutez une ou plusieurs clés. L'app utilise **une source à la fois, dans cet ordre** :
Google tant que sa limite du mois n'est pas atteinte, puis Tripadvisor, puis Foursquare.

| Source | Apporte | Coût par recherche | Limite par défaut |
|---|---|---|---|
| Google Places API (New) | note, nombre d'avis, prix, « ouvert maintenant » | 1 appel | 950 / mois (1 000 gratuits) |
| Tripadvisor Content API | note, avis, classement dans la ville | 1 + jusqu'à 10 appels | 900 / mois (à ajuster selon votre offre) |
| Foursquare Places API | classement par popularité | 1 appel | 5 000 / mois (à ajuster) |

Chaque source s'arrête d'elle-même à sa limite mensuelle (réglable) ou dès que le fournisseur répond
« quota dépassé » ; l'app revient alors au guide Wikivoyage et à la carte. Pour une vraie garantie,
fixez aussi un **quota par jour** chez le fournisseur (Google Cloud : APIs et services → Places API → Quotas).
Pour Tripadvisor, autorisez le domaine `riadben95.github.io` sur votre clé : l'app l'envoie comme référent.

## Structure

| Fichier | Rôle |
|---|---|
| `data/TravelpayoutsApi.kt` | Appels HTTP (prix par dates, calendrier, autocomplétion) et liens Aviasales |
| `data/Parsers.kt` | Lecture des réponses JSON |
| `data/ActivityLinks.kt` | Liens de réservation pour musées, attractions, visites et restaurants |
| `data/Places.kt` | Lieux OpenStreetMap (Nominatim + Overpass) et résumés Wikipédia |
| `data/DemoData.kt` | Prix fictifs du mode démo |
| `data/Storage.kt` | Réglages et alertes enregistrés sur le téléphone |
| `alerts/` | Vérification des prix en tâche de fond (WorkManager) et notifications |
| `AppViewModel.kt` | État de l'app et logique des écrans |
| `ui/` | Écrans : recherche de vols, résultats, sorties, alertes, réglages |

```bash
./gradlew :voyage:assembleDebug        # APK dans voyage/build/outputs/apk/debug/
./gradlew :voyage:testDebugUnitTest    # tests (lecture JSON, liens, formats)
```

---

# 🎸 Accordeur Guitare

Application Android d'accordage de guitare, en Kotlin + Jetpack Compose.

## Fonctionnalités

- **Détection en temps réel** de la note jouée via le micro (algorithme YIN, précision < 1 cent).
- **Jauge à aiguille** de −50 à +50 cents, avec code couleur : vert (±5 ¢), jaune (±15 ¢), rouge.
- **Indications claires** : « Trop bas · tendez la corde » / « Trop haut · détendez la corde » / « Accordé ✓ ».
- **Mode automatique** : la corde la plus proche est détectée seule. Touchez une corde pour la verrouiller.
- **Accordages** : Standard, Demi-ton plus bas, Drop D, DADGAD, Open G, Open D, Drop C, et un mode **Chromatique**.
- **Diapason réglable** : La4 de 415 à 466 Hz (440 Hz par défaut).
- Notes affichées en notation anglaise (E, A, D…) et en solfège (Mi, La, Ré…).
- Le son est analysé uniquement sur l'appareil ; rien n'est enregistré ni envoyé.

## Compiler soi-même

Prérequis : Android Studio (ou JDK 17 + SDK Android 35).

```bash
./gradlew :app:assembleDebug        # APK dans app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # tests de la détection de hauteur
```

Android 8.0 (API 26) minimum.

## Structure

| Fichier | Rôle |
|---|---|
| `audio/AudioCapture.kt` | Lecture du micro (44,1 kHz, fenêtre glissante de 4096 échantillons) |
| `dsp/YinPitchDetector.kt` | Détection de la fréquence fondamentale (YIN + interpolation parabolique) |
| `music/Notes.kt`, `music/Tunings.kt` | Notes, calcul des cents, accordages |
| `TunerViewModel.kt` | Filtrage (médiane), choix de la corde cible, état de l'écran |
| `ui/TunerScreen.kt` | Interface : note, jauge, sélecteur de cordes, menu d'accordage |

## Sécurité

- **Signature** : Envolée est publiée en version *release* signée avec une clé privée qui n'est
  pas dans le dépôt. La CI la lit dans les secrets GitHub `ENVOLEE_KEYSTORE_BASE64`,
  `ENVOLEE_KEYSTORE_PASSWORD`, `ENVOLEE_KEY_ALIAS` et `ENVOLEE_KEY_PASSWORD`. Sans ces secrets,
  l'app est compilée mais rien n'est publié.
- **CI** : droits en lecture par défaut, publication seulement depuis la branche principale ou de
  développement, actions épinglées par empreinte, mises à jour par Dependabot.
- **App** : aucune sauvegarde des données, trafic
  HTTPS uniquement, liens externes limités à http(s), page de paiement limitée à LiteAPI et
  Stripe, réservations payées non confirmées conservées pour être finalisées sans nouveau paiement.
- À venir : serveur intermédiaire pour garder les clés et vérifier les paiements côté serveur.
