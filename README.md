<div align="center">

<img src="assets/Sketchware-Pro.png" alt="NWS" width="120"/>

# NWS — New World Sketchware

**Un IDE Android pour créer des applications directement sur le téléphone, avec des blocs, un éditeur visuel et du Java/Kotlin.**

Fork de [Sketchware Pro](https://github.com/Sketchware-Pro/Sketchware-Pro), maintenu dans ce dépôt.

[Dernière version](https://github.com/prozizou/Sketch_App/releases/latest) ·
[Feuille de route](docs/IDE_ROADMAP.md) · [Licence](LICENSE.md) · [Code de conduite](CODE_OF_CONDUCT.md)

</div>

---

## Sommaire

1. [Présentation](#présentation)
2. [Installation](#installation)
3. [Fonctionnalités](#fonctionnalités)
4. [Structure du dépôt](#structure-du-dépôt)
5. [Compiler le projet](#compiler-le-projet)
6. [Tests](#tests)
7. [Intégration continue et publication](#intégration-continue-et-publication)
8. [Mises à jour dans l'application](#mises-à-jour-dans-lapplication)
9. [Formats de données utiles](#formats-de-données-utiles)
10. [Contribuer](#contribuer)
11. [Limites connues](#limites-connues)
12. [Crédits et licence](#crédits-et-licence)

---

## Présentation

NWS est une application Android qui permet de concevoir, programmer, compiler et signer d'autres applications Android
**sans ordinateur** : on dessine les écrans dans un éditeur visuel, on décrit la logique avec des blocs (ou en Java),
puis l'application compile le projet (AAPT2, ECJ, Kotlin, D8/R8) et produit un APK ou un AAB signé.

| | |
|---|---|
| Identifiant de l'application | `com.sketch_nws` |
| Version actuelle | voir [`update.json`](update.json) et les [Releases](https://github.com/prozizou/Sketch_App/releases) |
| Android | minSdk 26 (Android 8.0), targetSdk 28, compileSdk 37 |
| Langage | Java 17 (les projets générés ciblent Java 1.7/ECJ ; Kotlin pris en charge) |
| Interface | AndroidX + Material 3, thème clair/sombre |

## Installation

1. Ouvrir la page des [Releases](https://github.com/prozizou/Sketch_App/releases/latest).
2. Télécharger `NewWorldSketchware.apk`.
3. Autoriser l'installation depuis cette source si Android le demande, puis installer.

L'APK de chaque version est signé avec la clé de publication du projet ; la somme SHA-256 est publiée dans
[`update.json`](update.json). Les mises à jour suivantes se proposent directement dans l'application.

## Fonctionnalités

### Concevoir

- **Éditeur de vues** : glisser-déposer des widgets, propriétés, cadres d'appareils, grille, zoom à **deux doigts**
  (un doigt reste réservé au déplacement des éléments), annuler/rétablir par écran, guides d'alignement et distances.
- **Aperçus** : orientation, tablettes et pliables, zones sûres.
- **Éditeur de ressources** : couleurs, chaînes, styles, thèmes, polices, animations, icônes (créateur d'icônes intégré).
- **Sélecteur de couleur** : roue chromatique, saisie HEX et palette.

### Programmer

- **Éditeur logique** : blocs, vue code Java et vue partagée blocs/Java, ajustement à l'écran, mode copie qui reste
  ouvert jusqu'à ce qu'on le ferme, placement de la copie dans l'espace libre, palette repliable, bouton de collection
  (FAB) sur « Blocs ».
- **Débogueur de blocs** (simulation), détection de blocs inutilisés et de boucles, recherche dans la logique,
  graphe de navigation.
- **Éditeur de code** basé sur [sora-editor](https://github.com/Rosemoe/sora-editor) : coloration, numéros de ligne,
  complétion Java et diagnostics en direct.
- **Concepteur de données** : génère un client REST, du SQLite et des modèles Firebase.

### Gérer les blocs personnalisés

- **Gestionnaire de blocs** (Material 3) : palettes, blocs, recherche, tri, import/export, corbeille, « modifié le ».
- **Écran Edit Block** (quatre onglets) :
  - *General* : nom unique validé, choix visuel parmi les 12 formes réellement prises en charge, nom de type,
    couleur (roue, HEX, préréglages, réinitialisation), aperçu dynamique avec zoom et ajustement automatique,
    modèles rapides (Java, vue, Intent, AdMob, Firebase) proposés seulement s'ils sont compatibles.
  - *Parameters* : ajouter, modifier, supprimer, réordonner ; types existants conservés ; synchronisation avec
    la spec, l'aperçu et le code, avec renumérotation `%N$s` du code proposée (jamais automatique).
  - *Spec* : constructeur visuel et édition manuelle validée dans les deux sens, au format natif de NWS.
  - *Code* : éditeur Java (numéros de ligne, recherche, annuler/rétablir), imports ligne par ligne, diagnostics
    avec numéro de ligne, en distinguant erreurs statiques et erreurs qui n'apparaîtront qu'à la compilation.
  - Brouillon local, confirmation avant d'abandonner, enregistrement atomique (avec `.bak`), état conservé à la rotation,
    barre Annuler / Enregistrer qui suit le clavier.

### Firebase

- **Firebase Manager** : un seul éditeur `google-services.json` (import de fichier, collage, édition, formatage,
  coloration JSON, numéros de ligne). Le fichier est analysé et validé (syntaxe avec numéro de ligne, champs requis,
  format des identifiants, URL, cohérence du package) ; plusieurs clients Android sont gérés et celui du package du
  projet est choisi. Le résumé affiche le projet, l'URL de base de données, le bucket et les clients OAuth ; l'App ID
  et la clé API restent masqués et ne sont jamais écrits dans les journaux.
- **Tests de connexion séparés** : initialisation du SDK, authentification (la clé est-elle acceptée ?) et accès aux
  services (base de données, stockage), en lecture anonyme seulement. Les règles de sécurité de Firebase ne sont pas
  contournées : un « accès refusé » est la bonne réponse.
- Les quatre valeurs lues par la compilation (URL de la base ou ID du projet, App ID, clé API, bucket) sont écrites
  comme avant : les projets existants s'ouvrent sans migration manuelle.

### Compiler et publier

- Pipeline : AAPT2, ECJ, Kotlin, D8, R8 (avec fichier de mapping), export **APK** et **AAB**, signature.
- **Build Doctor** : explique les échecs de compilation courants (mémoire, classes dupliquées, limite dex, SDK minimum,
  AAPT2, ECJ, R8, signature…) avec cause, solution et ligne de journal.
- Compilateur adapté aux appareils à peu de mémoire (limite de threads pour D8/R8).
- **Release Manager** : historique, conservation du mapping, analyse de taille.
- Bibliothèques : graphe intégré, bibliothèques locales ou en ligne, exclusion des bibliothèques intégrées.
- Unity Ads : écran de réglages (Game ID, emplacements, mode test), téléchargement du SDK, classe `UnityAdsHelper` générée
  et catégorie de blocs « Unity Ads » (bannière, interstitielle, récompensée, événements).
- Export des sources pour Android Studio, avec génération optionnelle d'un workflow CI.

### Qualité et sécurité d'un projet

- **Santé du projet** : score sur 100, compatibilité Android (SDK, permissions, API dépréciées, accessibilité,
  RTL, mode sombre, edge-to-edge), **scanner de sécurité** (secrets, HTTP en clair, TLS désactivé, WebView…),
  inspecteur de dépendances (arbre, doublons, conflits), contrôle du design system.
- Sauvegarde automatique, **récupération après plantage**, instantanés automatiques, **Git** intégré (JGit).

> Les fonctions récentes sont derrière des **interrupteurs** (Paramètres de l'application) pour pouvoir les couper
> si l'une pose problème.

## Structure du dépôt

```
.
├── app/                         Module Android unique
│   ├── src/main/java/
│   │   ├── pro/sketchware/      Code principal : activités, analyse, blocs, build, éditeurs, Git…
│   │   ├── mod/                 Modules historiques de la communauté (build, éditeurs, propriétés…)
│   │   ├── com/ a/ dev/ …       Code hérité de Sketchware (a.a.a, besome…), bibliothèques embarquées
│   │   └── kellinwood/          Signature d'APK
│   ├── src/main/res/            Layouts, chaînes (23 dossiers de ressources), thèmes clair/sombre
│   ├── src/test/                Tests unitaires (JVM) et tests Robolectric
│   └── lint-baseline.xml        Base de référence Android Lint
├── docs/IDE_ROADMAP.md          Audit et avancement des chantiers « IDE professionnel »
├── scripts/generate-release-key.sh   Génère la clé de publication (à lancer sur VOTRE machine)
├── .github/workflows/           android.yml (CI), release.yml (publication), utilitaires
├── gradle/libs.versions.toml    Catalogue des versions de dépendances
├── update.json                  Description de la dernière version publiée (lue par l'application)
└── update-beta.json / update-dev.json
```

Dossiers notables sous `pro/sketchware/` :

| Dossier | Rôle |
|---|---|
| `analysis/` | Moteurs d'analyse en Java pur (Build Doctor, compatibilité, sécurité, dépendances, santé) |
| `blocks/` | Blocs personnalisés : `manager/` (liste, stockage atomique), `editor/` (écran Edit Block : spec, validation, diagnostics), `generator/` |
| `logic/` | Débogueur, graphe de blocs, navigation, recherche |
| `editor/` | Calques, guides, aperçus d'appareils |
| `build/` | Mémoire de compilation, paramètres de build |
| `designer/` | Générateurs REST, SQLite, Firebase |
| `releases/` | Release Manager |
| `activities/git/` | Intégration Git |
| `flags/` | Interrupteurs de fonctionnalités |

## Compiler le projet

Prérequis : **JDK 17**, Android SDK (plateforme 37, build-tools 37.0.0) et une connexion Internet pour Gradle.

```bash
git clone https://github.com/prozizou/Sketch_App.git
cd Sketch_App

export ANDROID_HOME=/chemin/vers/android-sdk
./gradlew :app:assembleDebug          # APK de debug dans app/build/outputs/apk/debug/
```

Contrôles identiques à ceux de la CI :

```bash
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

Le rapport Lint est dans `app/build/reports/lint-results-debug.html`. Android Lint s'appuie sur
`app/lint-baseline.xml` ; un nouveau défaut de niveau *erreur* (par exemple `NewApi`) fait échouer la CI.

Le JVM de test dispose de 3 Go de heap (`maxHeapSize` dans `app/build.gradle`) : les tests d'écrans Robolectric
conservent des layouts et des bitmaps et dépassent le tas par défaut.

## Tests

- **Tests unitaires purs** (moteurs d'analyse, modèle de spec, validation, diagnostics de code, stockage des blocs).
- **Tests Robolectric** (`@GraphicsMode(NATIVE)`, SDK 35) pour les écrans : gestionnaire de blocs, écran Edit Block
  (CRUD, synchronisation spec/paramètres, rotation, clavier, enregistrement, brouillon), éditeur logique, éditeur de vues,
  rendus clair/sombre enregistrés dans `app/build/reports/theme-renders/`.

Aucun de ces tests ne remplace un essai sur appareil : la plupart des écrans ont été vérifiés en simulation, pas sur
téléphone réel.

## Intégration continue et publication

| Workflow | Déclencheur | Rôle |
|---|---|---|
| `android.yml` — *Android CI* | push / PR vers `main` (fichiers de l'app et de Gradle) | `assembleDebug`, `lintDebug`, `testDebugUnitTest` |
| `release.yml` — *Release* | manuel (`workflow_dispatch`) | tests, APK de release signé, vérification de la signature et de l'ID de ressource figé, GitHub Release, mise à jour de `update.json` sur `main`, annonce |

Pour publier une version :

1. Augmenter `versionCode` / `versionName` dans `app/build.gradle` et mettre à jour les « nouveautés ».
2. Fusionner dans `main` et attendre une CI verte.
3. Lancer le workflow **Release** (options : mise à jour obligatoire, message du dialogue).

Secrets GitHub requis : `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD`. Générer la clé avec `scripts/generate-release-key.sh` **sur votre machine** et la
sauvegarder hors ligne : sans elle, les utilisateurs ne pourront plus mettre à jour.

## Mises à jour dans l'application

L'application lit [`update.json`](update.json) (sur `main`) :

```json
{
  "versionCode": 157,
  "versionName": "v7.2.4",
  "mandatory": true,
  "url": "https://github.com/prozizou/Sketch_App/releases/tag/v7.2.4",
  "apkUrl": "…/NewWorldSketchware.apk",
  "sha256": "…",
  "notes": "…"
}
```

Si `versionCode` dépasse celui de l'application installée, un dialogue propose la mise à jour (non fermable quand
`mandatory` vaut `true`). Le fichier est écrit uniquement par le workflow Release.

## Formats de données utiles

Les blocs personnalisés sont stockés dans le dossier de données de l'application :

- `palette.json` : liste de palettes (la palette *i* correspond à l'identifiant *i + 9*).
- `block.json` : liste de blocs avec `name`, `type` (`" "` pour un bloc ordinaire), `typeName`, `spec`, `spec2`,
  `color`, `imports`, `code`, `palette` (`"-1"` = corbeille).

Format de spec natif : des mots et des paramètres `%s` (texte), `%b` (booléen), `%d` (nombre), `%s.menu`, `%m.menu`
séparés par des espaces — sans nom ni valeur par défaut. Le code Java généré est un
`String.format(code, entrées…, sous-pile1, sous-pile2)` où tous les arguments sont des chaînes.

## Contribuer

1. Créer une branche depuis `main` et garder les changements petits et ciblés.
2. Lancer en local `assembleDebug`, `lintDebug` et `testDebugUnitTest`.
3. Ajouter des tests pour toute logique nouvelle (de préférence en Java pur, testable sans Android).
4. Ne pas modifier les formats de projet existants et ne rien supprimer sans remplacement.
5. Ouvrir une Pull Request vers `main`. Respecter le [code de conduite](CODE_OF_CONDUCT.md).

## Limites connues

- Pas de nom ni de valeur par défaut pour les paramètres de bloc (le format natif n'en contient pas).
- Pas de pipette dans le sélecteur de couleur (aucune API Android).
- Les `imports` d'un bloc personnalisé sont enregistrés mais **ignorés** par le générateur de code actuel.
- Les modèles AdMob / Firebase supposent que la bibliothèque correspondante est dans le projet.
- Unity Ads : la logique (réglages, génération du code, blocs) est testée sur JVM ; l'écran, le téléchargement du SDK et la
  compilation d'un projet utilisant le SDK **n'ont pas été essayés sur un appareil**. La bannière se place dans une vue
  existante (bloc « show banner … inside »), il n'y a pas de composant bannière dans l'éditeur de vues.
- Le « débogueur de blocs » est une simulation, pas un débogueur en direct sur l'appareil.
- Multi-sélection et application des guides (aimantation, alignement) à un projet : non réalisées ;
  voir [`docs/IDE_ROADMAP.md`](docs/IDE_ROADMAP.md) pour l'état exact de chaque chantier.

## Crédits et licence

NWS est construit sur **Sketchware Pro** et sur le travail de ses contributeurs. Le fichier de licence mentionne des
modules Kotlin issus de [CodeAssist](https://github.com/tyron12233/CodeAssist) (GPL-3.0) ; ils ne sont pas présents
comme modules de ce dépôt, la compilation Kotlin passant par les dépendances Gradle déclarées dans
`gradle/libs.versions.toml`.

Le code n'est **pas** open source au sens strict : il est *source-available*. Voir [`LICENSE.md`](LICENSE.md) avant
toute réutilisation en dehors d'un usage personnel.
