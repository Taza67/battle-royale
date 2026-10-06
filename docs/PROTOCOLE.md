# Protocoles de communication

Le projet est composé de trois briques :

- **le jeu** (`battle-royale-game`) : simulation et affichage OpenGL, serveur TCP ;
- **le serveur web** (`battle-royale-server`) : Tomcat embarqué, point d'accès WebSocket, client TCP du jeu ;
- **les clients web** : la manette (`gamepad/`) et le panneau d'administration (`adminPanel/`).

```
manette(s) ──WebSocket/JSON──┐
                             ├── serveur web ──TCP/binaire── jeu
panneau admin ─WebSocket/JSON┘
```

Constantes partagées : carte de `1280 × 720` pixels, `100` points de vie maximum,
au plus `100` joueurs par manche,
directions numérotées dans le sens trigonométrique avec l'axe Y vers le bas :
`0` est, `1` nord-est, `2` nord, `3` nord-ouest, `4` ouest, `5` sud-ouest, `6` sud, `7` sud-est.

Le jeu accepte jusqu'à `100` joueurs par manche (`MAX_PLAYERS` de
`battle-royale-game`) ; le serveur web n'en inscrit que `50`
(`GameSession.MAX_PLAYERS`), un identifiant par image du panneau
d'administration : la limite effective d'une partie en ligne est donc `50`.

## Jeu ⇄ serveur web (TCP)

Le jeu écoute sur `127.0.0.1:8000` (options `--port` et `--bind`, par exemple
`--bind 0.0.0.0` si le serveur web tourne sur une autre machine). Le serveur web s'y connecte
quand l'administrateur lance la partie (propriétés `battle-royale.game-host` et
`battle-royale.game-port`). Tous les entiers sont en big-endian
(`DataInputStream` / `DataOutputStream`).

### Démarrage

| Sens | Contenu |
| --- | --- |
| serveur → jeu | `int 0` |
| serveur → jeu | `int n`, puis `n` fois `byte id` + `UTF pseudo` |
| jeu → serveur | `boolean accepté` |

### Boucle

Toutes les `50 ms`, le serveur envoie un code `int` :

| Code | Signification | Réponse du jeu |
| --- | --- | --- |
| `L ≥ 0` | `L` octets d'actions suivent (éventuellement aucune) | `int S`, `S` octets d'état, `boolean enCours` |
| `-1` | pause | aucune |
| `-3` | reprise | aucune |
| `-2` | arrêt demandé par l'administrateur | aucune, le jeu termine la manche |

Quand `enCours` vaut `false`, la partie est terminée et l'état reçu est l'état final.

Après `-2`, le serveur continue d'envoyer `int 0` à chaque cycle et lit les états
jusqu'à recevoir `enCours = false` (au plus `2 s`), puis ferme la connexion : la
manche arrêtée a donc elle aussi un état final et un classement. Côté jeu, une
fermeture reçue après `-2` est une fin normale, pas une perte de connexion.

Le jeu ferme toute connexion qui n'a pas terminé la poignée de main en `5 s` et
refuse les pseudos vides ou de plus de 16 caractères.

Actions :

| Action | Octets |
| --- | --- |
| déplacement | `id`, `0`, `direction (0-7)`, `vitesse (0-4)` (`0` = arrêt) |
| attaque | `id`, `1`, `forme` (`1` corps-à-corps, `2` tir) |

Un déplacement reste actif jusqu'au déplacement suivant, à un arrêt explicite ou
au bout de `250 ms` sans nouvelle commande.

### État

En-tête de `23` octets. Sauf mention contraire, les champs d'un octet sont
**non signés** (0-255) ; l'encodeur borne ses valeurs à cet intervalle :

| Champ | Type |
| --- | --- |
| phase (`0` échauffement, `1` combat, `2` terminé) | `byte` |
| joueurs vivants | `byte` (non signé) |
| joueurs au total | `byte` (non signé) |
| identifiant du vainqueur (`-1` si aucun) | `byte` (signé) |
| points de vie maximum | `byte` (non signé) |
| zone sûre actuelle `x1`, `y1`, `x2`, `y2` | `4 × short` |
| prochaine zone sûre `x1`, `y1`, `x2`, `y2` | `4 × short` |
| secondes avant la prochaine étape | `short` |

Puis `9` octets par joueur :

| Champ | Type |
| --- | --- |
| identifiant | `byte` (non signé) |
| statut (`0` éliminé, `1` vivant, `2` vainqueur) | `byte` |
| points de vie | `byte` (non signé) |
| position `x`, `y` | `2 × short` |
| éliminations | `byte` (non signé) |
| classement final (`0` tant que le joueur est en vie) | `byte` (non signé) |

## Clients web ⇄ serveur web (WebSocket)

Point d'accès : `ws(s)://<hôte>/battle-royale-server/websocketserver`, construit
à partir de `window.location`. Chaque message est un objet JSON muni d'un champ `type`.

### Client → serveur

| Message | Rôle |
| --- | --- |
| `{"type":"join","pseudo":"Taza","token":"…"}` | inscription ou reconnexion d'un joueur (pseudo de 1 à 16 caractères, `token` facultatif) |
| `{"type":"move","direction":0,"speed":4}` | déplacement (`speed` `0` = arrêt) |
| `{"type":"attack","form":1}` | attaque (`1` corps-à-corps, `2` tir) ; au plus 4 attaques en file par joueur, les suivantes sont ignorées sans réponse |
| `{"type":"admin-join","password":"…"}` | connexion de l'administrateur |
| `{"type":"admin-command","command":"start"}` | `start`, `pause`, `resume` ou `stop` |
| `{"type":"ping"}` | entretien de la connexion, envoyé par les clients toutes les `20 s` ; ignoré par le serveur |

### Serveur → joueur

| Message | Rôle |
| --- | --- |
| `{"type":"welcome","id":3,"pseudo":"Taza","state":"lobby","token":"…"}` | inscription acceptée, `state` vaut `lobby`, `running`, `paused`, `over` ou `stopped` ; `token` est le jeton de reprise du joueur |
| `{"type":"rejected","code":"…","reason":"…"}` | inscription refusée |
| `{"type":"game","state":"running"}` | changement d'état de la partie |
| `{"type":"state", …}` | état du joueur, voir ci-dessous |
| `{"type":"end","winner":{"id":3,"pseudo":"Taza"},"ranking":[…]}` | fin de partie, `winner` peut valoir `null` |

Message `state` :

```json
{
  "type": "state",
  "status": "alive",
  "life": 87, "maxLife": 100,
  "x": 640, "y": 360,
  "kills": 2, "rank": 0,
  "alive": 5, "total": 8,
  "phase": "battle", "secondsLeft": 12,
  "zone": {"x1": 100, "y1": 50, "x2": 1100, "y2": 650},
  "nextZone": {"x1": 300, "y1": 120, "x2": 900, "y2": 560},
  "map": {"width": 1280, "height": 720}
}
```

Éléments de `ranking` : `{"id":3,"pseudo":"Taza","kills":2,"rank":1}`.
`ranking` ne contient que les joueurs humains ; `end` porte aussi `"total"`, le
nombre de participants robots compris, et `"stopped":true` si l'administrateur a
arrêté la manche. `winner` vaut `null` en cas d'égalité ou si un robot gagne.

### Serveur → administrateur

| Message | Rôle |
| --- | --- |
| `{"type":"admin-welcome","state":"lobby"}` | connexion acceptée |
| `{"type":"rejected","code":"…","reason":"…"}` | connexion refusée |
| `{"type":"players","players":[…]}` | liste des joueurs à chaque changement (au plus 2 fois par seconde en partie) |
| `{"type":"ack","command":"start","ok":true,"error":null}` | résultat d'une commande |
| `{"type":"game","state":"running"}` | changement d'état de la partie |
| `{"type":"end", …}` | fin de partie, même format que pour les joueurs |

Éléments de `players` :
`{"id":3,"pseudo":"Taza","connected":true,"status":"alive","life":87,"kills":2,"rank":0}`.

Message `rejected` : `code` est un identifiant stable destiné aux comparaisons
par les clients (`reason` reste le texte affiché, en français). Codes envoyés :
`pseudo-taken` (pseudo associé à une session ouverte), `session-taken-over`
(ancienne connexion d'un joueur remplacée par une reprise), `admin-replaced`
(ancienne session administrateur remplacée) et `game-refused` pour tout autre
refus.

### Règles

- Les inscriptions ne sont acceptées qu'avant le lancement de la partie.
- Un joueur déconnecté peut se reconnecter avec le même pseudo à tout moment ;
  il reçoit `welcome` avec l'état courant de la partie.
- Un pseudo déjà associé à une session ouverte est refusé, sauf si `join` fournit
  le `token` reçu dans le `welcome` de ce pseudo : la nouvelle connexion remplace
  alors l'ancienne, qui reçoit `rejected` (« Session reprise par une autre
  connexion ») puis est fermée. Le jeton est aléatoire (128 bits) et propre à
  chaque pseudo pour la durée de vie du serveur.
- Le serveur envoie un ping WebSocket toutes les `10 s` et ferme une session
  muette depuis `30 s` ; un envoi bloqué plus de `10 s` ferme aussi la session.
  En partie, un client qui ne reçoit rien pendant `5 s` ferme son socket et se
  reconnecte.
- Une session déjà inscrite (joueur ou administrateur) qui envoie `join` ou
  `admin-join` reçoit `rejected` et reste dans son rôle.
- Le serveur n'accepte que les connexions WebSocket dont l'en-tête `Origin` est
  absent ou correspond à l'hôte de la requête. Derrière un mandataire qui termine
  TLS, le schéma vu par le client est lu depuis `Forwarded`/`X-Forwarded-Proto`.
  Après 5 mots de passe
  administrateur erronés, la session est fermée.
- Seule la session administrateur peut envoyer `admin-command`.
- Si la propriété `battle-royale.admin-password` est définie, `admin-join` doit
  fournir ce mot de passe ; sinon la place d'administrateur revient à la première
  session qui la réclame tant qu'elle reste connectée.
- Un joueur qui s'inscrit entre deux manches reçoit `welcome` puis le dernier `end`
  disponible, pour afficher le résultat de la manche écoulée.
- La limite de 5 mots de passe administrateur erronés s'applique par connexion :
  rouvrir une session WebSocket remet le compteur à zéro.
