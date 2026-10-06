// Affichage des informations du joueur sur la manette et de l'écran de fin.

import { LIBELLES_PHASE, LIBELLES_STATUT } from "../../common/scripts/protocole.js";
import { niveauVie, ordinal, poserAvatar, remplirClassement, resumerFin } from "../../common/scripts/interface.js";

const element = identifiant => document.getElementById(identifiant);

/**
 * Affiche le pseudo et l'avatar du joueur sur tous les écrans.
 */
export function afficherProfil(pseudo, id) {
	for (const cible of document.querySelectorAll("[data-pseudo]"))
		cible.textContent = pseudo;
	for (const image of document.querySelectorAll("img[data-avatar]"))
		poserAvatar(image, id);
}

/**
 * Met à jour les informations de la manette à partir d'un état normalisé.
 */
export function afficherEtat(etat) {
	const proportion = etat.vieMaximale > 0 ? Math.max(0, Math.min(1, etat.vie / etat.vieMaximale)) : 0;
	const barre = element("barre-vie");

	barre.dataset.niveau = niveauVie(proportion);
	barre.setAttribute("aria-valuemax", String(etat.vieMaximale));
	barre.setAttribute("aria-valuenow", String(etat.vie));
	element("barre-vie-remplissage").style.width = `${proportion * 100}%`;
	element("texte-vie").textContent = `${etat.vie} / ${etat.vieMaximale}`;

	const statut = element("info-statut");
	statut.dataset.statut = etat.statut ?? "";
	statut.textContent = (LIBELLES_STATUT[etat.statut] ?? "Inconnu").toUpperCase();

	element("info-kills").textContent = String(etat.kills);
	element("info-vivants").textContent = `${etat.vivants} / ${etat.total}`;
	element("info-rang").textContent = etat.rang > 0 ? ordinal(etat.rang) : "–";

	element("info-phase").textContent = (LIBELLES_PHASE[etat.phase] ?? "?").toUpperCase();
	element("info-phase").dataset.phase = etat.phase ?? "";
	element("info-compte").textContent = etat.phase === "over" ? "–" : `${etat.secondes} s`;
	element("info-compte").title = etat.phase === "warmup" ? "Secondes avant le début du combat" : "Secondes avant la prochaine étape de la zone";

	const dansLaLave = etat.statut === "alive" && etat.zone !== null && !(etat.x >= etat.zone.x1 && etat.x <= etat.zone.x2 && etat.y >= etat.zone.y1 && etat.y <= etat.zone.y2);
	element("cadre-carte").classList.toggle("cadre-carte-lave", dansLaLave);
	element("alerte-lave").hidden = !dansLaLave;

	const manette = element("manette");
	manette.dataset.statut = etat.statut;
	element("message-carte").hidden = etat.statut !== "eliminated" && etat.statut !== "winner";
	element("message-carte").textContent = etat.statut === "winner" ? "VAINQUEUR !" : "ÉLIMINÉ";
}

/**
 * Remet la manette dans son état de début de manche.
 */
export function reinitialiserEtat(vieMaximale) {
	afficherEtat({
		statut: "alive", vie: vieMaximale, vieMaximale, kills: 0, rang: 0,
		vivants: 0, total: 0, phase: "warmup", secondes: 0, x: 0, y: 0, zone: null
	});
}

/**
 * Affiche l'écran de fin de partie.
 *
 * @param {object} donnees
 * @param {string} donnees.etat `over` ou `stopped`
 * @param {object|null} donnees.fin message `end` reçu, ou `null`
 * @param {number|null} donnees.id identifiant du joueur
 * @param {object|null} donnees.dernierEtat dernier état normalisé du joueur
 */
export function afficherFin({ etat, fin, id, dernierEtat }) {
	const vainqueur = fin?.winner ?? null;
	const classement = Array.isArray(fin?.ranking) ? fin.ranking : [];
	const moi = classement.find(joueur => joueur.id === id);
	const rang = moi?.rank || dernierEtat?.rang || 0;
	const kills = moi?.kills ?? dernierEtat?.kills ?? 0;
	const gagnant = vainqueur !== null && vainqueur.id === id;
	const resume = resumerFin(fin, { total: dernierEtat?.total, arretee: etat === "stopped" });

	element("titre-fin").textContent = gagnant ? "Victoire !" : resume.arretee ? "Partie arrêtée" : "Partie terminée";
	element("ecran-fin").dataset.victoire = String(gagnant);
	element("fin-message").hidden = resume.message === null;
	element("fin-message").textContent = resume.message ?? "";

	element("bloc-vainqueur").hidden = vainqueur === null;
	if (vainqueur !== null) {
		element("pseudo-vainqueur").textContent = vainqueur.pseudo;
		poserAvatar(element("avatar-vainqueur"), vainqueur.id);
	}

	element("fin-rang").textContent = rang > 0 ? ordinal(rang) : "–";
	element("fin-total").textContent = resume.total > 0 ? `sur ${resume.total}` : "";
	element("fin-kills").textContent = String(kills);

	remplirClassement(element("corps-classement"), classement, id);
	element("tableau-fin").hidden = classement.length === 0;
	element("classement-vide").hidden = classement.length > 0;
}
