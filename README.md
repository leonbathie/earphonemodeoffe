# EarphoneModeOff

**Votre téléphone croit qu'un casque est branché alors qu'il n'y a rien ?** Cette app diagnostique
le problème et tente de rebasculer le son vers le haut-parleur.

[![Build APK](https://github.com/leonbathie/earphonemodeoffe/actions/workflows/build-apk.yml/badge.svg)](https://github.com/leonbathie/earphonemodeoffe/actions/workflows/build-apk.yml)
[![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](#compatibilité)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)

---

## Téléchargement

**[⬇️ Télécharger app-debug.apk](https://github.com/leonbathie/earphonemodeoffe/releases/download/debug-latest/app-debug.apk)**

Le lien pointe toujours vers la dernière version, reconstruite automatiquement à chaque
modification du code.

### Installation

1. Ouvrez le lien ci-dessus depuis le téléphone
2. Android bloquera l'installation la première fois → autorisez votre navigateur dans
   **Paramètres → Installer des applications inconnues**
3. Ouvrez le fichier depuis **Téléchargements** → **Installer**
4. Play Protect affichera un avertissement, l'APK n'étant pas signé par le Play Store →
   **Installer quand même**

L'APK est signé avec la clé de debug Android. C'est sans risque pour un usage personnel, mais
cela signifie qu'il ne peut pas être publié tel quel sur le Play Store.

---

## Ce que l'app fait — et ne fait pas

Soyons directs, pour vous éviter une déception.

### ✅ Fonctionne sans condition

- **Diagnostic audio en direct** — l'état réel du routage, rafraîchi chaque seconde : le
  téléphone croit-il qu'un casque est branché, quel périphérique de sortie est actif, le
  forçage est-il accepté ou rejeté par le système
- **Test en conditions réelles** — joue un son en `USAGE_MEDIA` / `STREAM_MUSIC`, c'est-à-dire
  exactement le chemin audio de TikTok ou YouTube, et non un flux d'alarme qui donnerait un
  faux positif
- **Coupure automatique** au débranchement du casque (optionnelle)
- **Restauration** du routage par défaut en un bouton, si un réglage a laissé le son coincé

### ⚠️ Ne fonctionne pas sans root

**Rediriger le son d'une autre application vers le haut-parleur.** L'app essaie, mais Android
le lui refuse, et il faut comprendre pourquoi avant de télécharger.

Le forçage passe par `AudioSystem.setForceUse`, protégée par la permission
`MODIFY_AUDIO_ROUTING` de niveau `signature|privileged` : elle est réservée aux applications
signées par le constructeur ou installées en partition système. La bibliothèque
`hiddenapibypass` permet bien d'*appeler* la méthode cachée, mais elle ne contourne pas le
contrôle de permission effectué côté serveur audio, qui rejette l'appel. Cette permission n'est
pas non plus attribuable via `adb shell pm grant`, réservé aux permissions de niveau
`development`.

C'est une limite d'Android, pas un défaut de cette app. Sur un téléphone rooté, le forçage
fonctionne. Sur un téléphone standard, la carte de diagnostic affichera
`setForceUse: SANS EFFET`.

Les méthodes `setSpeakerphoneOn` et `setCommunicationDevice`, elles, sont bien accessibles —
mais elles ne pilotent que l'audio de communication (appels, VoIP), jamais `STREAM_MUSIC`.

---

## Lire le diagnostic

| Champ | Ce qu'il signifie |
|---|---|
| `Audio Mode` | Mode audio courant. `NORMAL` en usage média classique |
| `SpeakerphoneOn` | Le haut-parleur est demandé pour l'audio de communication |
| `WiredHeadset Detected` | **Le champ décisif.** `true` sans casque branché = le téléphone est bloqué en mode écouteurs |
| `CommDevice` | Périphérique de sortie retenu pour la communication |
| `Media Volume` | Volume média actuel sur le maximum |
| `setForceUse` | Résultat réel du forçage : `applique sans erreur`, `SANS EFFET -> ...` (refus système), ou `API INACCESSIBLE` |

---

## Le son ne bascule pas ? Les vraies solutions

### 1. Le sélecteur de sortie d'Android

Le seul contournement logiciel qui fonctionne sans permission particulière, parce qu'il est
intégré au système. Lancez la vidéo, déroulez le volet de notifications, touchez l'icône de
sortie audio en haut à droite du panneau média, choisissez **Haut-parleur du téléphone**.

À refaire à chaque session, mais efficace immédiatement.

### 2. Nettoyer la prise

Si `WiredHeadset Detected` affiche `true` sans casque branché, la cause est presque toujours
physique : poussière, peluche ou humidité dans la prise jack ou le port USB-C maintient le
contact de détection fermé.

Téléphone éteint, nettoyez à sec (cure-dent en bois, air comprimé), puis à l'alcool
isopropylique si nécessaire. Branchez et débranchez un vrai casque cinq ou six fois pour
décoincer le contact, et redémarrez.

### 3. Isoler la cause

Démarrez en mode sans échec : appui long sur le bouton d'alimentation, puis appui long sur
**Éteindre**. Si le son sort du haut-parleur en mode sans échec, une application installée
interfère. S'il reste muet, la détection matérielle est en cause — et si le nettoyage n'a rien
changé, le contact de détection est défectueux, ce qui relève d'une réparation.

---

## Compatibilité

- **Android 7.0** (API 24) minimum
- Compilé pour **Android 16** (API 36.1)
- Toutes les fonctions de forçage dépendent du constructeur ; le diagnostic, lui, fonctionne
  partout

---

## Compiler le projet

### Via GitHub Actions (aucune installation)

Le workflow [`build-apk.yml`](.github/workflows/build-apk.yml) compile l'APK à chaque push sur
`main` et le publie dans la release `debug-latest`. Il peut aussi être lancé à la main depuis
l'onglet **Actions** → **Run workflow**, ce qui permet de reconstruire l'app depuis un
téléphone.

### En local

Ce projet est un export AI Studio : il **ne contient pas de wrapper Gradle**. Ouvrez-le dans
[Android Studio](https://developer.android.com/studio), qui fournira Gradle et le SDK.

Prérequis si vous compilez en ligne de commande :

| Outil | Version |
|---|---|
| JDK | 17 minimum (21 recommandé) |
| Gradle | **9.3.1 minimum**, imposé par AGP 9.1.1 |
| SDK Android | plateforme `android-36.1` |

```bash
gradle assembleDebug --no-configuration-cache
```

L'option `--no-configuration-cache` est nécessaire : `gradle.properties` active le cache de
configuration, incompatible avec `secrets-gradle-plugin` 2.0.1.

L'APK est produit dans `app/build/outputs/apk/debug/`.

---

## Architecture

| Fichier | Rôle |
|---|---|
| [`MainActivity.kt`](app/src/main/java/com/example/MainActivity.kt) | Interface Compose, diagnostic en direct, test audio |
| [`EarphoneModeService.kt`](app/src/main/java/com/example/EarphoneModeService.kt) | Service de premier plan qui réapplique le routage toutes les 1,5 s et réagit aux changements de lecture |
| [`RoutingDiagnostics.kt`](app/src/main/java/com/example/RoutingDiagnostics.kt) | Appelle `setForceUse` et expose le résultat réel, en distinguant API inaccessible et refus du serveur audio |
| [`BootReceiver.kt`](app/src/main/java/com/example/BootReceiver.kt) | Relance le service au démarrage si le mode haut-parleur était actif |

### Permissions

| Permission | Usage |
|---|---|
| `MODIFY_AUDIO_SETTINGS` | Modifier le routage et les volumes |
| `FOREGROUND_SERVICE` + `..._MEDIA_PLAYBACK` | Maintenir le forçage en arrière-plan |
| `POST_NOTIFICATIONS` | Notification du service (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Réactivation au redémarrage |

Aucune permission d'accès au réseau, au micro ou au stockage. L'app ne collecte et n'envoie
aucune donnée.
