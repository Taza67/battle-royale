// Serveur factice embarqué dans la page (mode démonstration `?mock=1`).
// Il implémente le côté serveur du protocole WebSocket décrit dans
// docs/PROTOCOLE.md et simule une manche complète avec des joueurs robots.

import { CARTE, CODES_REFUS, VIE_MAXIMALE, directionDepuisVecteur, pseudoValide } from "./protocole.js";

const JOUEURS_MAXIMUM = 50;
const ESSAIS_MOT_DE_PASSE = 5;

const REFUS = Object.freeze({
	pseudoInvalide: { code: CODES_REFUS.refusPartie, reason: "Pseudo invalide : 1 à 16 caractères" },
	pseudoPris: { code: CODES_REFUS.pseudoPris, reason: "Pseudo déjà utilisé" },
	inscriptionsFermees: { code: CODES_REFUS.refusPartie, reason: "Inscriptions fermées : la partie a déjà commencé" },
	complet: { code: CODES_REFUS.refusPartie, reason: "Partie complète" },
	sessionReprise: { code: CODES_REFUS.sessionReprise, reason: "Session reprise par une autre connexion" },
	dejaInscrite: { code: CODES_REFUS.refusPartie, reason: "Session déjà inscrite" },
	motDePasse: { code: CODES_REFUS.refusPartie, reason: "Mot de passe administrateur incorrect" },
	adminPris: { code: CODES_REFUS.refusPartie, reason: "Un administrateur est déjà connecté" },
	adminRemplace: { code: CODES_REFUS.adminRemplace, reason: "Session administrateur reprise par une autre connexion" }
});

const DUREE_TICK = 50;
const DUREE_DEPLACEMENT = 250;
const PERIODE_ETAT = 100;
const PERIODE_JOUEURS = 500;
const LATENCE = 15;

const PIXELS_PAR_VITESSE = 2.2;
const DUREE_ECHAUFFEMENT = 5000;
const DUREE_ATTENTE_ZONE = 9000;
const DUREE_RETRECISSEMENT = 5000;
const DEGATS_LAVE_PAR_SECONDE = 9;

const ATTAQUES = {
	1: { portee: 75, degats: 22, recharge: 450 },
	2: { portee: 320, degats: 11, recharge: 650 }
};

const NOMS_ROBOTS = ["Mistral", "Kiwi", "Bastion", "Nova", "Pixel", "Orage", "Lynx", "Comète", "Titan", "Zéphyr", "Gecko", "Brume"];
const PREMIER_ID_ROBOT_JEU = 100;

const DIRECTIONS = Array.from({ length: 8 }, (_, direction) => {
	const angle = direction * Math.PI / 4;
	return { x: Math.cos(angle), y: -Math.sin(angle) };
});

const STATUTS = { eliminated: "eliminated", alive: "alive", winner: "winner" };

function creerJeton() {
	const octets = new Uint8Array(16);
	crypto.getRandomValues(octets);
	return Array.from(octets, octet => octet.toString(16).padStart(2, "0")).join("");
}

/**
 * Socket factice reliée au serveur factice, compatible avec l'interface WebSocket
 * utilisée par la classe Connexion.
 */
class SocketFactice {
	readyState = 0;
	onopen = null;
	onmessage = null;
	onclose = null;
	onerror = null;

	#serveur;

	constructor(serveur) {
		this.#serveur = serveur;
		setTimeout(() => {
			if (this.readyState !== 0)
				return;
			this.readyState = 1;
			this.#serveur.ouvrirSession(this);
			this.onopen?.({});
		}, LATENCE * 4);
	}

	/**
	 * Transmet un message texte au serveur factice.
	 */
	send(donnees) {
		if (this.readyState !== 1)
			throw new Error("Socket factice fermée");

		setTimeout(() => this.#serveur.recevoir(this, donnees), LATENCE);
	}

	/**
	 * Ferme la socket.
	 */
	close() {
		if (this.readyState >= 2)
			return;

		this.readyState = 3;
		this.#serveur.fermerSession(this);
		setTimeout(() => this.onclose?.({ code: 1000 }), LATENCE);
	}

	/**
	 * Livre un message du serveur au client.
	 */
	livrer(message) {
		if (this.readyState !== 1 || this.#serveur.muet)
			return;

		const donnees = JSON.stringify(message);
		setTimeout(() => {
			if (this.readyState === 1)
				this.onmessage?.({ data: donnees });
		}, LATENCE);
	}

	/**
	 * Coupe la connexion côté serveur, comme une perte de réseau.
	 */
	couper() {
		if (this.readyState !== 1)
			return;

		this.readyState = 3;
		this.#serveur.fermerSession(this);
		setTimeout(() => this.onclose?.({ code: 1006 }), LATENCE);
	}

	/**
	 * Livre un dernier message puis ferme la connexion côté serveur.
	 */
	congedier(message) {
		this.livrer(message);
		setTimeout(() => {
			if (this.readyState !== 1)
				return;
			this.readyState = 3;
			this.#serveur.fermerSession(this);
			this.onclose?.({ code: 1008 });
		}, LATENCE * 2);
	}
}

function borner(valeur, minimum, maximum) {
	return Math.max(minimum, Math.min(maximum, valeur));
}

function distance(a, b) {
	return Math.hypot(a.x - b.x, a.y - b.y);
}

function copierZone(zone) {
	return { x1: zone.x1, y1: zone.y1, x2: zone.x2, y2: zone.y2 };
}

function arrondirZone(zone) {
	return {
		x1: Math.round(zone.x1), y1: Math.round(zone.y1),
		x2: Math.round(zone.x2), y2: Math.round(zone.y2)
	};
}

function estDansZone(point, zone) {
	return point.x >= zone.x1 && point.x <= zone.x2 && point.y >= zone.y1 && point.y <= zone.y2;
}

/**
 * Serveur de jeu factice : inscriptions, salle d'attente, manche avec zone qui
 * rétrécit, dégâts, éliminations et classement final.
 */
export class ServeurFactice {
	#options;
	#sessions = new Set();
	#joueurs = new Map();
	#robotsJeu = [];
	#admin = null;
	#echecsMotDePasse = new Map();
	#muet = false;
	#derniereFin = null;
	#etat = "lobby";
	#horloge = 0;
	#manche = null;
	#prochainRobot = 0;
	#demarrageAuto = null;
	#dernierEtat = 0;
	#derniersJoueurs = 0;
	#joueursModifies = false;
	#dernieresDonneesJoueurs = "";

	/**
	 * @param {object} options
	 * @param {number} [options.robots] nombre de robots qui rejoignent la salle d'attente
	 * @param {number} [options.robotsJeu] nombre de robots ajoutés par le jeu à chaque manche, absents de la liste des joueurs et du classement
	 * @param {number} [options.arriveeRobots] intervalle d'arrivée des robots (ms)
	 * @param {number} [options.demarrageAuto] lance la manche ce délai (ms) après l'inscription d'un humain, `0` pour attendre l'administrateur
	 * @param {number} [options.nouvelleMancheAuto] relance une manche ce délai (ms) après la fin, `0` pour attendre l'administrateur
	 * @param {number} [options.vitesse] multiplicateur du temps simulé
	 * @param {string|null} [options.motDePasse] mot de passe administrateur exigé
	 */
	constructor(options = {}) {
		this.#options = {
			robots: 5,
			robotsJeu: 0,
			arriveeRobots: 1200,
			demarrageAuto: 0,
			nouvelleMancheAuto: 0,
			vitesse: 1,
			motDePasse: null,
			...options
		};
		this.#prochainRobot = this.#options.arriveeRobots / 2;
		setInterval(() => this.#tick(), DUREE_TICK);
	}

	/**
	 * Crée une socket factice connectée à ce serveur.
	 */
	creerSocket() {
		return new SocketFactice(this);
	}

	/**
	 * État courant de la partie.
	 */
	get etat() {
		return this.#etat;
	}

	/**
	 * Indique si le serveur n'envoie plus rien (connexion à moitié ouverte).
	 */
	get muet() {
		return this.#muet;
	}

	/**
	 * Coupe toutes les connexions ouvertes (simulation d'une perte de réseau).
	 */
	couperConnexions() {
		for (const socket of [...this.#sessions])
			socket.couper();
	}

	/**
	 * Cesse d'envoyer des messages sans fermer les connexions, comme un réseau
	 * qui ne transmet plus rien, ou reprend les envois.
	 */
	rendreMuet(muet = true) {
		this.#muet = muet;
	}

	/**
	 * Renouvelle les jetons de reprise : ceux déjà distribués ne sont plus valables.
	 */
	oublierJetons() {
		for (const joueur of this.#joueurs.values())
			joueur.jeton = creerJeton();
	}

	/**
	 * Ouvre une session supplémentaire, comme un autre appareil, et retourne la
	 * socket et la liste des messages qu'elle reçoit.
	 */
	autreAppareil() {
		const socket = this.creerSocket();
		const recus = [];
		socket.onmessage = evenement => recus.push(JSON.parse(evenement.data));
		return { socket, recus, envoyer: message => socket.send(JSON.stringify(message)) };
	}

	/**
	 * Exécute une commande d'administration sans passer par une session.
	 */
	commande(nom) {
		return this.#executer(nom);
	}

	/**
	 * Inflige des dégâts à un joueur, désigné par son pseudo.
	 */
	blesser(pseudo, degats) {
		const joueur = [...this.#joueurs.values()].find(candidat => candidat.pseudo === pseudo);
		if (joueur !== undefined && this.#etat === "running")
			this.#infligerDegats(joueur, degats, null);
	}

	/**
	 * Termine immédiatement la manche : tous les joueurs sont éliminés sauf le
	 * vainqueur désigné par son pseudo (un robot par défaut).
	 */
	accelererFin(pseudoVainqueur = null) {
		if (this.#etat !== "running")
			return;

		const vivants = this.#vivants();
		const vainqueur = vivants.find(joueur => joueur.pseudo === pseudoVainqueur) ?? vivants.find(joueur => joueur.robot && !joueur.jeu) ?? vivants[0];
		for (const joueur of vivants)
			if (joueur !== vainqueur)
				this.#infligerDegats(joueur, VIE_MAXIMALE, vainqueur);
	}

	/**
	 * Retourne une copie de l'état des joueurs (positions comprises).
	 */
	instantane() {
		return [...this.#joueurs.values()].map(({ id, pseudo, robot, connecte, statut, vie, x, y, kills, rang, deplacement }) =>
			({ id, pseudo, robot, connecte, statut, vie, x, y, kills, rang, deplacement: deplacement && { ...deplacement } }));
	}

	// Sessions ///////////////////////////////////////////////////////////////

	/**
	 * Enregistre une nouvelle session.
	 */
	ouvrirSession(socket) {
		this.#sessions.add(socket);
	}

	/**
	 * Retire une session fermée.
	 */
	fermerSession(socket) {
		this.#sessions.delete(socket);
		this.#echecsMotDePasse.delete(socket);

		if (this.#admin === socket)
			this.#admin = null;

		for (const joueur of this.#joueurs.values()) {
			if (joueur.session === socket) {
				joueur.session = null;
				joueur.connecte = false;
				joueur.deplacement = null;
				this.#joueursModifies = true;
			}
		}
	}

	/**
	 * Traite un message reçu d'une session.
	 */
	recevoir(socket, donnees) {
		if (!this.#sessions.has(socket))
			return;

		let message;
		try {
			message = JSON.parse(donnees);
		} catch (erreur) {
			return;
		}

		switch (message?.type) {
			case "join":
				this.#inscrire(socket, message.pseudo, message.token);
				break;
			case "move":
				this.#deplacer(socket, message);
				break;
			case "attack":
				this.#attaquer(socket, message.form);
				break;
			case "admin-join":
				this.#connecterAdmin(socket, message.password);
				break;
			case "admin-command":
				this.#commanderAdmin(socket, message.command);
				break;
		}
	}

	#inscrire(socket, pseudoRecu, jeton) {
		const pseudo = typeof pseudoRecu === "string" ? pseudoRecu.trim() : "";

		if (socket === this.#admin || this.#joueurDeSession(socket) !== null) {
			socket.livrer({ type: "rejected", ...REFUS.dejaInscrite });
			return;
		}

		if (!pseudoValide(pseudo)) {
			socket.livrer({ type: "rejected", ...REFUS.pseudoInvalide });
			return;
		}

		let joueur = [...this.#joueurs.values()].find(candidat => candidat.pseudo.toLowerCase() === pseudo.toLowerCase());

		if (joueur !== undefined) {
			const ancienne = joueur.session;
			if (joueur.robot || (ancienne !== null && jeton !== joueur.jeton)) {
				socket.livrer({ type: "rejected", ...REFUS.pseudoPris });
				return;
			}
			if (ancienne !== null) {
				joueur.session = null;
				ancienne.congedier({ type: "rejected", ...REFUS.sessionReprise });
			}
		} else {
			if (this.#etat !== "lobby") {
				socket.livrer({ type: "rejected", ...REFUS.inscriptionsFermees });
				return;
			}
			if (this.#joueurs.size >= JOUEURS_MAXIMUM) {
				socket.livrer({ type: "rejected", ...REFUS.complet });
				return;
			}

			joueur = this.#creerJoueur(pseudo, false);
			if (this.#options.demarrageAuto > 0 && this.#demarrageAuto === null)
				this.#demarrageAuto = this.#horloge + this.#options.demarrageAuto;
		}

		joueur.session = socket;
		joueur.connecte = true;
		this.#joueursModifies = true;

		socket.livrer({ type: "welcome", id: joueur.id, pseudo: joueur.pseudo, state: this.#etat, token: joueur.jeton });
		if (this.#manche !== null && (this.#etat === "running" || this.#etat === "paused"))
			socket.livrer(this.#messageEtat(joueur));
		else if (this.#derniereFin !== null && (this.#etat === "over" || this.#etat === "stopped"))
			socket.livrer(this.#derniereFin);
	}

	#connecterAdmin(socket, motDePasse) {
		if (socket === this.#admin || this.#joueurDeSession(socket) !== null) {
			socket.livrer({ type: "rejected", ...REFUS.dejaInscrite });
			return;
		}

		if (this.#options.motDePasse !== null) {
			if (motDePasse !== this.#options.motDePasse) {
				const echecs = (this.#echecsMotDePasse.get(socket) ?? 0) + 1;
				this.#echecsMotDePasse.set(socket, echecs);
				if (echecs >= ESSAIS_MOT_DE_PASSE)
					socket.congedier({ type: "rejected", ...REFUS.motDePasse });
				else
					socket.livrer({ type: "rejected", ...REFUS.motDePasse });
				return;
			}
			if (this.#admin !== null) {
				const ancienne = this.#admin;
				this.#admin = null;
				ancienne.congedier({ type: "rejected", ...REFUS.adminRemplace });
			}
		} else if (this.#admin !== null) {
			socket.livrer({ type: "rejected", ...REFUS.adminPris });
			return;
		}

		this.#echecsMotDePasse.delete(socket);
		this.#admin = socket;
		socket.livrer({ type: "admin-welcome", state: this.#etat });
		socket.livrer({ type: "players", players: this.#listeJoueurs() });
		if (this.#derniereFin !== null && (this.#etat === "over" || this.#etat === "stopped"))
			socket.livrer(this.#derniereFin);
	}

	#commanderAdmin(socket, commande) {
		if (socket !== this.#admin) {
			socket.livrer({ type: "ack", command: commande, ok: false, error: "Commande réservée à l'administrateur." });
			return;
		}

		const erreur = this.#executer(commande);
		socket.livrer({ type: "ack", command: commande, ok: erreur === null, error: erreur });
	}

	#joueurDeSession(socket) {
		for (const joueur of this.#joueurs.values())
			if (joueur.session === socket)
				return joueur;
		return null;
	}

	#deplacer(socket, message) {
		const joueur = this.#joueurDeSession(socket);
		const direction = Number(message.direction);
		const vitesse = Number(message.speed);

		if (joueur === null || !Number.isInteger(direction) || !Number.isInteger(vitesse))
			return;
		if (direction < 0 || direction > 7 || vitesse < 0 || vitesse > 4)
			return;

		joueur.deplacement = vitesse === 0 ? null : { direction, vitesse, expiration: this.#horloge + DUREE_DEPLACEMENT * this.#options.vitesse };
	}

	#attaquer(socket, forme) {
		const joueur = this.#joueurDeSession(socket);
		if (joueur !== null && this.#etat === "running")
			this.#lancerAttaque(joueur, Number(forme));
	}

	// Partie /////////////////////////////////////////////////////////////////

	#creerJoueur(pseudo, robot) {
		let id = 0;
		while (this.#joueurs.has(id))
			id++;

		const joueur = this.#nouveauParticipant(id, pseudo, robot);
		this.#joueurs.set(id, joueur);
		this.#joueursModifies = true;
		return joueur;
	}

	#nouveauParticipant(id, pseudo, robot) {
		return {
			id, pseudo, robot,
			jeu: false,
			jeton: creerJeton(),
			session: null,
			connecte: robot,
			statut: STATUTS.alive,
			vie: VIE_MAXIMALE,
			x: CARTE.width / 2, y: CARTE.height / 2,
			kills: 0, rang: 0,
			deplacement: null,
			recharge: 0,
			cible: null,
			absenceJusqua: 0
		};
	}

	// Joueurs inscrits et robots ajoutés par le jeu pour la manche.
	#participants() {
		return [...this.#joueurs.values(), ...this.#robotsJeu];
	}

	#executer(commande) {
		switch (commande) {
			case "start":
				if (!["lobby", "over", "stopped"].includes(this.#etat))
					return "La partie est déjà en cours.";
				if (![...this.#joueurs.values()].some(joueur => joueur.connecte))
					return "Il faut au moins un joueur connecté.";
				this.#demarrer();
				return null;
			case "pause":
				if (this.#etat !== "running")
					return "La partie n'est pas en cours.";
				this.#changerEtat("paused");
				return null;
			case "resume":
				if (this.#etat !== "paused")
					return "La partie n'est pas en pause.";
				this.#changerEtat("running");
				return null;
			case "stop":
				if (this.#etat !== "running" && this.#etat !== "paused")
					return "Aucune partie à arrêter.";
				this.#terminer(true);
				return null;
			default:
				return "Commande inconnue.";
		}
	}

	#demarrer() {
		this.#robotsJeu = Array.from({ length: this.#options.robotsJeu }, (_, index) => {
			const robot = this.#nouveauParticipant(PREMIER_ID_ROBOT_JEU + index, `Robot ${index + 1}`, true);
			robot.jeu = true;
			return robot;
		});

		const joueurs = this.#participants();
		const total = joueurs.length;

		joueurs.forEach((joueur, index) => {
			const angle = (index / total) * Math.PI * 2;
			joueur.statut = STATUTS.alive;
			joueur.vie = VIE_MAXIMALE;
			joueur.kills = 0;
			joueur.rang = 0;
			joueur.x = Math.round(CARTE.width / 2 + Math.cos(angle) * CARTE.width * 0.35);
			joueur.y = Math.round(CARTE.height / 2 + Math.sin(angle) * CARTE.height * 0.35);
			joueur.deplacement = null;
			joueur.cible = null;
			joueur.recharge = 0;
		});

		const zone = { x1: 0, y1: 0, x2: CARTE.width, y2: CARTE.height };
		this.#manche = {
			phase: "warmup",
			total,
			zone,
			zoneDepart: copierZone(zone),
			prochaineZone: this.#tirerZone(zone),
			etape: "attente",
			finEtape: this.#horloge + DUREE_ECHAUFFEMENT
		};

		this.#demarrageAuto = null;
		this.#changerEtat("running");
	}

	#tirerZone(zone) {
		const largeur = zone.x2 - zone.x1;
		const hauteur = zone.y2 - zone.y1;
		const nouvelleLargeur = Math.max(60, largeur * 0.62);
		const nouvelleHauteur = Math.max(34, hauteur * 0.62);
		const x1 = zone.x1 + Math.random() * (largeur - nouvelleLargeur);
		const y1 = zone.y1 + Math.random() * (hauteur - nouvelleHauteur);

		return arrondirZone({ x1, y1, x2: x1 + nouvelleLargeur, y2: y1 + nouvelleHauteur });
	}

	#changerEtat(etat) {
		this.#etat = etat;
		this.#joueursModifies = true;
		this.#diffuser({ type: "game", state: etat }, true);
	}

	#vivants() {
		return this.#participants().filter(joueur => joueur.statut !== STATUTS.eliminated);
	}

	#lancerAttaque(joueur, forme) {
		const attaque = ATTAQUES[forme];
		if (attaque === undefined || joueur.statut !== STATUTS.alive || joueur.recharge > this.#horloge)
			return;

		joueur.recharge = this.#horloge + attaque.recharge;
		const cibles = this.#vivants()
			.filter(autre => autre !== joueur && distance(autre, joueur) <= attaque.portee)
			.sort((a, b) => distance(a, joueur) - distance(b, joueur));

		if (forme === 1)
			cibles.forEach(cible => this.#infligerDegats(cible, attaque.degats, joueur));
		else if (cibles.length > 0)
			this.#infligerDegats(cibles[0], attaque.degats, joueur);
	}

	#infligerDegats(joueur, degats, auteur) {
		if (joueur.statut !== STATUTS.alive)
			return;

		joueur.vie = Math.max(0, joueur.vie - degats);
		if (joueur.vie > 0)
			return;

		joueur.rang = this.#vivants().length;
		joueur.statut = STATUTS.eliminated;
		joueur.deplacement = null;
		if (auteur !== null && auteur !== joueur)
			auteur.kills++;
		this.#joueursModifies = true;

		const vivants = this.#vivants();
		if (vivants.length <= 1)
			this.#terminer(false);
	}

	#terminer(arret) {
		const vivants = this.#vivants().sort((a, b) => b.vie - a.vie || b.kills - a.kills);
		let vainqueur = null;

		if (!arret && vivants.length === 1) {
			vainqueur = vivants[0];
			vainqueur.statut = STATUTS.winner;
			vainqueur.rang = 1;
		} else {
			vivants.forEach((joueur, index) => joueur.rang = index + 1);
		}

		if (this.#manche !== null)
			this.#manche.phase = "over";

		const classement = [...this.#joueurs.values()]
			.sort((a, b) => a.rang - b.rang)
			.map(joueur => ({ id: joueur.id, pseudo: joueur.pseudo, kills: joueur.kills, rank: joueur.rang }));

		for (const joueur of this.#joueurs.values())
			if (joueur.session !== null)
				joueur.session.livrer(this.#messageEtat(joueur));

		this.#derniereFin = {
			type: "end",
			winner: vainqueur === null || vainqueur.jeu ? null : { id: vainqueur.id, pseudo: vainqueur.pseudo },
			ranking: classement,
			total: this.#manche?.total ?? classement.length,
			stopped: arret
		};
		this.#diffuser(this.#derniereFin, true);
		this.#changerEtat(arret ? "stopped" : "over");

		if (this.#options.nouvelleMancheAuto > 0)
			this.#demarrageAuto = this.#horloge + this.#options.nouvelleMancheAuto;
	}

	// Boucle /////////////////////////////////////////////////////////////////

	#tick() {
		const pas = DUREE_TICK * this.#options.vitesse;
		this.#horloge += pas;

		if (this.#etat === "lobby")
			this.#tickSalleAttente();

		if (this.#demarrageAuto !== null && this.#horloge >= this.#demarrageAuto && this.#etat !== "running" && this.#etat !== "paused")
			this.#demarrageAuto = this.#executer("start") === null ? null : this.#horloge + 1000;

		if (this.#etat === "running")
			this.#tickManche(pas);

		this.#tickAbsences();

		if (this.#horloge - this.#dernierEtat >= PERIODE_ETAT * this.#options.vitesse && (this.#etat === "running" || this.#etat === "paused")) {
			this.#dernierEtat = this.#horloge;
			for (const joueur of this.#joueurs.values())
				if (joueur.session !== null)
					joueur.session.livrer(this.#messageEtat(joueur));
		}

		const periodeJoueurs = this.#etat === "running" ? PERIODE_JOUEURS * this.#options.vitesse : 0;
		if (this.#joueursModifies && this.#horloge - this.#derniersJoueurs >= periodeJoueurs)
			this.#envoyerJoueurs();
	}

	#tickSalleAttente() {
		const robots = [...this.#joueurs.values()].filter(joueur => joueur.robot).length;
		if (robots >= this.#options.robots || this.#horloge < this.#prochainRobot)
			return;

		const nom = NOMS_ROBOTS.find(candidat => ![...this.#joueurs.values()].some(joueur => joueur.pseudo === candidat));
		if (nom !== undefined)
			this.#creerJoueur(nom, true);
		this.#prochainRobot = this.#horloge + this.#options.arriveeRobots * (0.6 + Math.random() * 0.8);
	}

	#tickAbsences() {
		for (const joueur of this.#joueurs.values()) {
			if (!joueur.robot)
				continue;

			if (!joueur.connecte && this.#horloge >= joueur.absenceJusqua) {
				joueur.connecte = true;
				this.#joueursModifies = true;
			} else if (joueur.connecte && Math.random() < 0.0008) {
				joueur.connecte = false;
				joueur.absenceJusqua = this.#horloge + 2500 + Math.random() * 3000;
				this.#joueursModifies = true;
			}
		}
	}

	#tickManche(pas) {
		const manche = this.#manche;
		this.#avancerZone();

		const vieAvant = new Map([...this.#joueurs.values()].map(joueur => [joueur.id, joueur.vie]));

		for (const joueur of this.#vivants()) {
			if (this.#etat !== "running")
				return;

			if (joueur.robot)
				this.#piloterRobot(joueur);

			const deplacement = joueur.deplacement;
			if (deplacement !== null && (joueur.robot || this.#horloge <= deplacement.expiration)) {
				const vecteur = DIRECTIONS[deplacement.direction];
				const longueur = deplacement.vitesse * PIXELS_PAR_VITESSE * this.#options.vitesse;
				joueur.x = borner(joueur.x + vecteur.x * longueur, 0, CARTE.width);
				joueur.y = borner(joueur.y + vecteur.y * longueur, 0, CARTE.height);
			} else if (deplacement !== null) {
				joueur.deplacement = null;
			}

			if (manche.phase === "battle" && !estDansZone(joueur, manche.zone))
				this.#infligerDegats(joueur, DEGATS_LAVE_PAR_SECONDE * pas / 1000, null);
		}

		for (const joueur of this.#joueurs.values())
			if (Math.ceil(joueur.vie) !== Math.ceil(vieAvant.get(joueur.id)))
				this.#joueursModifies = true;
	}

	#avancerZone() {
		const manche = this.#manche;

		if (manche.etape === "attente") {
			if (this.#horloge < manche.finEtape)
				return;

			manche.phase = "battle";
			manche.etape = "retrecissement";
			manche.zoneDepart = copierZone(manche.zone);
			manche.finEtape = this.#horloge + DUREE_RETRECISSEMENT;
			return;
		}

		const progression = 1 - Math.max(0, manche.finEtape - this.#horloge) / DUREE_RETRECISSEMENT;
		for (const cle of ["x1", "y1", "x2", "y2"])
			manche.zone[cle] = manche.zoneDepart[cle] + (manche.prochaineZone[cle] - manche.zoneDepart[cle]) * progression;

		if (progression >= 1) {
			manche.zone = copierZone(manche.prochaineZone);
			manche.prochaineZone = this.#tirerZone(manche.zone);
			manche.etape = "attente";
			manche.finEtape = this.#horloge + DUREE_ATTENTE_ZONE;
		}
	}

	#piloterRobot(robot) {
		const manche = this.#manche;
		const cibleZone = manche.prochaineZone;

		if (robot.cible === null || distance(robot, robot.cible) < 20 || Math.random() < 0.01) {
			robot.cible = {
				x: cibleZone.x1 + Math.random() * (cibleZone.x2 - cibleZone.x1),
				y: cibleZone.y1 + Math.random() * (cibleZone.y2 - cibleZone.y1)
			};
		}

		const adversaire = this.#vivants()
			.filter(autre => autre !== robot)
			.sort((a, b) => distance(a, robot) - distance(b, robot))[0];

		let destination = robot.cible;
		if (manche.phase === "battle" && adversaire !== undefined && distance(adversaire, robot) < 260 && estDansZone(robot, manche.zone))
			destination = adversaire;

		const direction = directionDepuisVecteur(destination.x - robot.x, destination.y - robot.y);
		robot.deplacement = distance(robot, destination) > 12 ? { direction, vitesse: 3, expiration: Infinity } : null;

		if (manche.phase === "battle" && adversaire !== undefined) {
			const ecart = distance(adversaire, robot);
			if (ecart <= ATTAQUES[1].portee && Math.random() < 0.05)
				this.#lancerAttaque(robot, 1);
			else if (ecart <= ATTAQUES[2].portee && Math.random() < 0.025)
				this.#lancerAttaque(robot, 2);
		}
	}

	// Messages ///////////////////////////////////////////////////////////////

	#messageEtat(joueur) {
		const manche = this.#manche;
		const vivants = this.#vivants().length;

		return {
			type: "state",
			status: joueur.statut,
			life: Math.ceil(joueur.vie),
			maxLife: VIE_MAXIMALE,
			x: Math.round(joueur.x),
			y: Math.round(joueur.y),
			kills: joueur.kills,
			rank: joueur.rang,
			alive: vivants,
			total: manche.total,
			phase: manche.phase,
			secondsLeft: manche.phase === "over" ? 0 : Math.max(0, Math.ceil((manche.finEtape - this.#horloge) / 1000)),
			zone: arrondirZone(manche.zone),
			nextZone: arrondirZone(manche.prochaineZone),
			map: { width: CARTE.width, height: CARTE.height }
		};
	}

	#listeJoueurs() {
		return [...this.#joueurs.values()].map(joueur => ({
			id: joueur.id,
			pseudo: joueur.pseudo,
			connected: joueur.connecte,
			status: joueur.statut,
			life: Math.ceil(joueur.vie),
			kills: joueur.kills,
			rank: joueur.rang
		}));
	}

	#envoyerJoueurs() {
		this.#joueursModifies = false;
		this.#derniersJoueurs = this.#horloge;

		const message = { type: "players", players: this.#listeJoueurs() };
		const donnees = JSON.stringify(message);
		if (donnees === this.#dernieresDonneesJoueurs)
			return;

		this.#dernieresDonneesJoueurs = donnees;
		this.#admin?.livrer(message);
	}

	#diffuser(message, admin) {
		for (const joueur of this.#joueurs.values())
			if (joueur.session !== null)
				joueur.session.livrer(message);

		if (admin)
			this.#admin?.livrer(message);
	}
}
