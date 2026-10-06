// Demande de plein écran et de verrouillage en paysage, sans bloquer l'usage
// sur ordinateur ni sur les navigateurs qui ne les permettent pas.

const CLE_IGNORE = "battle-royale.sans-plein-ecran";

/**
 * Indique si la page est affichée en plein écran.
 */
export function estPleinEcran() {
	return Boolean(document.fullscreenElement || document.webkitFullscreenElement);
}

/**
 * Indique si le navigateur permet de passer en plein écran.
 */
export function pleinEcranDisponible() {
	const racine = document.documentElement;
	return Boolean((racine.requestFullscreen || racine.webkitRequestFullscreen) && document.fullscreenEnabled !== false);
}

/**
 * Indique si l'appareil est principalement tactile (téléphone, tablette).
 */
export function estAppareilTactile() {
	return window.matchMedia("(pointer: coarse)").matches;
}

/**
 * Passe en plein écran puis tente de verrouiller l'orientation en paysage.
 */
export async function passerPleinEcran() {
	const racine = document.documentElement;

	try {
		if (racine.requestFullscreen)
			await racine.requestFullscreen({ navigationUI: "hide" });
		else
			racine.webkitRequestFullscreen?.();
	} catch (erreur) {
		return false;
	}

	try {
		await window.screen.orientation?.lock?.("landscape");
	} catch (erreur) {
		// Verrouillage refusé (ordinateur, navigateur sans support) : sans conséquence.
	}
	return true;
}

/**
 * Met en place la demande de plein écran : une invitation au chargement sur les
 * appareils tactiles, et un bouton discret tant que la page n'est pas en plein écran.
 *
 * @param {object} elements
 * @param {HTMLElement} elements.invitation superposition d'invitation
 * @param {HTMLButtonElement} elements.boutonEntrer bouton de passage en plein écran de l'invitation
 * @param {HTMLButtonElement} elements.boutonIgnorer bouton « continuer sans plein écran »
 * @param {HTMLButtonElement[]} elements.boutonsDiscrets boutons discrets de passage en plein écran
 */
export function initialiserPleinEcran({ invitation, boutonEntrer, boutonIgnorer, boutonsDiscrets }) {
	const disponible = pleinEcranDisponible();

	const actualiser = () => {
		for (const bouton of boutonsDiscrets)
			bouton.hidden = !disponible || estPleinEcran();
		if (estPleinEcran())
			invitation.hidden = true;
	};

	boutonEntrer.addEventListener("click", async () => {
		invitation.hidden = true;
		await passerPleinEcran();
		actualiser();
	});

	boutonIgnorer.addEventListener("click", () => {
		invitation.hidden = true;
		sessionStorage.setItem(CLE_IGNORE, "1");
	});

	for (const bouton of boutonsDiscrets)
		bouton.addEventListener("click", () => passerPleinEcran().then(actualiser));
	document.addEventListener("fullscreenchange", actualiser);
	document.addEventListener("webkitfullscreenchange", actualiser);

	invitation.hidden = !(disponible && estAppareilTactile() && !estPleinEcran() && sessionStorage.getItem(CLE_IGNORE) === null);
	actualiser();

	if (!invitation.hidden)
		boutonEntrer.focus();
}
