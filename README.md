# Battle Royale

Battle royale en vue de dessus pour un grand écran partagé : le jeu (Java, LWJGL/OpenGL) s'affiche sur un ordinateur, les joueurs se connectent avec leur téléphone, qui devient une manette web. Un panneau d'administration lance, met en pause et arrête les manches.

Projet initialement réalisé en équipe par Mourtaza Akil, Marie-Louise Desselier, Hany Bayazid et Kevin Lieske.

## Fonctionnalités

- Simulation autoritaire à pas fixe (60 Hz) sur un fil dédié, rendu découplé à partir d'instantanés immuables.
- Déplacement à 8 directions et 5 vitesses, collisions avec glissement le long des obstacles, deux attaques (tir et coup au corps à corps) avec temps de recharge.
- Zone sûre qui se resserre par vagues vers un centre aléatoire, dégâts croissants hors zone.
- Robots pour compléter une partie, mode solo au clavier et mode spectateur.
- HUD (vie, éliminations, survivants, zone et compte à rebours), fil des éliminations, minicarte, écran de fin et classement.
- Manette web tactile : joystick, attaques, barre de vie, minicarte, reconnexion automatique avec jeton de reprise de session.
- Panneau d'administration : liste des joueurs, commandes de manche, résultats ; mot de passe optionnel.
- Mode démonstration sans serveur pour les pages web (`?mock=1`).

## Architecture

```text
téléphones / navigateur ──WebSocket JSON──▶ battle-royale-server ──TCP binaire──▶ battle-royale-game
   gamepad/  adminPanel/                    Tomcat embarqué :8080                  127.0.0.1:8000
```

| Module | Contenu |
| --- | --- |
| `battle-royale-game/` | Jeu : moteur (`inside`), rendu, son, entrées et serveur TCP (`outside`) |
| `battle-royale-server/` | Serveur web : Tomcat embarqué, point d'accès WebSocket, liaison TCP avec le jeu, sessions |
| `battle-royale-server/src/main/webapp/` | Pages web : accueil, manette (`gamepad/`), administration (`adminPanel/`), code partagé (`common/`) |
| `docs/PROTOCOLE.md` | Contrat des deux protocoles (TCP jeu ↔ serveur, WebSocket serveur ↔ navigateur) |

## Prérequis

- JDK 17 ou plus récent (le wrapper Gradle est fourni, aucune autre installation n'est nécessaire).
- Une carte graphique compatible OpenGL 3.3 pour le jeu.

## Compiler et tester

```bash
./gradlew build
```

La commande compile les deux modules, exécute les tests JUnit et produit les distributions dans `*/build/distributions/`.

## Jouer en solo

```bash
./gradlew :battle-royale-game:run
```

| Touche | Action |
| --- | --- |
| `ZQSD`/`WASD` ou flèches | Se déplacer |
| `Maj` | Courir |
| `Espace` ou `J` | Tirer |
| `K` ou `Entrée` | Coup au corps à corps |
| `P` | Pause |
| `Entrée` (écran de fin) | Nouvelle partie |
| `F11` | Plein écran |
| `Échap` | Quitter |

Quelques options (`--help` pour la liste complète) :

```bash
./gradlew :battle-royale-game:run --args="--bots 7 --pseudo Taza"
./gradlew :battle-royale-game:run --args="--spectate --seed 42"
```

## Jouer en multijoueur

1. Lancer le jeu sur l'ordinateur relié au grand écran :

   ```bash
   ./gradlew :battle-royale-game:run --args="--mode multi --bots 3"
   ```

2. Lancer le serveur web (dans un autre terminal) :

   ```bash
   ./gradlew :battle-royale-server:run -Dbattle-royale.admin-password=motdepasse
   ```

3. Les joueurs ouvrent `http://<adresse de l'ordinateur>:8080/battle-royale-server/gamepad/` sur leur téléphone (l'adresse est affichée dans la salle d'attente du jeu), choisissent un pseudo, puis l'administrateur lance la manche depuis `http://localhost:8080/battle-royale-server/adminPanel/`.

### Options du jeu

| Option | Rôle | Défaut |
| --- | --- | --- |
| `--mode solo\|multi` | Mode de jeu | `solo` |
| `--bots N` | Nombre de robots | 9 en solo, 0 en multijoueur |
| `--port P` | Port TCP attendu par le serveur web | `8000` |
| `--bind ADRESSE` | Interface d'écoute (`0.0.0.0` si le serveur web tourne sur une autre machine) | `127.0.0.1` |
| `--gamepad-url URL` | Adresse de la manette affichée dans la salle d'attente | `http://<adresse locale>:8080/battle-royale-server/gamepad/` |
| `--warmup S` | Durée de l'échauffement en secondes | `12` |
| `--seed N` | Graine de la carte, des zones et des robots | aléatoire |
| `--no-sound`, `--window LxH` | Son désactivé, taille de la fenêtre | |

### Réglages du serveur web

Propriétés système, à passer avec `-D` à `./gradlew :battle-royale-server:run` ou dans `JAVA_OPTS` pour la distribution :

| Propriété | Rôle | Défaut |
| --- | --- | --- |
| `battle-royale.port` | Port HTTP | `8080` |
| `battle-royale.game-host` | Hôte du jeu | `localhost` |
| `battle-royale.game-port` | Port TCP du jeu | `8000` |
| `battle-royale.admin-password` | Mot de passe du panneau d'administration | aucun : la première session qui le réclame devient administratrice |
| `battle-royale.webapp` | Répertoire des pages web | détecté automatiquement |

### Distributions

```bash
./gradlew installDist
battle-royale-game/build/install/battle-royale-game/bin/battle-royale-game --mode multi
JAVA_OPTS="-Dbattle-royale.admin-password=motdepasse" \
  battle-royale-server/build/install/battle-royale-server/bin/battle-royale-server
```

## Pages web sans serveur

Les pages fonctionnent sans jeu ni serveur avec un serveur factice intégré : `gamepad/?mock=1` et `adminPanel/?mock=1`.

## Dépannage

- **« Le jeu n'est pas lancé »** dans le panneau d'administration : démarrer le jeu en `--mode multi` avant de lancer la manche, et vérifier `battle-royale.game-host`/`game-port`.
- **Les téléphones ne se connectent pas** : ils doivent être sur le même réseau que l'ordinateur et le port 8080 doit être ouvert dans le pare-feu.
- **Serveur web sur une autre machine que le jeu** : lancer le jeu avec `--bind 0.0.0.0` et le serveur avec `-Dbattle-royale.game-host=<adresse du jeu>`. Le port du jeu n'est pas authentifié : ne l'exposer que sur un réseau de confiance.
- **Pseudo déjà utilisé** après une coupure : la manette réessaie automatiquement pendant 30 s, le temps que le serveur détecte la connexion perdue.
