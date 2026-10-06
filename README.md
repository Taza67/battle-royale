<a id="readme-top"></a>

[![Contributors][contributors-shield]][contributors-url]
[![Issues][issues-shield]][issues-url]
[![License][license-shield]][license-url]
[![Java][java-shield]][java-url]

<div align="center">

<h3 align="center">battle-royale</h3>

  <p align="center">
    Battle royale en vue de dessus pour un grand écran partagé : le jeu Java/OpenGL s'affiche sur un ordinateur et chaque joueur utilise son téléphone comme manette web. Zone qui se resserre, tir et épée, robots, classement — jusqu'à 50 joueurs.
    <br />
    <br />
    <a href="https://github.com/Taza67/battle-royale/issues/new?labels=bug">Report Bug</a>
    &middot;
    <a href="https://github.com/Taza67/battle-royale/issues/new?labels=enhancement">Request Feature</a>
  </p>
</div>

<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About The Project</a>
      <ul>
        <li><a href="#built-with">Built With</a></li>
      </ul>
    </li>
    <li>
      <a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#prerequisites">Prerequisites</a></li>
        <li><a href="#installation">Installation</a></li>
      </ul>
    </li>
    <li><a href="#usage">Usage</a></li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
  </ol>
</details>

## About The Project

[![Aperçu d'une manche][product-screenshot]](https://github.com/Taza67/battle-royale)

`battle-royale` est un battle royale jouable en groupe autour d'un écran partagé : le jeu (Java, LWJGL/OpenGL) tourne sur un ordinateur pendant que les joueurs se connectent depuis leur téléphone, qui devient une manette web avec joystick, boutons d'attaque, barre de vie et minicarte. Un panneau d'administration web lance, met en pause et arrête les manches.

La simulation est autoritaire à pas fixe (60 Hz) avec snapshots immuables ; la zone sûre se resserre par vagues vers un centre aléatoire et la lave inflige des dégâts croissants hors zone. Robots pour compléter une partie, mode solo au clavier, spectateur, reconnexion par jeton de session, mode démonstration des pages web sans serveur (`?mock=1`).

Projet initialement réalisé en équipe par Mourtaza Akil, Marie-Louise Desselier, Hany Bayazid et Kevin Lieske.

### Built With

* [Java](https://www.java.com/) 17
* [LWJGL](https://www.lwjgl.org/) (OpenGL)
* [Apache Tomcat](https://tomcat.apache.org/) (embarqué, WebSocket)
* [Gradle](https://gradle.org/)

<p align="right"><a href="#readme-top" title="Retour en haut">↑</a></p>

## Getting Started

### Prerequisites

* JDK 17 ou plus récent (le wrapper Gradle est fourni)
* Une carte graphique compatible OpenGL 3.3 pour le jeu

### Installation

```bash
git clone https://github.com/Taza67/battle-royale.git
cd battle-royale
./gradlew build
```

<p align="right"><a href="#readme-top" title="Retour en haut">↑</a></p>

## Usage

Partie solo au clavier, avec robots :

```sh
./gradlew :battle-royale-game:run --args="--mode solo --bots 9"
```

Partie multijoueur avec manettes web :

```sh
# terminal 1 : le serveur web (manettes + admin)
./gradlew :battle-royale-server:run

# terminal 2 : le jeu
./gradlew :battle-royale-game:run --args="--mode multi"
```

Puis ouvrir `http://localhost:8080/battle-royale-server/` sur les téléphones (même réseau) et `http://localhost:8080/battle-royale-server/adminPanel/` pour administrer.

Touches : `ZQSD`/`WASD` ou flèches pour bouger, `Maj` courir, `Espace`/`J` tirer, `K`/`Entrée` coup d'épée, `P` pause, `F11` plein écran, `Échap` quitter.

Le contrat complet des deux protocoles (TCP jeu ↔ serveur, WebSocket serveur ↔ navigateur) est documenté dans [docs/PROTOCOLE.md](docs/PROTOCOLE.md).

<p align="right"><a href="#readme-top" title="Retour en haut">↑</a></p>

## Contributing

Voir [CONTRIBUTING.md](CONTRIBUTING.md). Veuillez lire [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) avant de participer.

<p align="right"><a href="#readme-top" title="Retour en haut">↑</a></p>

## License

Distribué sous la licence MIT. Voir [LICENSE](LICENSE) pour plus d'informations.

<p align="right"><a href="#readme-top" title="Retour en haut">↑</a></p>

## Contact

Taza67 - [tazaakil67@gmail.com](mailto:tazaakil67@gmail.com)

Lien du projet : [https://github.com/Taza67/battle-royale](https://github.com/Taza67/battle-royale)

<p align="right"><a href="#readme-top" title="Retour en haut">↑</a></p>

<!-- MARKDOWN LINKS & IMAGES -->
[contributors-shield]: https://img.shields.io/github/contributors/Taza67/battle-royale.svg
[contributors-url]: https://github.com/Taza67/battle-royale/graphs/contributors
[issues-shield]: https://img.shields.io/github/issues/Taza67/battle-royale.svg
[issues-url]: https://github.com/Taza67/battle-royale/issues
[license-shield]: https://img.shields.io/badge/License-MIT-blue.svg
[license-url]: https://github.com/Taza67/battle-royale/blob/main/LICENSE
[java-shield]: https://img.shields.io/badge/Java%2017-ED8B00.svg?logo=openjdk&logoColor=white
[java-url]: https://www.java.com/
[product-screenshot]: images/gameplay.webp
