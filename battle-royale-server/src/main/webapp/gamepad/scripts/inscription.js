// Validation du pseudo saisi à l'inscription.

import { LONGUEUR_PSEUDO_MAXIMALE } from "../../common/scripts/protocole.js";

const CARACTERES_AUTORISES = /^[\p{L}\p{N} _.'-]+$/u;

/**
 * Valide un pseudo et retourne `{pseudo, erreur}` : le pseudo nettoyé, et un
 * message d'erreur ou `null` s'il est valide.
 */
export function validerPseudo(saisie) {
	const pseudo = String(saisie ?? "").trim().replace(/\s+/g, " ");

	if (pseudo.length === 0)
		return { pseudo, erreur: "Entrez un pseudo." };
	if (pseudo.length > LONGUEUR_PSEUDO_MAXIMALE)
		return { pseudo, erreur: `Le pseudo ne doit pas dépasser ${LONGUEUR_PSEUDO_MAXIMALE} caractères.` };
	if (!CARACTERES_AUTORISES.test(pseudo))
		return { pseudo, erreur: "Utilisez uniquement des lettres, des chiffres, des espaces et - _ . '" };

	return { pseudo, erreur: null };
}
