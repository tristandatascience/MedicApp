# Dossier Médical — Application Android

Application Android native de gestion du **dossier médical personnel** : vaccinations,
traitements, ordonnances, résultats d'examens et rendez-vous, avec numérisation
de documents papier (photo + OCR hors ligne), verrouillage PIN/biométrie et
stockage **100 % local chiffré** (AES-256).

> V1 « stockage local » — l'option Google Drive (Option B du cahier des charges)
> est préparée dans l'architecture mais désactivée dans cette version.

## Fonctionnalités

- **5 modules** : carnets de vaccination (suggestions du calendrier vaccinal
  français), traitements (en cours / historique, prises horaires), ordonnances
  (médicaments, avertissement d'expiration), examens (catégories, documents
  multiples), rendez-vous (documents à apporter, ajout au calendrier du téléphone).
- **Numérisation** : capture CameraX multi-pages, recadrage 4 coins avec
  redressement de perspective, amélioration du contraste, génération PDF,
  import galerie/PDF.
- **OCR hors ligne** (ML Kit, modèle embarqué) : texte extrait automatiquement,
  consultable, corrigeable, copiable et indexé pour la recherche plein texte.
  Multi-passes : contraste renforcé, filtre « tampon bleu », rotations.
- **Moteur IA embarqué optionnel (bêta)** : Gemma 4 E2B multimodal via
  LiteRT-LM (litert-community, accès libre), téléchargé à la demande depuis
  les réglages (≈ 2,6 Go) puis 100 %
  hors ligne — bouton « Améliorer la transcription avec l'IA » dans la
  visionneuse de documents (tampons, écriture difficile). Double emploi :
  base du futur assistant conversationnel.
- **Recherche globale** : titres, notes et texte OCR de tous les modules,
  filtres par module et par période.
- **Profils multiples** (soi, enfants, proches) avec bascule depuis l'accueil.
- **Sécurité** : code PIN obligatoire, biométrie optionnelle, verrouillage
  automatique paramétrable (1 à 10 min), FLAG_SECURE anti-capture,
  base **SQLCipher** et fichiers chiffrés **AES-256/GCM** via une clé maître
  protégée par l'**Android Keystore**.
- **Rappels locaux** : prises de traitement (quotidiens), rappels de vaccination
  (J-30/J-7/J-1), rendez-vous (J-1/H-2) — re-planifiés après redémarrage du
  téléphone (BootReceiver).
- **Sauvegarde** : export d'une archive chiffrée par mot de passe (à conserver
  hors du téléphone) et restauration complète ; suppression définitive du dossier.
- **Respect de la vie privée** : la permission INTERNET n'est utilisée que par
  la recherche **manuelle** de mises à jour (réglages → Mise à jour, requête
  GET anonyme vers api.github.com) — aucune donnée de santé ne quitte jamais
  le téléphone, aucun tracker, aucune analytics.

## Stack technique

Kotlin 2.0 · Jetpack Compose (Material 3) · Room + SQLCipher · CameraX ·
ML Kit Text Recognition (latin, embarqué) · AlarmManager/WorkManager ·
BiometricPrompt · DataStore · minSdk 26 (Android 8.0) / targetSdk 35.

## Ouvrir et compiler le projet

### Avec Android Studio
1. Installer Android Studio (Ladybug ou plus récent).
2. Ouvrir le dossier `C:\MedicApp` (le SDK est téléchargé automatiquement).
3. `Run ▶` sur un appareil ou un émulateur Android 8+.

### En ligne de commande (chaîne d'outils déjà installée dans `tools/`)
```bash
export JAVA_HOME='C:\MedicApp\tools\jdk\jdk-17.0.20.1+1'
gradlew.bat assembleDebug          # APK debug
gradlew.bat testDebugUnitTest      # tests unitaires
```
APK produit : `app\build\outputs\apk\debug\app-debug.apk`
(installation sur téléphone : `adb install app-debug.apk`, ou copie + ouverture
du fichier avec autorisation « sources inconnues »).

> Le dossier `tools/` (JDK, SDK Android, Gradle) n'est pas versionné ; il est
> facultatif si vous utilisez Android Studio. `local.properties` pointe vers
> `C:/MedicApp/tools/android-sdk`.

## Structure du code

```
app/src/main/java/com/medicapp/
├── data/
│   ├── backup/    # archive chiffrée (export/import), mot de passe
│   ├── crypto/    # Keystore, clé maître, AES/GCM fichiers, hash PIN
│   ├── db/        # Room + SQLCipher : entités, DAO, convertisseurs
│   ├── prefs/     # réglages (DataStore)
│   ├── repo/      # repositories (modules, recherche, tableau de bord)
│   └── storage/   # stockage des documents chiffrés (abstraction Drive prête)
├── notifications/ # alarmes exactes, BootReceiver, canaux
├── ocr/           # ML Kit (hors ligne)
├── scan/          # CameraX, recadrage perspective, génération PDF
└── ui/            # Compose : verrouillage, onboarding, modules, réglages…
```

## Vie privée

Les données de santé restent uniquement sur le téléphone, chiffrées au repos ;
l'application ne contient aucun tracker ni analytics et ne demande aucune
permission réseau. La sauvegarde exportée est chiffrée par votre mot de passe :
**il n'existe aucun moyen de la déchiffrer sans lui**.

## Limitations connues (V1)

- Détection automatique des contours : pré-positionnement heuristique + ajustement
  manuel des 4 coins (détection robuste prévue en V2).
- PDF importés depuis la galerie : stockés et consultables mais sans OCR automatique.
- Mode Google Drive : non actif (architecture prête, écran de choix présent à
  l'installation avec mention « bientôt disponible »).
