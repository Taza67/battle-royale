// Éléments d'interface partagés : écrans, notifications, indicateur de connexion,
// avatars et tableau de classement.

const LIBELLES_CONNEXION = {
	connexion: "Connexion…",
	connectee: "En ligne",
	reconnexion: "Reconnexion…",
	deconnectee: "Hors ligne"
};

const MESSAGE_ARRET = "Manche arrêtée par l'administrateur";
const MESSAGE_VICTOIRE_ROBOT = "Victoire d'un robot";

const DUREE_NOTIFICATION = 3500;
const NOMBRE_AVATARS = 50;

const SEUIL_ORANGE = 0.5;
const SEUIL_ROUGE = 0.25;

/**
 * Retourne le niveau d'une barre de vie selon la proportion restante
 * (`haut`, `moyen` ou `bas`).
 */
export function niveauVie(proportion) {
	if (proportion > SEUIL_ORANGE)
		return "haut";
	return proportion > SEUIL_ROUGE ? "moyen" : "bas";
}

/**
 * Crée un élément HTML.
 *
 * @param {string} balise nom de la balise
 * @param {object} [proprietes] `classe`, `texte` et attributs à poser
 * @param {Array<Node|string>} [enfants] nœuds ajoutés à l'élément
 */
export function creerElement(balise, proprietes = {}, enfants = []) {
	const element = document.createElement(balise);
	const { classe, texte, ...attributs } = proprietes;

	if (classe)
		element.className = classe;
	if (texte !== undefined)
		element.textContent = texte;
	for (const [nom, valeur] of Object.entries(attributs))
		element.setAttribute(nom, valeur);

	element.append(...enfants);
	return element;
}

/**
 * Affiche l'écran demandé et masque les autres éléments de classe `ecran`.
 * L'identifiant est reporté sur `body[data-ecran]` pour les styles.
 */
export function afficherEcran(identifiant) {
	for (const ecran of document.querySelectorAll(".ecran"))
		ecran.hidden = ecran.id !== identifiant;
	document.body.dataset.ecran = identifiant;
}

/**
 * Retourne l'identifiant de l'écran affiché.
 */
export function ecranCourant() {
	return document.querySelector(".ecran:not([hidden])")?.id ?? null;
}

/**
 * Affiche une notification éphémère.
 *
 * @param {string} message texte de la notification
 * @param {"info"|"succes"|"erreur"} [genre] style de la notification
 */
export function notifier(message, genre = "info") {
	let conteneur = document.getElementById("notifications");
	if (conteneur === null) {
		conteneur = creerElement("div", { id: "notifications", classe: "notifications", "aria-live": "polite" });
		document.body.append(conteneur);
	}

	const notification = creerElement("div", { classe: `notification notification-${genre}`, role: genre === "erreur" ? "alert" : "status", texte: message });
	conteneur.append(notification);

	const retirer = () => {
		notification.classList.add("notification-sortie");
		setTimeout(() => notification.remove(), 300);
	};
	notification.addEventListener("click", retirer);
	setTimeout(retirer, DUREE_NOTIFICATION);

	while (conteneur.children.length > 4)
		conteneur.firstElementChild.remove();
}

/**
 * Retire immédiatement toutes les notifications affichées.
 */
export function effacerNotifications() {
	document.getElementById("notifications")?.replaceChildren();
}

/**
 * Relie un indicateur à l'état d'une connexion.
 */
export function lierIndicateurConnexion(element, connexion, demo = false) {
	const texte = element.querySelector(".connexion-texte") ?? element;

	connexion.surEtat((etat, tentatives) => {
		element.dataset.etat = etat;
		texte.textContent = etat === "reconnexion" && tentatives > 1
			? `${LIBELLES_CONNEXION.reconnexion} (${tentatives})`
			: LIBELLES_CONNEXION[etat];
		element.title = demo ? "Mode démonstration : serveur simulé dans la page" : "État de la connexion au serveur";
	});

	if (demo)
		element.classList.add("connexion-demo");
}

/**
 * Pose l'avatar d'un joueur (`common/images/<id>.png`) sur une image, avec le
 * logo en secours. Les chemins sont relatifs aux pages `gamepad/` et
 * `adminPanel/` qui utilisent cette fonction.
 */
export function poserAvatar(image, id) {
	const numero = Number.isInteger(id) && id >= 0 ? id % NOMBRE_AVATARS : null;

	image.onerror = () => {
		image.onerror = null;
		image.src = "../common/images/logo.png";
	};
	image.src = numero === null ? "../common/images/logo.png" : `../common/images/${numero}.png`;
}

/**
 * Retourne le rang en toutes lettres abrégées : « 1er », « 2e »…
 */
export function ordinal(rang) {
	return rang === 1 ? "1er" : `${rang}e`;
}

/**
 * Remplit le corps d'un tableau de classement (rang, avatar, pseudo, éliminations).
 *
 * @param {HTMLTableSectionElement} corps corps du tableau
 * @param {Array<{id: number, pseudo: string, kills: number, rank: number}>} classement
 * @param {number|null} [idMis] identifiant du joueur à mettre en évidence
 */
export function remplirClassement(corps, classement, idMis = null) {
	const lignes = [...classement]
		.sort((a, b) => (a.rank || Infinity) - (b.rank || Infinity) || b.kills - a.kills)
		.map(joueur => {
			const avatar = creerElement("img", { classe: "avatar-mini", alt: "" });
			poserAvatar(avatar, joueur.id);

			const ligne = creerElement("tr", { classe: joueur.rank === 1 ? "classement-premier" : "" }, [
				creerElement("td", { classe: "classement-rang", texte: joueur.rank > 0 ? ordinal(joueur.rank) : "–" }),
				creerElement("td", { classe: "classement-joueur" }, [avatar, creerElement("span", { texte: joueur.pseudo })]),
				creerElement("td", { classe: "classement-kills", texte: String(joueur.kills ?? 0) })
			]);

			if (joueur.id === idMis)
				ligne.classList.add("classement-moi");
			return ligne;
		});

	corps.replaceChildren(...lignes);
}

/**
 * Résume la fin d'une manche pour l'affichage : nombre de participants, arrêt
 * par l'administrateur, victoire d'un robot et message correspondant.
 *
 * `ranking` ne contenant que les humains, un `winner` nul sans humain classé
 * premier désigne la victoire d'un robot ; avec un humain premier, une égalité.
 *
 * @param {object|null} fin message `end` reçu, ou `null`
 * @param {object} [secours] valeurs connues par ailleurs, quand `end` ne les porte pas
 * @param {number} [secours.total] nombre de participants (`state.total`)
 * @param {boolean} [secours.arretee] partie dans l'état `stopped`
 * @returns {{total: number, arretee: boolean, victoireRobot: boolean, message: string|null}}
 */
export function resumerFin(fin, { total = 0, arretee = false } = {}) {
	const classement = Array.isArray(fin?.ranking) ? fin.ranking : [];
	const rangMaximal = classement.reduce((maximum, joueur) => Math.max(maximum, Number.isInteger(joueur?.rank) ? joueur.rank : 0), 0);
	const totalFin = Number.isInteger(fin?.total) && fin.total > 0 ? fin.total : 0;
	const totalSecours = Number.isInteger(total) && total > 0 ? total : 0;

	const estArretee = typeof fin?.stopped === "boolean" ? fin.stopped : arretee;
	const victoireRobot = fin != null && !estArretee && (fin.winner ?? null) === null
		&& !classement.some(joueur => joueur?.rank === 1);

	return {
		total: totalFin || totalSecours || rangMaximal,
		arretee: estArretee,
		victoireRobot,
		message: estArretee ? MESSAGE_ARRET : victoireRobot ? MESSAGE_VICTOIRE_ROBOT : null
	};
}
