// Serveur factice embarqué dans la page (mode démonstration `?mock=1`).
// Il implémente le côté serveur du protocole WebSocket décrit dans
// docs/PROTOCOLE.md et simule une manche complète avec des joueurs robots.

const LARGEUR_CARTE = 1280;
const HAUTEUR_CARTE = 720;
const VIE_MAXIMALE = 100;
const JOUEURS_MAXIMUM = 50;
const LONGUEUR_PSEUDO_MAXIMALE = 16;

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

const DIRECTIONS = Array.from({ length: 8 }, (_, direction) => {
	const angle = direction * Math.PI / 4;
	return { x: Math.cos(angle), y: -Math.sin(angle) };
});

const STATUTS = { eliminated: "eliminated", alive: "alive", winner: "winner" };

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
		if (this.readyState !== 1)
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
	#admin = null;
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
	 * @param {number} [options.arriveeRobots] intervalle d'arrivée des robots (ms)
	 * @param {number} [options.demarrageAuto] lance la manche ce délai (ms) après l'inscription d'un humain, `0` pour attendre l'administrateur
	 * @param {number} [options.nouvelleMancheAuto] relance une manche ce délai (ms) après la fin, `0` pour attendre l'administrateur
	 * @param {number} [options.vitesse] multiplicateur du temps simulé
	 * @param {string|null} [options.motDePasse] mot de passe administrateur exigé
	 */
	constructor(options = {}) {
		this.#options = {
			robots: 5,
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
	 * Coupe toutes les connexions ouvertes (simulation d'une perte de réseau).
	 */
	couperConnexions() {
		for (const socket of [...this.#sessions])
			socket.couper();
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
	 * Termine immédiatement la manche en éliminant tous les robots sauf un.
	 */
	accelererFin() {
		if (this.#etat !== "running")
			return;

		const vivants = this.#vivants().filter(joueur => joueur.robot);
		for (const joueur of vivants.slice(1))
			this.#infligerDegats(joueur, VIE_MAXIMALE, vivants[0]);
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
				this.#inscrire(socket, message.pseudo);
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

	#inscrire(socket, pseudoRecu) {
		const pseudo = typeof pseudoRecu === "string" ? pseudoRecu.trim() : "";

		if (pseudo.length < 1 || pseudo.length > LONGUEUR_PSEUDO_MAXIMALE) {
			socket.livrer({ type: "rejected", reason: "Le pseudo doit contenir entre 1 et 16 caractères." });
			return;
		}

		if (socket === this.#admin) {
			socket.livrer({ type: "rejected", reason: "Cette session est celle de l'administrateur." });
			return;
		}

		let joueur = [...this.#joueurs.values()].find(candidat => candidat.pseudo.toLowerCase() === pseudo.toLowerCase());

		if (joueur !== undefined) {
			if (joueur.robot || (joueur.session !== null && joueur.session !== socket)) {
				socket.livrer({ type: "rejected", reason: "Ce pseudo est déjà utilisé." });
				return;
			}
		} else {
			if (this.#etat !== "lobby") {
				socket.livrer({ type: "rejected", reason: "Les inscriptions sont fermées : la partie a déjà commencé." });
				return;
			}
			if (this.#joueurs.size >= JOUEURS_MAXIMUM) {
				socket.livrer({ type: "rejected", reason: "La partie est complète." });
				return;
			}

			joueur = this.#creerJoueur(pseudo, false);
			if (this.#options.demarrageAuto > 0 && this.#demarrageAuto === null)
				this.#demarrageAuto = this.#horloge + this.#options.demarrageAuto;
		}

		for (const autre of this.#joueurs.values()) {
			if (autre !== joueur && autre.session === socket) {
				autre.session = null;
				autre.connecte = false;
			}
		}

		joueur.session = socket;
		joueur.connecte = true;
		this.#joueursModifies = true;

		socket.livrer({ type: "welcome", id: joueur.id, pseudo: joueur.pseudo, state: this.#etat });
		if (this.#manche !== null && (this.#etat === "running" || this.#etat === "paused"))
			socket.livrer(this.#messageEtat(joueur));
	}

	#connecterAdmin(socket, motDePasse) {
		if (this.#options.motDePasse !== null && motDePasse !== this.#options.motDePasse) {
			socket.livrer({ type: "rejected", reason: "Mot de passe incorrect." });
			return;
		}

		if (this.#admin !== null && this.#admin !== socket) {
			socket.livrer({ type: "rejected", reason: "Un administrateur est déjà connecté." });
			return;
		}

		this.#admin = socket;
		socket.livrer({ type: "admin-welcome", state: this.#etat });
		socket.livrer({ type: "players", players: this.#listeJoueurs() });
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

		const joueur = {
			id, pseudo, robot,
			session: null,
			connecte: robot,
			statut: STATUTS.alive,
			vie: VIE_MAXIMALE,
			x: LARGEUR_CARTE / 2, y: HAUTEUR_CARTE / 2,
			kills: 0, rang: 0,
			deplacement: null,
			recharge: 0,
			cible: null,
			absenceJusqua: 0
		};

		this.#joueurs.set(id, joueur);
		this.#joueursModifies = true;
		return joueur;
	}

	#executer(commande) {
		switch (commande) {
			case "start":
				if (!["lobby", "over", "stopped"].includes(this.#etat))
					return "La partie est déjà en cours.";
				if (this.#joueurs.size < 2)
					return "Il faut au moins deux joueurs inscrits.";
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
		const joueurs = [...this.#joueurs.values()];
		const total = joueurs.length;

		joueurs.forEach((joueur, index) => {
			const angle = (index / total) * Math.PI * 2;
			joueur.statut = STATUTS.alive;
			joueur.vie = VIE_MAXIMALE;
			joueur.kills = 0;
			joueur.rang = 0;
			joueur.x = Math.round(LARGEUR_CARTE / 2 + Math.cos(angle) * LARGEUR_CARTE * 0.35);
			joueur.y = Math.round(HAUTEUR_CARTE / 2 + Math.sin(angle) * HAUTEUR_CARTE * 0.35);
			joueur.deplacement = null;
			joueur.cible = null;
			joueur.recharge = 0;
		});

		const zone = { x1: 0, y1: 0, x2: LARGEUR_CARTE, y2: HAUTEUR_CARTE };
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
		return [...this.#joueurs.values()].filter(joueur => joueur.statut !== STATUTS.eliminated);
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

		this.#diffuser({
			type: "end",
			winner: vainqueur === null ? null : { id: vainqueur.id, pseudo: vainqueur.pseudo },
			ranking: classement
		}, true);
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
				joueur.x = borner(joueur.x + vecteur.x * longueur, 0, LARGEUR_CARTE);
				joueur.y = borner(joueur.y + vecteur.y * longueur, 0, HAUTEUR_CARTE);
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

		const angle = Math.atan2(-(destination.y - robot.y), destination.x - robot.x);
		const direction = ((Math.round(angle / (Math.PI / 4)) % 8) + 8) % 8;
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
			map: { width: LARGEUR_CARTE, height: HAUTEUR_CARTE }
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
