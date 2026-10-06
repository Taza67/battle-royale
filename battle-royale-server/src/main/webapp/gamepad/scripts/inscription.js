// Validation du pseudo saisi à l'inscription : mêmes règles que le serveur
// (1 à 16 points de code Unicode, hors caractères de contrôle et de format).

import { LONGUEUR_PSEUDO_MAXIMALE, pseudoValide } from "../../common/scripts/protocole.js";

/**
 * Valide un pseudo et retourne `{pseudo, erreur}` : le pseudo nettoyé, et un
 * message d'erreur ou `null` s'il est valide.
 */
export function validerPseudo(saisie) {
	const pseudo = String(saisie ?? "").trim();

	if (pseudo.length === 0)
		return { pseudo, erreur: "Entrez un pseudo." };
	if ([...pseudo].length > LONGUEUR_PSEUDO_MAXIMALE)
		return { pseudo, erreur: `Le pseudo ne doit pas dépasser ${LONGUEUR_PSEUDO_MAXIMALE} caractères.` };
	if (!pseudoValide(pseudo))
		return { pseudo, erreur: "Le pseudo contient des caractères invisibles interdits." };

	return { pseudo, erreur: null };
}
