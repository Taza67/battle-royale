// Commandes de la manette : joystick tactile (nipplejs), boutons A et B
// (pointeur : tactile, souris ou stylet) et clavier pour les tests sur ordinateur.

import { ATTAQUE } from "../../common/scripts/protocole.js";

const COULEUR_JOYSTICK = "rgba(120, 113, 129, 0.9)";

const PERIODE_ENTRETIEN = 100;
const PERIODE_REPETITION_ATTAQUE = 350;
const DELAI_CLIC_APRES_APPUI = 1000;
const ZONE_MORTE = 0.15;
const VITESSE_MAXIMALE = 4;
const VITESSE_MARCHE = 2;

const TOUCHES_DIRECTION = {
	ArrowUp: [0, -1], KeyW: [0, -1],
	ArrowDown: [0, 1], KeyS: [0, 1],
	ArrowLeft: [-1, 0], KeyA: [-1, 0],
	ArrowRight: [1, 0], KeyD: [1, 0]
};

const TOUCHES_ATTAQUE = {
	KeyJ: ATTAQUE.tir, Space: ATTAQUE.tir,
	KeyK: ATTAQUE.corpsACorps, Enter: ATTAQUE.corpsACorps, NumpadEnter: ATTAQUE.corpsACorps
};

/**
 * Convertit un vecteur écran (axe Y vers le bas) en direction du protocole :
 * `0` est, `2` nord, `4` ouest, `6` sud.
 */
export function directionDepuisVecteur(dx, dy) {
	const angle = Math.atan2(-dy, dx);
	return ((Math.round(angle / (Math.PI / 4)) % 8) + 8) % 8;
}

/**
 * Convertit la force du joystick (distance rapportée au rayon) en vitesse de 0 à 4.
 */
export function vitesseDepuisForce(force) {
	if (!(force >= ZONE_MORTE))
		return 0;

	return Math.min(VITESSE_MAXIMALE, Math.ceil(Math.min(force, 1) * VITESSE_MAXIMALE));
}

/**
 * Gère l'ensemble des commandes et envoie les messages `move` et `attack`.
 */
export class Commandes {
	#envoyer;
	#surAttaque;
	#active = false;
	#joystick = null;
	#zoneJoystick;
	#tailleJoystick = 0;
	#deplacementJoystick = { direction: 0, vitesse: 0 };
	#deplacementClavier = { direction: 0, vitesse: 0 };
	#courant = { direction: 0, vitesse: 0 };
	#entretien = null;
	#touches = new Set();
	#repetitions = new Map();
	#observateur;

	/**
	 * @param {object} options
	 * @param {HTMLElement} options.zoneJoystick conteneur du joystick
	 * @param {HTMLButtonElement[]} options.boutons boutons d'attaque portant un attribut `data-forme`
	 * @param {function(object): boolean} options.envoyer envoie un message au serveur
	 * @param {function(number): void} [options.surAttaque] appelée à chaque attaque envoyée
	 */
	constructor({ zoneJoystick, boutons, envoyer, surAttaque = () => {} }) {
		this.#envoyer = envoyer;
		this.#surAttaque = surAttaque;
		this.#zoneJoystick = zoneJoystick;

		for (const bouton of boutons)
			this.#lierBouton(bouton);

		window.addEventListener("keydown", evenement => this.#toucheEnfoncee(evenement));
		window.addEventListener("keyup", evenement => this.#toucheRelachee(evenement));
		window.addEventListener("blur", () => this.#toutRelacher());

		this.#observateur = new ResizeObserver(() => this.#creerJoystick());
		this.#observateur.observe(zoneJoystick);
	}

	/**
	 * Active ou désactive les commandes. La désactivation envoie un arrêt
	 * explicite et suspend les attaques répétées ; la réactivation réapplique
	 * le joystick, les touches et les boutons restés enfoncés entre-temps
	 * (pause, reconnexion).
	 */
	activer(active) {
		if (this.#active === active)
			return;

		this.#active = active;
		for (const repetition of this.#repetitions.values()) {
			if (active)
				this.#lancerRepetition(repetition);
			else
				this.#suspendreRepetition(repetition);
		}
		this.#appliquer();
	}

	/**
	 * Indique si les commandes sont actives.
	 */
	get active() {
		return this.#active;
	}

	/**
	 * Déplacement courant, `{direction, vitesse}`.
	 */
	get deplacement() {
		return { ...this.#courant };
	}

	// Joystick ///////////////////////////////////////////////////////////////

	#creerJoystick() {
		const largeur = this.#zoneJoystick.clientWidth;
		const hauteur = this.#zoneJoystick.clientHeight;
		const taille = Math.round(Math.max(60, Math.min(largeur, hauteur) * 0.75));

		if (largeur === 0 || hauteur === 0 || Math.abs(taille - this.#tailleJoystick) < 8)
			return;

		this.#tailleJoystick = taille;
		if (this.#joystick !== null) {
			this.#joystick.destroy();
			this.#deplacementJoystick = { direction: 0, vitesse: 0 };
			this.#appliquer();
		}

		this.#joystick = nipplejs.create({
			zone: this.#zoneJoystick,
			mode: "static",
			position: { left: "50%", top: "50%" },
			size: taille,
			color: COULEUR_JOYSTICK,
			restOpacity: 0.75
		});

		this.#joystick.on("move", (evenement, donnees) => {
			const centre = donnees.instance.position;
			const direction = directionDepuisVecteur(donnees.position.x - centre.x, donnees.position.y - centre.y);
			this.#deplacementJoystick = { direction, vitesse: vitesseDepuisForce(donnees.force) };
			this.#appliquer();
		});
		this.#joystick.on("end", () => {
			this.#deplacementJoystick = { direction: this.#deplacementJoystick.direction, vitesse: 0 };
			this.#appliquer();
		});
	}

	// Déplacement ////////////////////////////////////////////////////////////

	#appliquer() {
		const precedent = this.#courant;
		const voulu = this.#deplacementJoystick.vitesse > 0 ? this.#deplacementJoystick : this.#deplacementClavier;
		// À l'arrêt, la direction courante est conservée (orientation du personnage).
		const suivant = this.#active && voulu.vitesse > 0 ? voulu : { direction: precedent.direction, vitesse: 0 };

		this.#courant = { ...suivant };

		if (suivant.vitesse === 0) {
			clearInterval(this.#entretien);
			this.#entretien = null;
			if (precedent.vitesse !== 0)
				this.#envoyerDeplacement();
			return;
		}

		if (suivant.vitesse !== precedent.vitesse || suivant.direction !== precedent.direction)
			this.#envoyerDeplacement();

		if (this.#entretien === null)
			this.#entretien = setInterval(() => this.#envoyerDeplacement(), PERIODE_ENTRETIEN);
	}

	#envoyerDeplacement() {
		this.#envoyer({ type: "move", direction: this.#courant.direction, speed: this.#courant.vitesse });
	}

	// Attaques ///////////////////////////////////////////////////////////////

	#attaquer(forme) {
		if (!this.#active)
			return;

		if (this.#envoyer({ type: "attack", form: forme }))
			this.#surAttaque(forme);
	}

	// Une répétition existe tant que son bouton ou sa touche reste enfoncé ; son
	// minuteur ne tourne que lorsque les commandes sont actives.
	#commencerRepetition(cle, forme, bouton = null) {
		if (this.#repetitions.has(cle))
			return;

		bouton?.classList.add("bouton-jeu-actif");
		const repetition = { forme, bouton, minuteur: null };
		this.#repetitions.set(cle, repetition);
		if (this.#active)
			this.#lancerRepetition(repetition);
	}

	#lancerRepetition(repetition) {
		if (repetition.minuteur !== null)
			return;

		this.#attaquer(repetition.forme);
		repetition.minuteur = setInterval(() => this.#attaquer(repetition.forme), PERIODE_REPETITION_ATTAQUE);
	}

	#suspendreRepetition(repetition) {
		clearInterval(repetition.minuteur);
		repetition.minuteur = null;
	}

	#arreterRepetition(cle) {
		const repetition = this.#repetitions.get(cle);
		if (repetition === undefined)
			return;

		this.#suspendreRepetition(repetition);
		repetition.bouton?.classList.remove("bouton-jeu-actif");
		this.#repetitions.delete(cle);
	}

	#lierBouton(bouton) {
		const forme = Number(bouton.dataset.forme);
		// Instant du dernier appui au pointeur : le « click » qui le suit ne
		// doit pas déclencher une seconde attaque (sur mobile, son `detail`
		// vaut 0 comme pour une activation au clavier).
		let dernierAppui = -Infinity;

		bouton.addEventListener("pointerdown", evenement => {
			evenement.preventDefault();
			dernierAppui = performance.now();
			bouton.setPointerCapture?.(evenement.pointerId);
			this.#commencerRepetition(`pointeur-${evenement.pointerId}`, forme, bouton);
		});

		for (const type of ["pointerup", "pointercancel", "lostpointercapture"])
			bouton.addEventListener(type, evenement => {
				dernierAppui = performance.now();
				this.#arreterRepetition(`pointeur-${evenement.pointerId}`);
			});

		bouton.addEventListener("contextmenu", evenement => evenement.preventDefault());
		bouton.addEventListener("keydown", evenement => {
			if (evenement.code === "Space" || evenement.code === "Enter")
				evenement.stopPropagation();
		});
		bouton.addEventListener("click", evenement => {
			if (performance.now() - dernierAppui > DELAI_CLIC_APRES_APPUI)
				this.#attaquer(forme);
		});
	}

	// Clavier ////////////////////////////////////////////////////////////////

	// Les touches sont enregistrées même inactif (pause, reconnexion) : leur
	// effet est suspendu par `#appliquer` et repris dès la réactivation,
	// comme les boutons du pointeur.
	#toucheEnfoncee(evenement) {
		if (evenement.ctrlKey || evenement.metaKey || evenement.altKey)
			return;
		const cible = evenement.target;
		if (cible instanceof HTMLInputElement || (cible instanceof HTMLButtonElement && !cible.classList.contains("bouton-jeu")))
			return;

		const code = evenement.code;
		if (code in TOUCHES_DIRECTION || code === "ShiftLeft" || code === "ShiftRight") {
			evenement.preventDefault();
			this.#touches.add(code);
			this.#majClavier();
		} else if (code in TOUCHES_ATTAQUE) {
			evenement.preventDefault();
			if (!evenement.repeat)
				this.#commencerRepetition(`touche-${code}`, TOUCHES_ATTAQUE[code], this.#boutonDeForme(TOUCHES_ATTAQUE[code]));
		}
	}

	#toucheRelachee(evenement) {
		const code = evenement.code;
		if (this.#touches.delete(code))
			this.#majClavier();
		this.#arreterRepetition(`touche-${code}`);
	}

	#boutonDeForme(forme) {
		return document.querySelector(`.bouton-jeu[data-forme="${forme}"]`);
	}

	#majClavier() {
		let dx = 0, dy = 0;
		for (const code of this.#touches) {
			const vecteur = TOUCHES_DIRECTION[code];
			if (vecteur !== undefined) {
				dx += vecteur[0];
				dy += vecteur[1];
			}
		}

		const marche = this.#touches.has("ShiftLeft") || this.#touches.has("ShiftRight");
		this.#deplacementClavier = dx === 0 && dy === 0
			? { direction: this.#deplacementClavier.direction, vitesse: 0 }
			: { direction: directionDepuisVecteur(dx, dy), vitesse: marche ? VITESSE_MARCHE : VITESSE_MAXIMALE };
		this.#appliquer();
	}

	#toutRelacher() {
		this.#touches.clear();
		this.#deplacementClavier = { direction: this.#deplacementClavier.direction, vitesse: 0 };
		this.#deplacementJoystick = { direction: this.#deplacementJoystick.direction, vitesse: 0 };
		for (const cle of [...this.#repetitions.keys()])
			this.#arreterRepetition(cle);
		this.#appliquer();
	}
}
