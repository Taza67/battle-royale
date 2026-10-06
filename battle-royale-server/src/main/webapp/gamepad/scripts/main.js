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

const CLE_PSEUDO = "battle-royale.pseudo";
const TENTATIVES_REINSCRIPTION = 3;
const DELAI_REINSCRIPTION = 1500;
const INTERVALLE_VIBRATION = 350;

const element = identifiant => document.getElementById(identifiant);

const session = {
	pseudo: lireStockage(),
	automatique: false,
	inscrit: false,
	id: null,
	partie: null,
	fin: null,
	dernierEtat: null,
	echecsAutomatiques: 0,
	derniereVibration: 0
};
session.automatique = session.pseudo !== null;

const connexion = await creerConnexion({ robots: 5, robotsJeu: 2, demarrageAuto: 6000, nouvelleMancheAuto: 20000 });
const minicarte = new Minicarte(element("minicarte"));
const commandes = new Commandes({
	zoneJoystick: element("joystick"),
	boutons: [element("bouton-a"), element("bouton-b")],
	envoyer: message => connexion.envoyer(message),
	surAttaque: () => vibrer(12)
});

// Stockage ///////////////////////////////////////////////////////////////////

function lireStockage() {
	try {
		return localStorage.getItem(CLE_PSEUDO);
	} catch (erreur) {
		return null;
	}
}

function ecrireStockage(pseudo) {
	try {
		if (pseudo === null)
			localStorage.removeItem(CLE_PSEUDO);
		else
			localStorage.setItem(CLE_PSEUDO, pseudo);
	} catch (erreur) {
		// Stockage indisponible (navigation privée) : la reconnexion automatique est simplement perdue.
	}
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
	if (session.pseudo !== null)
		connexion.envoyer({ type: "join", pseudo: session.pseudo });
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

	session.pseudo = pseudo;
	session.automatique = false;
	session.echecsAutomatiques = 0;
	envoyerInscription();
	actualiser();
}

function revenirInscription(raison) {
	const pseudo = session.pseudo;

	session.pseudo = null;
	session.automatique = false;
	session.inscrit = false;
	session.id = null;
	session.partie = null;
	session.fin = null;
	session.dernierEtat = null;
	ecrireStockage(null);

	element("pseudo").value = pseudo ?? "";
	element("erreur-inscription").textContent = raison;
	element("pseudo").setAttribute("aria-invalid", "true");
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
	if (!connexion.estConnectee)
		session.inscrit = false;
	actualiser();
});

connexion.sur("welcome", message => {
	session.inscrit = true;
	session.automatique = true;
	session.echecsAutomatiques = 0;
	session.id = entier(message.id, null);
	session.pseudo = typeof message.pseudo === "string" ? message.pseudo : session.pseudo;
	ecrireStockage(session.pseudo);

	element("erreur-inscription").textContent = "";
	afficherProfil(session.pseudo, session.id);
	changerPartie(normaliserEtat(message.state) ?? "lobby");
});

connexion.sur("rejected", message => {
	const raison = typeof message.reason === "string" && message.reason !== "" ? message.reason : "Inscription refusée.";

	if (session.automatique && session.echecsAutomatiques < TENTATIVES_REINSCRIPTION) {
		session.echecsAutomatiques++;
		setTimeout(envoyerInscription, DELAI_REINSCRIPTION);
		return;
	}

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
