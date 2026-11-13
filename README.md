# Applications Android de Riad

Deux applications Kotlin + Jetpack Compose, compilées automatiquement par GitHub Actions à chaque push.

| Application | Dossier | APK |
|---|---|---|
| ✈️ **Envolée** : vols, hôtels et sorties au meilleur prix | `voyage/` | `envolee.apk` |
| 🎸 **Accordeur Guitare** | `app/` | `accordeur-guitare.apk` |

Les APK sont publiés dans les [Releases](../../releases/latest) du dépôt.
Sur le téléphone, ouvrez le fichier téléchargé (autorisez les « sources inconnues » si Android le demande).

---

# ✈️ Envolée

<img src="docs/logo.svg" width="96" alt="Logo Envolée">

Comparateur de vols et d'hôtels. L'app trouve les meilleurs prix, puis envoie vers le site du vendeur
pour payer : aucune donnée bancaire ne passe par l'application.

## Fonctionnalités

- **Accueil** : recherche rapide, raccourcis Vols / Hôtels / Sorties / Alertes, recherches récentes à relancer
  d'un geste, destinations populaires en photo.
- **Vols** : aller-retour ou aller simple, autocomplétion des villes et aéroports, passagers, vols directs.
- **Recherche rapide** : le dernier départ est mémorisé, aller et retour se choisissent dans un seul calendrier,
  idées de destinations en un geste, logos des compagnies dans les résultats.
- **Dates flexibles** : cherche le meilleur prix sur tout le mois.
- **Calendrier des prix** : le tarif aller le plus bas jour par jour ; touchez un jour pour relancer la recherche.
- **Tri** par prix, durée ou nombre d'escales ; le meilleur prix est mis en évidence.
- **Hôtels** : saisissez le séjour une fois, comparez Booking.com, Hotels.com, Expedia, Agoda et Google Hôtels
  (liens de recherche pré-remplis).
- **Sorties** : filtre par type (musées, monuments, attractions, parcs, plages, restaurants, cafés, bars,
  boîtes de nuit, shopping, visites). Les lieux de la ville s'affichent dans l'app (photo, description,
  adresse, horaires, distance) grâce à OpenStreetMap et Wikipédia. « Réserver des billets » ouvre GetYourGuide ou
  Tiqets pour ce lieu ; « Itinéraire » ouvre Google Maps. Les visites guidées renvoient vers GetYourGuide, Viator, Klook.
- **Vol + hôtel** : depuis un vol, « Hôtels sur place » pré-remplit la destination et les dates.
- **Alertes de prix** : suivez un trajet ; l'app vérifie toutes les ~12 h et envoie une notification si le prix baisse.
- **Mode démo** avec des prix fictifs tant qu'aucune clé API n'est saisie.

## Activer les vrais prix (gratuit)

Les prix des vols viennent de l'API de données Aviasales, via le programme d'affiliation **Travelpayouts**.
Ce sont les tarifs trouvés par les voyageurs ces derniers jours : le prix final est confirmé sur le site du vendeur.

1. Créez un compte sur https://www.travelpayouts.com et rejoignez le programme **Aviasales**.
2. Dans le profil, section API, copiez le **token**.
3. Dans l'app : onglet **Réglages**, collez le token (et votre **marker** pour toucher une commission), puis Enregistrer.

La clé reste enregistrée sur le téléphone ; elle n'est jamais dans le code du dépôt.

## Structure

| Fichier | Rôle |
|---|---|
| `data/TravelpayoutsApi.kt` | Appels HTTP (prix par dates, calendrier, autocomplétion) et liens Aviasales |
| `data/Parsers.kt` | Lecture des réponses JSON |
| `data/HotelLinks.kt` | Liens de recherche d'hôtels pré-remplis |
| `data/ActivityLinks.kt` | Liens de réservation pour musées, attractions, visites et restaurants |
| `data/Places.kt` | Lieux OpenStreetMap (Nominatim + Overpass) et résumés Wikipédia |
| `data/DemoData.kt` | Prix fictifs du mode démo |
| `data/Storage.kt` | Réglages et alertes enregistrés sur le téléphone |
| `alerts/` | Vérification des prix en tâche de fond (WorkManager) et notifications |
| `AppViewModel.kt` | État de l'app et logique des écrans |
| `ui/` | Écrans : recherche de vols, résultats, hôtels, alertes, réglages |

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
