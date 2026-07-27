# PELab

<p align="center">
  <img src="docs/assets/pelab-icon.png" alt="Icône PELab" width="128">
</p>

[English](README.md) | [简体中文](README.zh-CN.md) | **Français**

PELab, abréviation de Penguito Effect Lab, a pour objectif de construire de zéro une application Android complète dotée de fonctionnalités de rendu graphique.

## Avancement actuel du développement

### 0.2

- Établir la chaîne de rendu caméra de base.

#### 0.2.1

- Centralisation de la vérification, de la demande et du traitement du résultat de l'autorisation caméra dans `render-core-permission` ;
- Ajout de `CaptureActivity` pour vérifier la barrière d'autorisation caméra et le cycle de vie élémentaire de la page.

#### 0.2.2

- Ajout dans `render-core-camera` de la détection des appareils Camera2 avant la création de la caméra ;
- Ajout de `CameraErrorListener` pour transmettre à la couche métier les erreurs d'accès à la caméra, d'autorisation, de détection des appareils et de configuration.

#### 0.2.3

- Ajout de `Camera2Manager` dans `render-core-camera` pour créer et gérer le cycle de vie de `CameraDevice` et de `CaptureSession`.

#### 0.2.4

- Ajout d'une méthode d'initialisation à `RenderEngine` dans `render-sdk` pour créer et libérer l'environnement GL.

### 0.1

- Mettre en place l'architecture de base du projet et son découpage en modules.

#### 0.1.1

- Créer le projet Android et fixer les versions de la chaîne d'outils ;
- applicationId : `com.penguito.effectlab` ;
- Version minimale prise en charge : Android 8.0 (API 26).

#### 0.1.2

- Finalisation du découpage en modules du projet Android ;
- Ajout des modules Android Library `render-ui`, `render-core-camera`, `render-core-permission`, `render-core-material` et `render-sdk`.

#### 0.1.3

- Vérification de la chaîne d'appel minimale Java → JNI → C++.
- Ajout du point d'entrée Java `RenderEngine` dans `render-sdk` et chargement de `libpelab_sdk.so` ;

## Architecture modulaire

```text
app
└── render-ui
    ├── render-core-camera
    ├── render-core-permission
    ├── render-core-material
    └── render-sdk
```

| Module | Responsabilité |
| --- | --- |
| `app` | Point d'entrée de l'application |
| `render-ui` | Gestion de l'interface et du cycle de vie des pages |
| `render-core-camera` | Module caméra — gestion de l'entrée caméra et de son cycle de vie |
| `render-core-permission` | Module d'autorisations — gestion des demandes d'autorisations et de leurs résultats |
| `render-core-material` | Module de gestion des ressources — stockage et chargement des ressources d'effets |
| `render-sdk` | Module SDK de rendu — fournit les interfaces Java et contient l'implémentation du rendu natif |

## Chaîne d'outils fixée

| Composant | Version |
| --- | --- |
| Cible JDK / JVM | 17 |
| Plugin Android Gradle | 9.2.1 |
| Wrapper Gradle | 9.4.1 |
| Kotlin | Kotlin 2.3.10 intégré à AGP 9.2.1 |
| compileSdk | Android 36.1 |
| targetSdk / minSdk | 36 / 26 |
| NDK | 29.0.14206865 |
| CMake | 3.22.1 |

## Compilation

La compilation actuelle nécessite JDK 17 et Android SDK 36.1.

```bash
./gradlew :app:assembleDebug
```

L'APK de débogage est généré dans `app/build/outputs/apk/debug/app-debug.apk`.

La bibliothèque Native est compilée séparément pour `arm64-v8a`, `armeabi-v7a` et `x86_64`, puis intégrée automatiquement dans l'APK.
