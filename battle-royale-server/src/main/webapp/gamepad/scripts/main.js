// Manette : inscription, salle d'attente, commandes en partie et écran de fin,
// pilotés par les messages du serveur (docs/PROTOCOLE.md).

import { creerConnexion, estModeDemo } from "../../common/scripts/connexion.js";
import { afficherEcran, ecranCourant, effacerNotifications, lierIndicateurConnexion, notifier } from "../../common/scripts/interface.js";
import {
	CARTE, VIE_MAXIMALE, entier, normaliserEtat, normaliserPhase, normaliserStatut, normaliserZone
} from "../../common/scripts/protocole.js";
import { afficherEtat, afficherFin, afficherProfil, reinitialiserEtat } from "./affichage.js";
import { Commandes } from "./commandes.js";
import { validerPseudo } from "./inscription.js";
import { Minicarte } from "./minicarte.js";
import { initialiserPleinEcran } from "./pleinEcran.js";

const CLE_JOUEUR = "battle-royale.joueur";
const ANCIENNE_CLE_PSEUDO = "battle-royale.pseudo";
const RAISON_PSEUDO_PRIS = "Pseudo déjà utilisé";
const RAISON_SESSION_REPRISE = "Session reprise par une autre connexion";
const DUREE_ESSAIS_PSEUDO = 30000;
const ATTENTE_ESSAI_INITIALE = 1000;
const ATTENTE_ESSAI_MAXIMALE = 8000;
const INTERVALLE_VIBRATION = 350;
const SILENCE_MAXIMAL = 5000;

const element = identifiant => document.getElementById(identifiant);

const memorise = lireStockage();
const session = {
	pseudo: memorise?.pseudo ?? null,
	jeton: memorise?.jeton ?? null,
	automatique: memorise !== null,
	inscrit: false,
	id: null,
	partie: null,
	fin: null,
	dernierEtat: null,
	derniereVibration: 0
};

// Nouveaux essais d'inscription quand le pseudo est encore attaché à l'ancienne
// connexion, que le serveur n'a pas encore vue se fermer.
const essais = { debut: null, nombre: 0, minuteur: null };

const connexion = await creerConnexion({ robots: 5, robotsJeu: 2, demarrageAuto: 6000, nouvelleMancheAuto: 20000 });
const minicarte = new Minicarte(element("minicarte"));
const commandes = new Commandes({
	zoneJoystick: element("joystick"),
	boutons: [element("bouton-a"), element("bouton-b")],
	envoyer: message => connexion.envoyer(message),
	surAttaque: () => vibrer(12)
});

// Stockage ///////////////////////////////////////////////////////////////////

// Le pseudo et son jeton de reprise sont mémorisés ensemble : `{pseudo, jeton}`.
function lireStockage() {
	try {
		const donnees = JSON.parse(localStorage.getItem(CLE_JOUEUR));
		if (typeof donnees?.pseudo === "string" && donnees.pseudo !== "")
			return { pseudo: donnees.pseudo, jeton: typeof donnees.jeton === "string" && donnees.jeton !== "" ? donnees.jeton : null };

		const ancien = localStorage.getItem(ANCIENNE_CLE_PSEUDO);
		return ancien === null || ancien === "" ? null : { pseudo: ancien, jeton: null };
	} catch (erreur) {
		return null;
	}
}

function ecrireStockage(pseudo, jeton = null) {
	try {
		localStorage.removeItem(ANCIENNE_CLE_PSEUDO);
		if (pseudo === null)
			localStorage.removeItem(CLE_JOUEUR);
		else
			localStorage.setItem(CLE_JOUEUR, JSON.stringify({ pseudo, jeton }));
	} catch (erreur) {
		// Stockage indisponible (navigation privée) : la reconnexion automatique est simplement perdue.
	}
}

function memePseudo(a, b) {
	return typeof a === "string" && typeof b === "string" && a.toLowerCase() === b.toLowerCase();
}

// Retours sensoriels /////////////////////////////////////////////////////////

function vibrer(motif) {
	if (typeof navigator.vibrate === "function")
		navigator.vibrate(motif);
}

function signalerDegats(degats) {
	const maintenant = performance.now();
	if (maintenant - session.derniereVibration < INTERVALLE_VIBRATION)
		return;

	session.derniereVibration = maintenant;
	vibrer(Math.min(150, 40 + degats * 4));

	const flash = element("flash-degats");
	flash.classList.remove("flash-degats-actif");
	void flash.offsetWidth;
	flash.classList.add("flash-degats-actif");
}

// Inscription ////////////////////////////////////////////////////////////////

function envoyerInscription() {
	if (session.pseudo === null)
		return;

	const message = { type: "join", pseudo: session.pseudo };
	if (session.jeton !== null)
		message.token = session.jeton;
	connexion.envoyer(message);
}

function annulerEssais() {
	clearTimeout(essais.minuteur);
	essais.minuteur = null;
}

function reinitialiserEssais() {
	annulerEssais();
	essais.debut = null;
	essais.nombre = 0;
}

// Planifie un nouvel essai d'inscription avec une attente croissante ; retourne
// `false` une fois le délai total écoulé.
function planifierEssai() {
	const maintenant = performance.now();
	essais.debut ??= maintenant;

	const restant = DUREE_ESSAIS_PSEUDO - (maintenant - essais.debut);
	if (restant <= 0)
		return false;

	const attente = Math.min(ATTENTE_ESSAI_MAXIMALE, ATTENTE_ESSAI_INITIALE * 2 ** essais.nombre, restant);
	essais.nombre++;
	annulerEssais();
	essais.minuteur = setTimeout(() => {
		essais.minuteur = null;
		envoyerInscription();
	}, attente);
	return true;
}

function inscrire(evenement) {
	evenement.preventDefault();

	const champ = element("pseudo");
	const { pseudo, erreur } = validerPseudo(champ.value);

	champ.value = pseudo;
	champ.setAttribute("aria-invalid", String(erreur !== null));
	element("erreur-inscription").textContent = erreur ?? "";

	if (erreur !== null) {
		champ.focus();
		return;
	}

	// Le jeton mémorisé (éventuellement par un autre onglet) n'accompagne que son pseudo.
	const stocke = lireStockage();
	session.pseudo = pseudo;
	session.jeton = stocke !== null && memePseudo(stocke.pseudo, pseudo) ? stocke.jeton : null;
	session.automatique = false;
	reinitialiserEssais();
	envoyerInscription();
	actualiser();
}

/**
 * Abandonne l'inscription et revient au formulaire avec la raison.
 *
 * @param {string} raison message affiché sous le formulaire
 * @param {boolean} conserverStockage garde le pseudo et le jeton mémorisés, utilisés par la connexion qui a repris la session
 */
function revenirInscription(raison, conserverStockage = false) {
	const pseudo = session.pseudo;

	reinitialiserEssais();
	session.pseudo = null;
	session.jeton = null;
	session.automatique = false;
	session.inscrit = false;
	session.id = null;
	session.partie = null;
	session.fin = null;
	session.dernierEtat = null;
	if (!conserverStockage)
		ecrireStockage(null);

	element("pseudo").value = pseudo ?? "";
	element("erreur-inscription").textContent = raison;
	element("pseudo").setAttribute("aria-invalid", String(!conserverStockage));
	actualiser();
	element("pseudo").focus();
}

// Messages du serveur ////////////////////////////////////////////////////////

connexion.surOuverture(() => {
	session.inscrit = false;
	if (session.pseudo !== null)
		envoyerInscription();
	actualiser();
});

connexion.surEtat(() => {
	if (!connexion.estConnectee) {
		session.inscrit = false;
		// La réouverture de la socket renverra l'inscription.
		annulerEssais();
	}
	actualiser();
});

connexion.sur("welcome", message => {
	session.inscrit = true;
	session.automatique = true;
	reinitialiserEssais();
	session.id = entier(message.id, null);
	session.pseudo = typeof message.pseudo === "string" ? message.pseudo : session.pseudo;
	session.jeton = typeof message.token === "string" && message.token !== "" ? message.token : null;
	ecrireStockage(session.pseudo, session.jeton);

	element("erreur-inscription").textContent = "";
	afficherProfil(session.pseudo, session.id);
	changerPartie(normaliserEtat(message.state) ?? "lobby");
});

connexion.sur("rejected", message => {
	const raison = typeof message.reason === "string" && message.reason !== "" ? message.reason : "Inscription refusée.";

	// Un autre onglet ou appareil a repris la session : pas de nouvel essai, qui
	// la lui reprendrait à son tour.
	if (raison === RAISON_SESSION_REPRISE) {
		revenirInscription(raison, true);
		return;
	}

	// Une session déjà inscrite qui reçoit un autre refus garde son inscription.
	if (session.inscrit)
		return;

	if (session.automatique && raison === RAISON_PSEUDO_PRIS && planifierEssai())
		return;

	revenirInscription(raison);
});

connexion.sur("game", message => {
	const etat = normaliserEtat(message.state);
	if (etat !== null && session.inscrit)
		changerPartie(etat);
});

connexion.sur("state", message => {
	if (!session.inscrit)
		return;

	const precedent = session.dernierEtat;
	const vieMaximale = Math.max(1, entier(message.maxLife, VIE_MAXIMALE));
	const etat = {
		statut: normaliserStatut(message.status),
		vie: Math.max(0, Math.min(vieMaximale, entier(message.life, vieMaximale))),
		vieMaximale,
		x: entier(message.x),
		y: entier(message.y),
		kills: Math.max(0, entier(message.kills)),
		rang: Math.max(0, entier(message.rank)),
		vivants: Math.max(0, entier(message.alive)),
		total: Math.max(0, entier(message.total)),
		phase: normaliserPhase(message.phase),
		secondes: Math.max(0, entier(message.secondsLeft)),
		zone: normaliserZone(message.zone),
		prochaineZone: normaliserZone(message.nextZone),
		carte: message.map && entier(message.map.width) > 0 && entier(message.map.height) > 0
			? { width: entier(message.map.width), height: entier(message.map.height) }
			: CARTE
	};
	session.dernierEtat = etat;

	if (precedent !== null) {
		if (etat.vie < precedent.vie && etat.statut !== "winner")
			signalerDegats(precedent.vie - etat.vie);
		if (precedent.statut === "alive" && etat.statut === "eliminated") {
			vibrer([200, 100, 300]);
			notifier("Vous avez été éliminé.", "erreur");
		}
		if (precedent.statut !== "winner" && etat.statut === "winner")
			vibrer([100, 60, 100, 60, 300]);
	}

	afficherEtat(etat);
	minicarte.mettreAJour(etat);
	actualiserCommandes();
});

connexion.sur("end", message => {
	if (!session.inscrit)
		return;

	session.fin = message;
	if (session.partie === "running" || session.partie === "paused")
		session.partie = "over";
	actualiser();
});

// État de la partie //////////////////////////////////////////////////////////

function changerPartie(etat) {
	const precedent = session.partie;
	const nouvelleManche = etat === "running" && precedent !== "running" && precedent !== "paused";

	if (nouvelleManche || etat === "lobby") {
		session.fin = null;
		session.dernierEtat = null;
		minicarte.reinitialiser();
		reinitialiserEtat(VIE_MAXIMALE);
	}

	session.partie = etat;
	actualiser();

	// La pause est signalée par sa superposition, la reprise par une notification.
	if (precedent === "paused" && etat === "running")
		notifier("Reprise de la partie !", "succes");
	else if (nouvelleManche && precedent !== null)
		notifier("La partie commence !", "succes");
}

// Affichage //////////////////////////////////////////////////////////////////

function afficherAttente(titre, texte, profil) {
	element("titre-attente").textContent = titre;
	element("texte-attente").textContent = texte;
	element("profil-attente").hidden = !profil;
	element("aide-commandes").hidden = !profil;
	afficherEcran("ecran-attente");
}

function actualiser() {
	const connectee = connexion.estConnectee;

	if (session.id === null) {
		if (session.pseudo === null)
			afficherEcran("ecran-inscription");
		else if (!connectee)
			afficherAttente(session.automatique ? "Reconnexion" : "Connexion", "Connexion au serveur en cours.", false);
		else
			afficherAttente(session.automatique ? "Reconnexion" : "Inscription", `Inscription de ${session.pseudo}.`, false);
	} else if (session.partie === "lobby" || session.partie === null) {
		if (connectee && session.inscrit)
			afficherAttente("Salle d'attente", "La partie commencera quand l'administrateur la lancera.", true);
		else
			afficherAttente("Reconnexion", "Connexion au serveur perdue, nouvelle tentative en cours.", true);
	} else if (session.partie === "running" || session.partie === "paused") {
		afficherEcran("ecran-manette");
	} else {
		if (ecranCourant() !== "ecran-fin")
			effacerNotifications();
		afficherFin({ etat: session.partie, fin: session.fin, id: session.id, dernierEtat: session.dernierEtat });
		afficherEcran("ecran-fin");
	}

	const surManette = ecranCourant() === "ecran-manette";
	element("superposition-connexion").hidden = !surManette || (connectee && session.inscrit);
	element("superposition-pause").hidden = !surManette || session.partie !== "paused" || !element("superposition-connexion").hidden;

	if (surManette)
		minicarte.demarrer();
	else
		minicarte.arreter();

	// En partie, le serveur envoie l'état du joueur inscrit en continu : un
	// silence prolongé révèle une connexion morte.
	const enPartie = session.partie === "running" || session.partie === "paused";
	connexion.surveiller(session.inscrit && enPartie ? SILENCE_MAXIMAL : 0);
	actualiserCommandes();
}

function actualiserCommandes() {
	const statut = session.dernierEtat?.statut ?? "alive";
	commandes.activer(
		ecranCourant() === "ecran-manette" && connexion.estConnectee && session.inscrit
		&& session.partie === "running" && statut === "alive"
	);
}

// Démarrage //////////////////////////////////////////////////////////////////

element("formulaire-inscription").addEventListener("submit", inscrire);
element("pseudo").addEventListener("input", () => {
	element("pseudo").removeAttribute("aria-invalid");
	element("erreur-inscription").textContent = "";
});

initialiserPleinEcran({
	invitation: element("invitation-plein-ecran"),
	boutonEntrer: element("bouton-plein-ecran"),
	boutonIgnorer: element("bouton-sans-plein-ecran"),
	boutonsDiscrets: [element("bouton-plein-ecran-flottant"), element("bouton-plein-ecran-manette")]
});

lierIndicateurConnexion(element("connexion"), connexion, estModeDemo());
lierIndicateurConnexion(element("connexion-manette"), connexion, estModeDemo());
reinitialiserEtat(VIE_MAXIMALE);
actualiser();
connexion.ouvrir();
