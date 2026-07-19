# PELab

<p align="center">
  <img src="docs/assets/pelab-icon.png" alt="Icône PELab" width="128">
</p>

[English](README.md) | [简体中文](README.zh-CN.md) | **Français**

PELab, abréviation de Penguito Effect Lab, a pour objectif de construire de zéro une application Android complète dotée de fonctionnalités de rendu graphique.

## Avancement actuel du développement

### 0.1

- Mettre en place l'architecture de base du projet et son découpage en modules.

#### 0.1.1

- Créer le projet Android et fixer les versions de la chaîne d'outils ;
- applicationId : `com.penguito.effectlab` ;
- Version minimale prise en charge : Android 8.0 (API 26).

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
