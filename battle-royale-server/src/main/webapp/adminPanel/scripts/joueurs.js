// Tableau des joueurs du panneau d'administration et détection des événements
// (arrivées, déconnexions, éliminations) entre deux listes successives.

import { LIBELLES_STATUT, VIE_MAXIMALE, entier, normaliserStatut } from "../../common/scripts/protocole.js";
import { creerElement, ordinal, poserAvatar } from "../../common/scripts/interface.js";

const SEUIL_ORANGE = 0.5;
const SEUIL_ROUGE = 0.25;

/**
 * Normalise un élément du message `players`.
 */
export function normaliserJoueur(joueur) {
	return {
		id: entier(joueur.id, -1),
		pseudo: String(joueur.pseudo ?? "?"),
		connecte: joueur.connected === true,
		statut: normaliserStatut(joueur.status),
		vie: Math.max(0, entier(joueur.life, VIE_MAXIMALE)),
		kills: Math.max(0, entier(joueur.kills)),
		rang: Math.max(0, entier(joueur.rank))
	};
}

/**
 * Compare deux listes de joueurs et retourne les événements à journaliser.
 *
 * @returns {Array<{texte: string, genre: string}>}
 */
export function evenementsEntre(avant, apres) {
	const precedents = new Map(avant.map(joueur => [joueur.id, joueur]));
	const evenements = [];

	for (const joueur of apres) {
		const precedent = precedents.get(joueur.id);

		if (precedent === undefined) {
			evenements.push({ texte: `${joueur.pseudo} a rejoint la partie.`, genre: "succes" });
			continue;
		}
		if (precedent.connecte && !joueur.connecte)
			evenements.push({ texte: `${joueur.pseudo} s'est déconnecté.`, genre: "erreur" });
		else if (!precedent.connecte && joueur.connecte)
			evenements.push({ texte: `${joueur.pseudo} s'est reconnecté.`, genre: "succes" });
		if (precedent.statut === "alive" && joueur.statut === "eliminated")
			evenements.push({ texte: `${joueur.pseudo} est éliminé${joueur.rang > 0 ? ` (${ordinal(joueur.rang)})` : ""}.`, genre: "erreur" });
	}

	return evenements;
}

function comparateur(etatPartie) {
	if (etatPartie === "running" || etatPartie === "paused") {
		const poids = { winner: 0, alive: 1, eliminated: 2 };
		return (a, b) => (poids[a.statut] ?? 1) - (poids[b.statut] ?? 1)
			|| (a.statut === "eliminated" ? a.rang - b.rang : b.vie - a.vie)
			|| a.id - b.id;
	}

	if (etatPartie === "over" || etatPartie === "stopped")
		return (a, b) => (a.rang || Infinity) - (b.rang || Infinity) || a.id - b.id;

	return (a, b) => a.id - b.id;
}

function niveauVie(proportion) {
	if (proportion > SEUIL_ORANGE)
		return "haut";
	return proportion > SEUIL_ROUGE ? "moyen" : "bas";
}

function creerLigne() {
	const avatar = creerElement("img", { classe: "avatar-mini", alt: "" });
	const remplissage = creerElement("div", { classe: "vie-remplissage" });

	return creerElement("tr", {}, [
		creerElement("td", {}, [creerElement("div", { classe: "cellule-joueur" }, [avatar, creerElement("span")])]),
		creerElement("td", { classe: "colonne-id" }),
		creerElement("td", {}, [creerElement("span", { classe: "pastille" })]),
		creerElement("td", {}, [creerElement("span", { classe: "statut-joueur" })]),
		creerElement("td", {}, [creerElement("div", { classe: "vie" }, [
			creerElement("div", { classe: "vie-barre" }, [remplissage]),
			creerElement("span", { classe: "vie-texte" })
		])]),
		creerElement("td", { classe: "colonne-nombre" }),
		creerElement("td", { classe: "colonne-nombre" })
	]);
}

/**
 * Tableau des joueurs mis à jour sans recréer les lignes existantes.
 */
export class TableauJoueurs {
	#corps;
	#vide;
	#lignes = new Map();

	/**
	 * @param {HTMLTableSectionElement} corps corps du tableau
	 * @param {HTMLElement} vide message affiché quand il n'y a aucun joueur
	 */
	constructor(corps, vide) {
		this.#corps = corps;
		this.#vide = vide;
	}

	/**
	 * Affiche la liste des joueurs, triée selon l'état de la partie.
	 */
	afficher(joueurs, etatPartie, vieMaximale = VIE_MAXIMALE) {
		const enPartie = etatPartie !== "lobby" && etatPartie !== null;
		const tries = [...joueurs].sort(comparateur(etatPartie));
		const presents = new Set();

		for (const joueur of tries) {
			presents.add(joueur.id);

			let ligne = this.#lignes.get(joueur.id);
			if (ligne === undefined) {
				ligne = creerLigne();
				ligne.classList.add("ligne-nouvelle");
				poserAvatar(ligne.querySelector("img"), joueur.id);
				this.#lignes.set(joueur.id, ligne);
			}

			this.#remplir(ligne, joueur, enPartie, vieMaximale);
			this.#corps.append(ligne);
		}

		for (const [id, ligne] of this.#lignes) {
			if (!presents.has(id)) {
				ligne.remove();
				this.#lignes.delete(id);
			}
		}

		this.#vide.hidden = joueurs.length > 0;
	}

	#remplir(ligne, joueur, enPartie, vieMaximale) {
		const cellules = ligne.children;
		const proportion = Math.max(0, Math.min(1, joueur.vie / vieMaximale));

		ligne.classList.toggle("ligne-deconnectee", !joueur.connecte);
		ligne.classList.toggle("ligne-eliminee", enPartie && joueur.statut === "eliminated");

		const pseudo = cellules[0].querySelector("span");
		pseudo.textContent = joueur.pseudo;
		pseudo.title = joueur.pseudo;
		cellules[1].textContent = String(joueur.id);

		const pastille = cellules[2].firstElementChild;
		pastille.dataset.connecte = String(joueur.connecte);
		pastille.textContent = joueur.connecte ? "Connecté" : "Déconnecté";

		const statut = cellules[3].firstElementChild;
		statut.dataset.statut = enPartie ? joueur.statut ?? "" : "";
		statut.textContent = enPartie ? LIBELLES_STATUT[joueur.statut] ?? "Inconnu" : "Inscrit";

		const vie = cellules[4].firstElementChild;
		vie.dataset.niveau = niveauVie(proportion);
		vie.querySelector(".vie-remplissage").style.width = `${proportion * 100}%`;
		vie.querySelector(".vie-texte").textContent = String(joueur.vie);

		cellules[5].textContent = String(joueur.kills);
		cellules[6].textContent = joueur.rang > 0 ? ordinal(joueur.rang) : "–";
	}
}
