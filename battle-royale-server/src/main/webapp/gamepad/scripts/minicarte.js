// Mini-carte de la manette : lave hors de la zone sûre, contour de la prochaine
// zone et position du joueur, avec un déplacement lissé entre deux états.

import { CARTE } from "../../common/scripts/protocole.js";

const COULEUR_CHAMP = "rgb(187, 214, 184)";
const COULEUR_GRILLE = "rgba(40, 60, 35, 0.12)";
const COULEUR_LAVE_SOMBRE = "#5c1206";
const COULEUR_LAVE_CLAIRE = "#d8461b";
const COULEUR_BULLE = "rgba(255, 176, 64, 0.45)";
const COULEUR_PROCHAINE_ZONE = "rgba(255, 255, 255, 0.95)";
const COULEUR_JOUEUR = "#1d1d1b";
const COULEUR_VAINQUEUR = "#f5c542";
const COULEUR_ELIMINE = "#8a8a8a";

const RAYON_JOUEUR = 20;
const PAS_GRILLE = 80;
const NOMBRE_BULLES = 36;
const LISSAGE = 12;
const SAUT_MAXIMAL = 250;

const BULLES = Array.from({ length: NOMBRE_BULLES }, (_, index) => {
	const alea = valeur => {
		const sinus = Math.sin(index * 127.1 + valeur * 311.7) * 43758.5453;
		return sinus - Math.floor(sinus);
	};
	return { x: alea(1), y: alea(2), rayon: 0.4 + alea(3) * 0.8, phase: alea(4) * Math.PI * 2 };
});

function interpoler(depart, arrivee, facteur) {
	return depart + (arrivee - depart) * facteur;
}

function lisserZone(affichee, cible, facteur) {
	if (cible === null)
		return null;
	if (affichee === null)
		return { ...cible };

	return {
		x1: interpoler(affichee.x1, cible.x1, facteur),
		y1: interpoler(affichee.y1, cible.y1, facteur),
		x2: interpoler(affichee.x2, cible.x2, facteur),
		y2: interpoler(affichee.y2, cible.y2, facteur)
	};
}

/**
 * Dessine la mini-carte dans un canvas.
 */
export class Minicarte {
	#canvas;
	#contexte;
	#largeur = 0;
	#hauteur = 0;
	#cible = null;
	#affiche = null;
	#animation = null;
	#dernierInstant = 0;

	/**
	 * @param {HTMLCanvasElement} canvas élément de dessin
	 */
	constructor(canvas) {
		this.#canvas = canvas;
		this.#contexte = canvas.getContext("2d");
		new ResizeObserver(() => this.#redimensionner()).observe(canvas);
	}

	/**
	 * Met à jour les données affichées à partir d'un message `state` normalisé.
	 */
	mettreAJour({ x, y, zone, prochaineZone, carte, statut }) {
		this.#cible = { x, y, zone, prochaineZone, carte, statut };

		const affiche = this.#affiche;
		if (affiche === null || Math.hypot(affiche.x - x, affiche.y - y) > SAUT_MAXIMAL)
			this.#affiche = { x, y, zone, prochaineZone };
	}

	/**
	 * Oublie la dernière position connue (nouvelle manche).
	 */
	reinitialiser() {
		this.#cible = null;
		this.#affiche = null;
		this.#dessiner(0);
	}

	/**
	 * Lance l'animation.
	 */
	demarrer() {
		if (this.#animation !== null)
			return;

		this.#redimensionner();
		this.#dernierInstant = performance.now();
		const boucle = instant => {
			this.#dessiner(instant);
			this.#animation = requestAnimationFrame(boucle);
		};
		this.#animation = requestAnimationFrame(boucle);
	}

	/**
	 * Arrête l'animation.
	 */
	arreter() {
		cancelAnimationFrame(this.#animation);
		this.#animation = null;
	}

	#redimensionner() {
		const ratio = window.devicePixelRatio || 1;
		const largeur = this.#canvas.clientWidth;
		const hauteur = this.#canvas.clientHeight;

		if (largeur === 0 || hauteur === 0)
			return;

		this.#largeur = largeur;
		this.#hauteur = hauteur;
		this.#canvas.width = Math.round(largeur * ratio);
		this.#canvas.height = Math.round(hauteur * ratio);
		this.#contexte.setTransform(ratio, 0, 0, ratio, 0, 0);
		this.#dessiner(performance.now());
	}

	#dessiner(instant) {
		const contexte = this.#contexte;
		const duree = Math.min(0.1, Math.max(0, (instant - this.#dernierInstant) / 1000));
		this.#dernierInstant = instant;

		if (this.#largeur === 0)
			return;

		const cible = this.#cible;
		const carte = cible?.carte ?? CARTE;
		const echelle = Math.min(this.#largeur / carte.width, this.#hauteur / carte.height);
		const decalageX = (this.#largeur - carte.width * echelle) / 2;
		const decalageY = (this.#hauteur - carte.height * echelle) / 2;

		if (cible !== null && this.#affiche !== null) {
			const facteur = 1 - Math.exp(-LISSAGE * duree);
			const affiche = this.#affiche;
			affiche.x = interpoler(affiche.x, cible.x, facteur);
			affiche.y = interpoler(affiche.y, cible.y, facteur);
			affiche.zone = lisserZone(affiche.zone, cible.zone, facteur);
			affiche.prochaineZone = lisserZone(affiche.prochaineZone, cible.prochaineZone, facteur);
		}

		contexte.clearRect(0, 0, this.#largeur, this.#hauteur);
		contexte.save();
		contexte.translate(decalageX, decalageY);
		contexte.scale(echelle, echelle);

		const zone = this.#affiche?.zone ?? { x1: 0, y1: 0, x2: carte.width, y2: carte.height };
		this.#dessinerLave(carte, instant);
		this.#dessinerChamp(zone, carte);
		if (this.#affiche?.prochaineZone)
			this.#dessinerProchaineZone(this.#affiche.prochaineZone, echelle, instant);
		if (this.#affiche !== null)
			this.#dessinerJoueur(this.#affiche, cible.statut, echelle, instant);

		contexte.restore();
	}

	#dessinerLave(carte, instant) {
		const contexte = this.#contexte;
		const degrade = contexte.createLinearGradient(0, 0, carte.width, carte.height);
		degrade.addColorStop(0, COULEUR_LAVE_SOMBRE);
		degrade.addColorStop(0.5, COULEUR_LAVE_CLAIRE);
		degrade.addColorStop(1, COULEUR_LAVE_SOMBRE);
		contexte.fillStyle = degrade;
		contexte.fillRect(0, 0, carte.width, carte.height);

		const temps = instant / 1000;
		contexte.fillStyle = COULEUR_BULLE;
		for (const bulle of BULLES) {
			const rayon = (18 + 22 * bulle.rayon) * (0.6 + 0.4 * Math.sin(temps * 1.7 + bulle.phase));
			contexte.beginPath();
			contexte.arc(bulle.x * carte.width, bulle.y * carte.height, Math.max(2, rayon), 0, Math.PI * 2);
			contexte.fill();
		}
	}

	#dessinerChamp(zone, carte) {
		const contexte = this.#contexte;
		const largeur = zone.x2 - zone.x1;
		const hauteur = zone.y2 - zone.y1;

		contexte.save();
		contexte.shadowColor = "rgba(255, 120, 30, 0.9)";
		contexte.shadowBlur = 18;
		contexte.fillStyle = COULEUR_CHAMP;
		contexte.fillRect(zone.x1, zone.y1, largeur, hauteur);
		contexte.restore();

		contexte.save();
		contexte.beginPath();
		contexte.rect(zone.x1, zone.y1, largeur, hauteur);
		contexte.clip();
		contexte.strokeStyle = COULEUR_GRILLE;
		contexte.lineWidth = 2;
		contexte.beginPath();
		for (let x = PAS_GRILLE; x < carte.width; x += PAS_GRILLE) {
			contexte.moveTo(x, 0);
			contexte.lineTo(x, carte.height);
		}
		for (let y = PAS_GRILLE; y < carte.height; y += PAS_GRILLE) {
			contexte.moveTo(0, y);
			contexte.lineTo(carte.width, y);
		}
		contexte.stroke();
		contexte.restore();
	}

	#dessinerProchaineZone(zone, echelle, instant) {
		const contexte = this.#contexte;
		const epaisseur = 2 / echelle;

		contexte.save();
		contexte.strokeStyle = COULEUR_PROCHAINE_ZONE;
		contexte.lineWidth = epaisseur;
		contexte.setLineDash([8 / echelle, 6 / echelle]);
		contexte.lineDashOffset = -(instant / 60) / echelle;
		contexte.strokeRect(zone.x1, zone.y1, zone.x2 - zone.x1, zone.y2 - zone.y1);
		contexte.restore();
	}

	#dessinerJoueur(position, statut, echelle, instant) {
		const contexte = this.#contexte;
		const rayon = Math.max(RAYON_JOUEUR, 6 / echelle);
		const contour = 2.5 / echelle;
		const couleur = statut === "winner" ? COULEUR_VAINQUEUR : statut === "eliminated" ? COULEUR_ELIMINE : COULEUR_JOUEUR;

		if (statut !== "eliminated") {
			const progression = (instant % 1400) / 1400;
			contexte.beginPath();
			contexte.arc(position.x, position.y, rayon * (1 + progression * 1.8), 0, Math.PI * 2);
			contexte.strokeStyle = `rgba(255, 255, 255, ${0.8 * (1 - progression)})`;
			contexte.lineWidth = contour;
			contexte.stroke();
		}

		contexte.beginPath();
		contexte.arc(position.x, position.y, rayon, 0, Math.PI * 2);
		contexte.fillStyle = couleur;
		contexte.fill();
		contexte.strokeStyle = "white";
		contexte.lineWidth = contour;
		contexte.stroke();

		if (statut === "eliminated") {
			const bras = rayon * 0.55;
			contexte.beginPath();
			contexte.moveTo(position.x - bras, position.y - bras);
			contexte.lineTo(position.x + bras, position.y + bras);
			contexte.moveTo(position.x + bras, position.y - bras);
			contexte.lineTo(position.x - bras, position.y + bras);
			contexte.strokeStyle = "#e5483c";
			contexte.lineWidth = contour * 1.4;
			contexte.stroke();
		}
	}
}
