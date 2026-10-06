// Client WebSocket partagé par la manette et le panneau d'administration :
// construction de l'URL, distribution des messages par type, reconnexion
// automatique avec attente exponentielle.

const NOM_POINT_ACCES = "websocketserver";
const APPLICATIONS = ["gamepad", "adminPanel"];
const HOTES_LOCAUX = ["localhost", "127.0.0.1", "[::1]"];

const ATTENTE_INITIALE = 500;
const ATTENTE_MAXIMALE = 10000;

const OUVERT = 1;

/**
 * Indique si la page a été ouverte en mode démonstration (`?mock=1`).
 */
export function estModeDemo(recherche = window.location.search) {
	const valeur = new URLSearchParams(recherche).get("mock");
	return valeur === "1" || valeur === "true";
}

/**
 * Retourne le chemin de contexte de l'application web à partir du chemin de la page,
 * par exemple `/battle-royale-server` pour `/battle-royale-server/gamepad/index.html`.
 */
export function cheminContexte(chemin = window.location.pathname) {
	const segments = chemin.split("/");
	const index = segments.findIndex(segment => APPLICATIONS.includes(segment));

	if (index >= 0)
		return segments.slice(0, index).join("/");

	return chemin.replace(/\/[^/]*$/, "").replace(/\/[^/]*$/, "");
}

/**
 * Retourne l'URL donnée par le paramètre `?ws=`, ou `null` s'il est absent ou
 * refusé : il n'est pris en compte que pour une page servie en local et une URL
 * `ws:` ou `wss:` sur le même hôte (seul le port peut changer), pour qu'un lien
 * piégé ne puisse pas envoyer le mot de passe administrateur à un autre serveur.
 */
export function urlWebSocketForcee(adresse = window.location) {
	const valeur = new URLSearchParams(adresse.search).get("ws");
	if (!valeur)
		return null;

	let url = null;
	try {
		url = new URL(valeur);
	} catch (erreur) {
		// Adresse illisible : refusée ci-dessous.
	}

	const acceptee = url !== null && HOTES_LOCAUX.includes(adresse.hostname)
		&& (url.protocol === "ws:" || url.protocol === "wss:") && url.hostname === adresse.hostname;
	if (!acceptee) {
		console.warn("Paramètre ws ignoré : seule une adresse ws(s) sur le même hôte local est acceptée.");
		return null;
	}
	return url.href;
}

/**
 * Construit l'URL du point d'accès WebSocket à partir de l'adresse de la page :
 * même hôte et même port, `wss` derrière `https`. En local, le paramètre `?ws=`
 * permet de viser un autre port pendant le développement.
 */
export function urlWebSocket(adresse = window.location) {
	const forcee = urlWebSocketForcee(adresse);
	if (forcee !== null)
		return forcee;

	const protocole = adresse.protocol === "https:" ? "wss:" : "ws:";
	return `${protocole}//${adresse.host}${cheminContexte(adresse.pathname)}/${NOM_POINT_ACCES}`;
}

/**
 * Retourne l'URL de la manette à partager aux joueurs.
 */
export function urlManette(adresse = window.location) {
	return `${adresse.origin}${cheminContexte(adresse.pathname)}/gamepad/`;
}

/**
 * Connexion WebSocket robuste : les messages JSON sont distribués aux gestionnaires
 * enregistrés pour leur champ `type`, et la connexion est rétablie automatiquement
 * tant que `fermer()` n'a pas été appelée.
 */
export class Connexion {
	#url;
	#fabrique;
	#socket = null;
	#gestionnaires = new Map();
	#ecouteursOuverture = new Set();
	#ecouteursEtat = new Set();
	#etat = "deconnectee";
	#tentatives = 0;
	#minuteur = null;
	#active = false;

	/**
	 * @param {object} options
	 * @param {string} options.url adresse du point d'accès
	 * @param {function(string): WebSocket} [options.fabrique] crée la socket (remplacée par le serveur factice en démonstration)
	 */
	constructor({ url, fabrique = adresse => new WebSocket(adresse) }) {
		this.#url = url;
		this.#fabrique = fabrique;

		window.addEventListener("online", () => this.#reessayerMaintenant());
		document.addEventListener("visibilitychange", () => {
			if (document.visibilityState === "visible")
				this.#reessayerMaintenant();
		});
	}

	/**
	 * État courant : `connexion`, `connectee`, `reconnexion` ou `deconnectee`.
	 */
	get etat() {
		return this.#etat;
	}

	/**
	 * Nombre de tentatives de reconnexion depuis la dernière connexion réussie.
	 */
	get tentatives() {
		return this.#tentatives;
	}

	/**
	 * Indique si la socket est ouverte.
	 */
	get estConnectee() {
		return this.#socket !== null && this.#socket.readyState === OUVERT;
	}

	/**
	 * Ouvre la connexion (sans effet si elle est déjà active).
	 */
	ouvrir() {
		if (this.#active)
			return;

		this.#active = true;
		this.#connecter();
	}

	/**
	 * Ferme définitivement la connexion.
	 */
	fermer() {
		this.#active = false;
		clearTimeout(this.#minuteur);

		if (this.#socket !== null) {
			this.#socket.onclose = null;
			this.#socket.close();
			this.#socket = null;
		}

		this.#changerEtat("deconnectee");
	}

	/**
	 * Enregistre un gestionnaire pour un type de message (`*` pour tous) et
	 * retourne la fonction qui le retire.
	 */
	sur(type, gestionnaire) {
		if (!this.#gestionnaires.has(type))
			this.#gestionnaires.set(type, new Set());

		this.#gestionnaires.get(type).add(gestionnaire);
		return () => this.#gestionnaires.get(type).delete(gestionnaire);
	}

	/**
	 * Enregistre une fonction appelée à chaque ouverture (ou réouverture) de la socket.
	 */
	surOuverture(ecouteur) {
		this.#ecouteursOuverture.add(ecouteur);
		return () => this.#ecouteursOuverture.delete(ecouteur);
	}

	/**
	 * Enregistre une fonction appelée à chaque changement d'état de la connexion.
	 */
	surEtat(ecouteur) {
		this.#ecouteursEtat.add(ecouteur);
		ecouteur(this.#etat, this.#tentatives);
		return () => this.#ecouteursEtat.delete(ecouteur);
	}

	/**
	 * Envoie un message JSON. Retourne `false` si la socket n'est pas ouverte :
	 * les messages ne sont pas mis en attente, ils seraient obsolètes à la reconnexion.
	 */
	envoyer(message) {
		if (!this.estConnectee)
			return false;

		this.#socket.send(JSON.stringify(message));
		return true;
	}

	#connecter() {
		clearTimeout(this.#minuteur);
		this.#changerEtat(this.#tentatives === 0 ? "connexion" : "reconnexion");

		let socket;
		try {
			socket = this.#fabrique(this.#url);
		} catch (erreur) {
			this.#planifierReconnexion();
			return;
		}

		this.#socket = socket;
		socket.onopen = () => {
			this.#tentatives = 0;
			this.#changerEtat("connectee");
			for (const ecouteur of this.#ecouteursOuverture)
				ecouteur();
		};
		socket.onmessage = evenement => this.#distribuer(evenement.data);
		socket.onclose = () => {
			if (this.#socket === socket)
				this.#socket = null;
			this.#planifierReconnexion();
		};
		socket.onerror = () => {};
	}

	#planifierReconnexion() {
		if (!this.#active)
			return;

		const attente = Math.min(ATTENTE_MAXIMALE, ATTENTE_INITIALE * 2 ** this.#tentatives);
		const alea = 0.8 + Math.random() * 0.4;

		this.#tentatives++;
		this.#changerEtat("reconnexion");
		clearTimeout(this.#minuteur);
		this.#minuteur = setTimeout(() => this.#connecter(), attente * alea);
	}

	#reessayerMaintenant() {
		if (!this.#active || this.#socket !== null)
			return;

		this.#connecter();
	}

	#distribuer(donnees) {
		let message;
		try {
			message = JSON.parse(donnees);
		} catch (erreur) {
			console.warn("Message illisible ignoré :", donnees);
			return;
		}

		if (message === null || typeof message !== "object" || typeof message.type !== "string")
			return;

		for (const cle of [message.type, "*"]) {
			const gestionnaires = this.#gestionnaires.get(cle);
			if (gestionnaires === undefined)
				continue;

			for (const gestionnaire of [...gestionnaires]) {
				try {
					gestionnaire(message);
				} catch (erreur) {
					console.error(erreur);
				}
			}
		}
	}

	#changerEtat(etat) {
		if (etat === this.#etat && etat !== "reconnexion")
			return;

		this.#etat = etat;
		for (const ecouteur of this.#ecouteursEtat)
			ecouteur(etat, this.#tentatives);
	}
}

/**
 * Crée la connexion de l'application : vers le serveur réel, ou vers un serveur
 * factice embarqué dans la page en mode démonstration.
 */
export async function creerConnexion(optionsDemo = {}) {
	if (!estModeDemo())
		return new Connexion({ url: urlWebSocket() });

	const { ServeurFactice } = await import("./serveurFactice.js");
	const parametres = new URLSearchParams(window.location.search);
	const serveur = new ServeurFactice({
		...optionsDemo,
		vitesse: Number(parametres.get("vitesse")) || 1,
		motDePasse: parametres.get("mdp") || null
	});

	window.serveurFactice = serveur;
	return new Connexion({ url: "demo://serveur-factice", fabrique: () => serveur.creerSocket() });
}
