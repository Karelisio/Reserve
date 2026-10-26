# Reserve

Appli Android native (Kotlin, Jetpack Compose, Material 3 Expressive) qui trouve le carburant le moins cher autour de vous. Pas de backend, pas de compte : tout reste sur le téléphone.

Package : `com.karelisio.reserve` · minSdk 26 · targetSdk 36 · compileSdk 37

## Fonctionnalités

**Base**
- Liste triée par prix, distance ou **vrai coût** (plein + carburant brûlé pour le détour aller-retour), rayon de 5 à 30 km, carburant au choix (Gazole, SP95-E10, SP98, E85, GPLc).
- Âge de chaque prix (« il y a 2 h »), signalement des prix de plus de 3 jours et des ruptures.
- Carte MapLibre + tuiles OpenStreetMap (aucune clé Google), style clair/sombre selon le thème, marqueurs du moins cher (vert) au plus cher (rouge) harmonisés avec la couleur dynamique.
- Fiche station : prix de tous les carburants, horaires, 24/24, services, bouton « Y aller » (Waze, Google Maps ou autre appli).
- Favoris et historique local des prix (Room) avec mini-graphiques.
- Widget Glance redimensionnable, couleurs dynamiques, coins arrondis système ; tap = fiche de la station.
- Alertes horaires (WorkManager) : favori sous un seuil ou en baisse de X centimes.
- Hors ligne : dernier résultat en cache.

**Avancé**
- Moins cher sur mon trajet : destination via l'API Adresse, itinéraire OSRM, stations dans un couloir de 2 km, triées par vrai coût.
- Alerte en voiture : à la connexion Bluetooth de la voiture choisie, notification « Moins cher à X km : 1,72 € » (au plus une fois par heure).
- Carnet de pleins : saisie rapide après « Y aller », consommation réelle, budget mensuel, économies vs prix moyen local.
- Maintenant ou attendre ? : tendance sur 7 jours de la moyenne de votre zone (en baisse / stable / en hausse).
- Remises fidélité : cartes déclarées (Leclerc, TotalEnergies, Intermarché…) appliquées aux prix affichés.
- Filtres : 24/24 avec automate CB, lavage, gonflage, boutique, exclure l'autoroute.
- Tuile de réglages rapides : meilleur prix autour de vous.
- Bornes de recharge électrique (puce « Bornes ») : fichier national IRVE de data.gouv.fr, repli OpenStreetMap ; filtres prise, puissance ≥ 50 kW, gratuité.
- Meilleur jour de la semaine, comparaison avec la moyenne nationale, alerte de rupture sur les favoris.
- Widget configurable (moins cher autour ou favori épinglé, carburant propre), raccourcis de l'icône.
- Sauvegarde : export/import JSON, export CSV du carnet, sauvegarde Android automatique.
- Apparence : Système / Clair / Sombre, couleurs dynamiques on/off, noir pur ; écran de démarrage animé.
- Mise à jour intégrée depuis les Releases GitHub (vérification au lancement, téléchargement de l'APK adapté au téléphone, installation confirmée par Android).
- Toutes les autorisations demandées au lancement (localisation, notifications, Bluetooth, puis localisation « Toujours » et installation des mises à jour, expliquées avant) ; état et raccourcis dans Réglages → Autorisations.
- Arrêt sur le trajet : « Via cette station » lance la navigation vers la destination avec la station en étape (Waze, qui ne gère pas les étapes, va d'abord à la station).
- Recherche par nom ou ville, partage d'une station (prix, adresse, lien OpenStreetMap).
- Multi-véhicules : carburant, consommation et réservoir par véhicule ; le véhicule actif pilote le vrai coût.
- Autonomie estimée à partir du carnet (jauge, alerte « sur la réserve »).
- Récap hebdomadaire (notification le dimanche à 18 h : dépenses, économies, meilleur jour pour faire le plein).
- Journal de plantage local et « Signaler un problème » (issue GitHub pré-remplie ou partage).
- Tablette / pliable : barre ou rail de navigation adaptatif, liste + fiche côte à côte à partir de 840 dp.
- Français et anglais (langue du système ; choix par appli dans Réglages → Apparence sur Android 13+), libellés TalkBack et titres de section.
- Android Auto (Car App Library, catégorie `androidx.car.app.category.FUEL`) : stations les moins chères sur la carte du véhicule, fiche et lancement de la navigation.

## Sources de données

| Donnée | Source | Licence |
|---|---|---|
| Prix, adresses, horaires, services | data.economie.gouv.fr, jeu `prix-des-carburants-en-france-flux-instantane-v2` (API Explore v2.1, mis à jour toutes les 10 min) | Licence Ouverte 2.0 |
| Nom et enseigne des stations | OpenStreetMap via Overpass (`amenity=fuel`, rapprochement par position, cache local 30 jours) | ODbL |
| Fond de carte | Tuiles OpenStreetMap | ODbL |
| Adresses (trajet) | api-adresse.data.gouv.fr (repli data.geopf.fr) | Licence Ouverte 2.0 |
| Itinéraire | OSRM (router.project-osrm.org) | Données ODbL |
| Bornes de recharge | Fichier consolidé IRVE, data.gouv.fr (API tabulaire), repli OpenStreetMap | Licence Ouverte 2.0 / ODbL |
| Moyenne nationale | Agrégat de l'API carburants | Licence Ouverte 2.0 |

Mention affichée dans l'appli : « Source : DGCCRF, prix des carburants, data.economie.gouv.fr » (Licence Ouverte 2.0).

## Build local

Prérequis : JDK 21, Android SDK avec `platforms;android-37.0` et `build-tools;36.0.0`.

```bash
./gradlew :app:testDebugUnitTest   # tests unitaires + rendus Robolectric (app/build/screenshots)
./gradlew :app:assembleDebug       # APK debug (suffixe .debug)
```

Les APK sont produits par ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`) et en version universelle.

## APK release signé (GitHub Actions)

Le workflow `.github/workflows/android.yml` lance les tests à chaque push et pull request, puis, sur `main`, sur un tag `v*` ou en lancement manuel, construit l'APK release signé, le publie en artefact **et crée une Release GitHub** (`v1.0.<n°>` ou le tag poussé) avec les APK `reserve-<version>-<versionCode>-<abi>.apk`.

La mise à jour intégrée lit la dernière Release (`/releases/latest`) et compare son `versionCode` (numéro d'exécution de la CI) à celui installé. Elle n'est active que dans les builds release : un build debug (`.debug`) ne peut pas être remplacé par un APK release. Première installation : télécharger l'APK depuis la page Releases ; les suivantes se font depuis l'appli.

Aucune clé ni keystore n'est commité. Créez le keystore en local, hors du dépôt :

```bash
keytool -genkeypair -v -keystore reserve-release.jks -alias reserve \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 reserve-release.jks > keystore.b64   # macOS : base64 -i reserve-release.jks
```

Puis dans *Settings → Secrets and variables → Actions* du dépôt, ajoutez :

| Secret | Valeur |
|---|---|
| `KEYSTORE_BASE64` | contenu de `keystore.b64` |
| `KEYSTORE_PASSWORD` | mot de passe du keystore |
| `KEY_ALIAS` | `reserve` (ou votre alias) |
| `KEY_PASSWORD` | mot de passe de la clé |

Gardez `reserve-release.jks` en lieu sûr : sans lui, impossible de publier une mise à jour installable par-dessus.

En local, la signature se fait avec les variables `RESERVE_KEYSTORE_PATH`, `RESERVE_KEYSTORE_PASSWORD`, `RESERVE_KEY_ALIAS`, `RESERVE_KEY_PASSWORD`.

## Baseline profile

Le module `baselineprofile` enregistre le démarrage et la navigation sur un émulateur ; le profil obtenu est compilé à l'installation (démarrage et défilement plus rapides, y compris hors Play Store grâce à ProfileInstaller). Pour le régénérer : *Actions → Baseline profile → Run workflow* ; le profil est commité automatiquement et sera inclus dans la release suivante.

## Android Auto

Pour une appli installée hors Play Store : dans Android Auto, *Paramètres → version (taper 10 fois) → mode développeur*, puis activer *Sources inconnues*. Test sur ordinateur avec le Desktop Head Unit (DHU) du SDK.

## Permissions

- Localisation (au premier lancement) ; « Toujours » en option pour l'alerte en voiture (sinon dernière position connue).
- Notifications : alertes de prix et alerte en voiture.
- Bluetooth (connexion) : choix de la voiture et détection de la connexion.
- Installation d'applications : mises à jour intégrées.

Toutes sont demandées au premier lancement ; les permissions système refusées deux fois ne sont plus proposées par Android (passer par Réglages → Autorisations).

## Architecture

Module unique `app`, injection manuelle (`AppContainer`) :

- `data/remote` : API carburants, Overpass, Adresse, OSRM (OkHttp + kotlinx.serialization).
- `data/local` : Room (favoris, historique, correspondances OSM, moyennes de zone, pleins, cartes fidélité).
- `data/prefs` : DataStore (réglages).
- `domain` : vrai coût, remises, tendance, carnet, géométrie (testés en unitaire).
- `ui` : écrans Compose (Material 3 Expressive, `MaterialExpressiveTheme`, couleurs dynamiques avec repli vert).
- `widget`, `tile`, `work`, `bluetooth`, `auto` : widget Glance, tuile, WorkManager, alerte voiture, Android Auto.
