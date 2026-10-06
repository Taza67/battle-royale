// Constantes et normalisation des valeurs du protocole (docs/PROTOCOLE.md).

/** Dimensions de la carte par défaut. */
export const CARTE = Object.freeze({ width: 1280, height: 720 });

/** Points de vie maximum par défaut. */
export const VIE_MAXIMALE = 100;

/** Longueur maximale d'un pseudo, en points de code Unicode. */
export const LONGUEUR_PSEUDO_MAXIMALE = 16;

const CARACTERES_PSEUDO_INTERDITS = /[\p{Cc}\p{Cf}]/u;

/**
 * Applique la règle du serveur : un pseudo tient sur 1 à 16 points de code
 * Unicode et exclut les caractères de contrôle et de format.
 */
export function pseudoValide(pseudo) {
	if (typeof pseudo !== "string")
		return false;

	const longueur = [...pseudo].length;
	return longueur >= 1 && longueur <= LONGUEUR_PSEUDO_MAXIMALE && !CARACTERES_PSEUDO_INTERDITS.test(pseudo);
}

/** Codes stables des messages `rejected` (`code`), comparés par les clients. */
export const CODES_REFUS = Object.freeze({
	refusPartie: "game-refused",
	pseudoPris: "pseudo-taken",
	sessionReprise: "session-taken-over",
	adminRemplace: "admin-replaced"
});

/**
 * Indique si un message `rejected` porte le code donné ; la raison textuelle
 * sert de repli pour les émetteurs qui n'envoient pas encore `code`.
 */
export function refusAvecCode(message, code, raison) {
	if (typeof message?.code === "string")
		return message.code === code;

	return message?.reason === raison;
}

/** Formes d'attaque. */
export const ATTAQUE = Object.freeze({ corpsACorps: 1, tir: 2 });

/** Libellés des états de la partie. */
export const LIBELLES_ETAT = Object.freeze({
	lobby: "Salle d'attente",
	running: "En cours",
	paused: "En pause",
	over: "Terminée",
	stopped: "Arrêtée"
});

/** Libellés des statuts des joueurs. */
export const LIBELLES_STATUT = Object.freeze({
	alive: "Vivant",
	eliminated: "Éliminé",
	winner: "Vainqueur"
});

/** Libellés des phases de la manche. */
export const LIBELLES_PHASE = Object.freeze({
	warmup: "Échauffement",
	battle: "Combat",
	over: "Terminé"
});

const ETATS = ["lobby", "running", "paused", "over", "stopped"];

/**
 * Normalise un état de partie ; retourne `null` s'il est inconnu.
 */
export function normaliserEtat(etat) {
	return ETATS.includes(etat) ? etat : null;
}

/**
 * Normalise un statut de joueur, transmis en texte ou sous sa valeur numérique
 * (0, 1, 2) ; retourne `null` s'il est inconnu, pour qu'un statut inattendu ne
 * soit pas confondu avec `alive`.
 */
export function normaliserStatut(statut) {
	switch (statut) {
		case 0: case "0": case "eliminated": case "dead":
			return "eliminated";
		case 1: case "1": case "alive":
			return "alive";
		case 2: case "2": case "winner":
			return "winner";
		default:
			return null;
	}
}

/**
 * Normalise une phase de manche, transmise en texte ou sous sa valeur numérique
 * (0, 1, 2) ; retourne `null` si elle est inconnue, pour qu'une phase inattendue
 * ne soit pas confondue avec `battle`.
 */
export function normaliserPhase(phase) {
	switch (phase) {
		case 0: case "0": case "warmup":
			return "warmup";
		case 1: case "1": case "battle":
			return "battle";
		case 2: case "2": case "over": case "ended":
			return "over";
		default:
			return null;
	}
}

/**
 * Normalise une zone `{x1, y1, x2, y2}` ; retourne `null` si elle est invalide.
 */
export function normaliserZone(zone) {
	if (zone === null || typeof zone !== "object")
		return null;

	const valeurs = ["x1", "y1", "x2", "y2"].map(cle => Number(zone[cle]));
	if (valeurs.some(valeur => !Number.isFinite(valeur)))
		return null;

	const [x1, y1, x2, y2] = valeurs;
	return { x1: Math.min(x1, x2), y1: Math.min(y1, y2), x2: Math.max(x1, x2), y2: Math.max(y1, y2) };
}

/**
 * Convertit en entier, avec une valeur par défaut si la valeur n'est pas un nombre.
 */
export function entier(valeur, parDefaut = 0) {
	const nombre = Number(valeur);
	return Number.isFinite(nombre) ? Math.round(nombre) : parDefaut;
}
