# 🎸 Accordeur Guitare (Android)

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

## Installer l'APK

**Lien direct :** https://github.com/RiadBen95/riad/releases/latest/download/accordeur-guitare.apk

Ouvrez ce lien sur le téléphone, puis le fichier téléchargé (autoriser les « sources inconnues »
si Android le demande). Chaque push recompile l'APK et met à jour ce lien.

## Compiler soi-même

Prérequis : Android Studio (ou JDK 17 + SDK Android 35).

```bash
./gradlew assembleDebug        # APK dans app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # tests de la détection de hauteur
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
