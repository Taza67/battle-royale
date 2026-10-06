# Contribuer

Merci de votre intérêt pour **battle-royale**. Le projet est un battle royale en Java 17 : un module `battle-royale-game` (moteur de simulation, rendu LWJGL/OpenGL, serveur TCP) et un module `battle-royale-server` (Tomcat embarqué, WebSocket, manette et panneau d'administration web), construits avec Gradle.

## Avant de commencer

- Recherchez dans les [issues existantes](https://github.com/Taza67/battle-royale/issues) pour éviter de faire un travail en double.
- Pour les changements importants ou structurels, ouvrez d'abord une issue.
- Toute évolution des échanges réseau doit respecter [docs/PROTOCOLE.md](docs/PROTOCOLE.md) — mettez le document à jour dans la même PR.

## Environnement de développement

### Prérequis

- JDK 17 ou plus récent (le wrapper Gradle est fourni)
- Une carte graphique compatible OpenGL 3.3 pour tester le jeu

### Cloner et compiler

```bash
git clone https://github.com/Taza67/battle-royale.git
cd battle-royale
./gradlew build
```

## Pull requests

1. Forkez le dépôt et créez une branche depuis `main`.
2. Faites des changements ciblés ; gardez les PR faciles à relire.
3. Vérifiez que `./gradlew build` se compile sans erreur et que les tests passent.
4. Ouvrez une pull request avec une description claire et liez les issues concernées.

## Messages de commit

Suivez [Conventional Commits](https://www.conventionalcommits.org/).

- **Types :** `feat`, `fix`, `refactor`, `docs`, `test`, `chore`
- **Scopes usuels :** `(game)`, `(server)`, `(web)`, `(gamepad)`, `(admin)`, `(protocole)`
- **Description :** à l'impératif, en minuscules, sans point final
- **Corps :** facultatif ; ligne vide après la description, puis des puces `-` — en minuscules sauf noms propres, à l'impératif, sans point final

## Organisation du code

| Répertoire | Rôle |
|------------|------|
| `battle-royale-game/src/main/java/inside/` | Moteur de simulation : plateau, joueurs, zone sûre, robots, géométrie |
| `battle-royale-game/src/main/java/outside/` | Intégration : rendu, son, entrées clavier, boucle principale, serveur TCP |
| `battle-royale-game/src/main/java/outside/communication/` | Protocole binaire jeu ↔ serveur |
| `battle-royale-server/src/main/java/communication/` | Serveur web : WebSocket, sessions, liaison TCP avec le jeu |
| `battle-royale-server/src/main/webapp/gamepad/` | Manette web des joueurs |
| `battle-royale-server/src/main/webapp/adminPanel/` | Panneau d'administration |
| `battle-royale-server/src/main/webapp/common/` | Code web partagé (connexion, protocole, serveur factice de démo) |
| `docs/` | Documentation (protocoles) |

## Code de conduite

Ce projet suit le [Contributor Covenant](CODE_OF_CONDUCT.md). En y participant, vous acceptez de le respecter.
