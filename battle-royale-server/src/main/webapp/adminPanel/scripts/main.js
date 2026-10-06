// Panneau d'administration : connexion, suivi des joueurs en direct et
// commandes de la partie (docs/PROTOCOLE.md).

import { creerConnexion, estModeDemo, urlManette } from "../../common/scripts/connexion.js";
import {
	afficherEcran, creerElement, lierIndicateurConnexion, notifier, poserAvatar, remplirClassement, resumerFin
} from "../../common/scripts/interface.js";
import { CODES_REFUS, LIBELLES_ETAT, entier, normaliserEtat, refusAvecCode } from "../../common/scripts/protocole.js";
import { TableauJoueurs, evenementsEntre, normaliserJoueur } from "./joueurs.js";

const CLE_SESSION = "battle-royale.admin";
const RAISON_REMPLACEE = "Session administrateur reprise par une autre connexion";
const DELAI_ACQUITTEMENT = 8000;
const TAILLE_JOURNAL = 60;

const COMMANDES_PERMISES = {
	lobby: ["start"],
	running: ["pause", "stop"],
	paused: ["resume", "stop"],
	over: ["start"],
	stopped: ["start"]
};

const LIBELLES_COMMANDE = {
	start: "Lancement de la partie",
	pause: "Mise en pause",
	resume: "Reprise de la partie",
	stop: "Arrêt de la partie"
};

const SUCCES_COMMANDE = {
	start: "Partie lancée !",
	pause: "Partie mise en pause.",
	resume: "Partie reprise.",
	stop: "Partie arrêtée."
};

const AIDES_ETAT = {
	lobby: "Les joueurs s'inscrivent depuis la manette. Lancez la partie quand tout le monde est prêt.",
	running: "La partie est en cours : vous pouvez la mettre en pause ou l'arrêter.",
	paused: "La partie est en pause : les manettes affichent un écran d'attente.",
	over: "La manche est terminée. Vous pouvez lancer une nouvelle manche.",
	stopped: "La manche a été arrêtée. Vous pouvez lancer une nouvelle manche."
};

const element = identifiant => document.getElementById(identifiant);

const session = {
	souhaitee: false,
	motDePasse: "",
	admise: false,
	automatique: false,
	partie: null,
	joueurs: [],
	listeRecue: false,
	finRecue: false,
	commande: null
};

const connexion = await creerConnexion({ robots: 7, robotsJeu: 2, arriveeRobots: 1600 });
const tableau = new TableauJoueurs(element("corps-joueurs"), element("aucun-joueur"));
const boutons = [...document.querySelectorAll("[data-commande]")];

// Stockage ///////////////////////////////////////////////////////////////////

function lireSession() {
	try {
		const valeur = sessionStorage.getItem(CLE_SESSION);
		return valeur === null ? null : JSON.parse(valeur);
	} catch (erreur) {
		return null;
	}
}

function ecrireSession(donnees) {
	try {
		if (donnees === null)
			sessionStorage.removeItem(CLE_SESSION);
		else
			sessionStorage.setItem(CLE_SESSION, JSON.stringify(donnees));
	} catch (erreur) {
		// Stockage indisponible : il faudra se reconnecter après un rechargement.
	}
}

// Journal ////////////////////////////////////////////////////////////////////

function journaliser(texte, genre = "info") {
	const journal = element("journal");
	const heure = new Date().toLocaleTimeString("fr-FR");

	journal.prepend(creerElement("li", { "data-genre": genre }, [
		creerElement("time", { texte: heure }),
		creerElement("span", { texte })
	]));

	while (journal.children.length > TAILLE_JOURNAL)
		journal.lastElementChild.remove();
}

// Connexion //////////////////////////////////////////////////////////////////

function envoyerConnexion() {
	if (session.souhaitee)
		connexion.envoyer({ type: "admin-join", password: session.motDePasse });
}

function seConnecter(evenement) {
	evenement.preventDefault();

	session.souhaitee = true;
	session.automatique = false;
	session.motDePasse = element("mot-de-passe").value;
	element("mot-de-passe").setAttribute("aria-invalid", "false");
	element("erreur-connexion").textContent = "";
	envoyerConnexion();
	actualiser();
}

function revenirConnexion(raison) {
	session.souhaitee = false;
	session.admise = false;
	session.automatique = false;
	// L'état de la partie rejetée est oublié : il serait incohérent avec la
	// liste reçue à la prochaine connexion.
	session.partie = null;
	session.joueurs = [];
	session.listeRecue = false;
	session.finRecue = false;
	terminerCommande();
	ecrireSession(null);

	element("erreur-connexion").textContent = raison;
	element("mot-de-passe").setAttribute("aria-invalid", "true");
	actualiser();
	element("mot-de-passe").focus();
}

// La connexion administrateur n'est renvoyée automatiquement qu'à l'ouverture
// d'une nouvelle socket, jamais après un refus.
connexion.surOuverture(() => {
	session.admise = false;
	envoyerConnexion();
	actualiser();
});

connexion.surEtat(() => {
	if (!connexion.estConnectee) {
		session.admise = false;
		terminerCommande();
	}
	actualiser();
});

connexion.sur("admin-welcome", message => {
	const premiere = !session.automatique;

	session.admise = true;
	session.automatique = true;
	// Seul le souvenir d'une session admise est persisté : le mot de passe
	// est redemandé après un rechargement plutôt que stocké en clair.
	ecrireSession({ connue: true });

	changerPartie(normaliserEtat(message.state) ?? "lobby", false);
	if (premiere)
		journaliser("Connexion au serveur établie.", "succes");
	else
		journaliser("Connexion au serveur rétablie.", "succes");
	actualiser();
});

connexion.sur("rejected", message => {
	const raison = typeof message.reason === "string" && message.reason !== "" ? message.reason : "Connexion refusée.";

	// Une session déjà admise qui reçoit un autre refus reste administrateur.
	if (session.admise && !refusAvecCode(message, CODES_REFUS.adminRemplace, RAISON_REMPLACEE))
		return;

	revenirConnexion(raison);
});

// Partie /////////////////////////////////////////////////////////////////////

function changerPartie(etat, journal = true) {
	const precedent = session.partie;
	if (etat === precedent)
		return;

	session.partie = etat;
	if (etat === "lobby" || (etat === "running" && (precedent === "lobby" || precedent === "over" || precedent === "stopped"))) {
		session.finRecue = false;
		element("bloc-resultats").hidden = true;
	}

	if (journal) {
		const textes = {
			running: precedent === "paused" ? "Partie reprise." : "Partie lancée.",
			paused: "Partie mise en pause.",
			over: "Manche terminée.",
			stopped: "Manche arrêtée.",
			lobby: "Retour en salle d'attente."
		};
		journaliser(textes[etat], etat === "stopped" ? "erreur" : "info");
	}

	actualiser();
}

connexion.sur("game", message => {
	const etat = normaliserEtat(message.state);
	if (etat !== null && session.admise)
		changerPartie(etat);
});

connexion.sur("players", message => {
	if (!session.admise || !Array.isArray(message.players))
		return;

	const joueurs = message.players.map(normaliserJoueur);
	if (session.listeRecue) {
		for (const { texte, genre } of evenementsEntre(session.joueurs, joueurs))
			journaliser(texte, genre);
	}

	session.joueurs = joueurs;
	session.listeRecue = true;
	afficherJoueurs();
	deduireResultats();
});

/**
 * Affiche les résultats d'une manche à partir d'un message `end`, reçu ou reconstitué.
 *
 * @returns {object} résumé de la fin (voir `resumerFin`)
 */
function afficherResultats(fin) {
	const vainqueur = fin.winner ?? null;
	const classement = Array.isArray(fin.ranking) ? fin.ranking : [];
	const resume = resumerFin(fin, { arretee: session.partie === "stopped" });

	element("bloc-resultats").hidden = false;
	element("resultat-message").hidden = resume.message === null;
	element("resultat-message").textContent = resume.message ?? "";
	element("resultat-vainqueur").hidden = vainqueur === null;
	if (vainqueur !== null) {
		element("pseudo-vainqueur").textContent = vainqueur.pseudo;
		poserAvatar(element("avatar-vainqueur"), entier(vainqueur.id, null));
	}
	element("resultat-total").textContent = resume.total > 0 ? `${resume.total} participant${resume.total > 1 ? "s" : ""}` : "";
	remplirClassement(element("corps-classement"), classement);
	return resume;
}

// Si le message `end` a été manqué (panneau ouvert ou reconnecté après la fin),
// les résultats sont reconstitués à partir des rangs de la liste des joueurs.
function deduireResultats() {
	if (session.finRecue || (session.partie !== "over" && session.partie !== "stopped"))
		return;

	const classes = session.joueurs.filter(joueur => joueur.rang > 0).sort((a, b) => a.rang - b.rang);
	if (classes.length === 0)
		return;

	const gagnant = session.joueurs.find(joueur => joueur.statut === "winner");
	afficherResultats({
		winner: gagnant === undefined ? null : { id: gagnant.id, pseudo: gagnant.pseudo },
		ranking: classes.map(({ id, pseudo, kills, rang }) => ({ id, pseudo, kills, rank: rang })),
		stopped: session.partie === "stopped"
	});
}

connexion.sur("end", message => {
	if (!session.admise)
		return;

	session.finRecue = true;
	const resume = afficherResultats(message);
	const vainqueur = message.winner ?? null;

	if (vainqueur !== null)
		journaliser(`Victoire de ${vainqueur.pseudo} !`, "succes");
	else if (resume.message !== null)
		journaliser(`${resume.message}.`, resume.arretee ? "erreur" : "succes");
	else
		journaliser("Fin de la manche sur une égalité.", "succes");
});

// Commandes //////////////////////////////////////////////////////////////////

function envoyerCommande(commande) {
	if (session.commande !== null || !(COMMANDES_PERMISES[session.partie] ?? []).includes(commande))
		return;

	if (!connexion.envoyer({ type: "admin-command", command: commande })) {
		notifier("Impossible d'envoyer la commande : connexion perdue.", "erreur");
		return;
	}

	session.commande = {
		nom: commande,
		minuteur: setTimeout(() => {
			terminerCommande();
			notifier(`${LIBELLES_COMMANDE[commande]} : aucune réponse du serveur.`, "erreur");
			actualiserCommandes();
		}, DELAI_ACQUITTEMENT)
	};
	actualiserCommandes();
}

function terminerCommande() {
	if (session.commande !== null)
		clearTimeout(session.commande.minuteur);
	session.commande = null;
}

connexion.sur("ack", message => {
	if (session.commande !== null && session.commande.nom === message.command)
		terminerCommande();

	if (message.ok === true) {
		notifier(SUCCES_COMMANDE[message.command] ?? "Commande effectuée.", "succes");
	} else {
		const erreur = typeof message.error === "string" && message.error !== "" ? message.error : "erreur inconnue";
		notifier(`${LIBELLES_COMMANDE[message.command] ?? "Commande"} impossible : ${erreur}`, "erreur");
		journaliser(`Commande « ${message.command} » refusée : ${erreur}`, "erreur");
	}
	actualiserCommandes();
});

function demanderArret() {
	const dialogue = element("dialogue-arret");
	if (typeof dialogue.showModal !== "function") {
		envoyerCommande("stop");
		return;
	}

	dialogue.returnValue = "";
	dialogue.showModal();
}

// Affichage //////////////////////////////////////////////////////////////////

function afficherJoueurs() {
	const joueurs = session.joueurs;
	const enPartie = session.partie === "running" || session.partie === "paused";

	tableau.afficher(joueurs, session.partie);
	element("stat-inscrits").textContent = String(joueurs.length);
	element("stat-connectes").textContent = String(joueurs.filter(joueur => joueur.connecte).length);
	element("stat-vivants").textContent = enPartie || session.partie === "over" || session.partie === "stopped"
		? String(joueurs.filter(joueur => joueur.statut !== "eliminated").length)
		: "–";
}

function actualiserCommandes() {
	const permises = session.admise && connexion.estConnectee ? COMMANDES_PERMISES[session.partie] ?? [] : [];

	for (const bouton of boutons) {
		const commande = bouton.dataset.commande;
		bouton.disabled = session.commande !== null || !permises.includes(commande);
		bouton.setAttribute("aria-busy", String(session.commande?.nom === commande));
	}

	const start = element("commande-start");
	start.textContent = session.partie === "over" || session.partie === "stopped" ? "Nouvelle manche" : "Lancer la partie";

	element("aide-commande").textContent = !connexion.estConnectee
		? "Connexion au serveur perdue : les commandes sont indisponibles."
		: AIDES_ETAT[session.partie] ?? "";
}

function actualiser() {
	afficherEcran(session.admise || session.automatique ? "ecran-panneau" : "ecran-connexion");

	const enCours = session.souhaitee && !session.admise && !session.automatique;
	const bouton = element("bouton-connexion");
	bouton.disabled = enCours;
	bouton.setAttribute("aria-busy", String(enCours));
	bouton.textContent = enCours ? (connexion.estConnectee ? "Connexion" : "Connexion au serveur") : "Se connecter";

	const badge = element("badge-etat");
	const etat = session.partie ?? "lobby";
	badge.dataset.etat = connexion.estConnectee && session.admise ? etat : "";
	badge.textContent = connexion.estConnectee && session.admise ? LIBELLES_ETAT[etat] : "Hors ligne";

	actualiserCommandes();
	afficherJoueurs();
}

// Démarrage //////////////////////////////////////////////////////////////////

const memorisee = lireSession();
if (memorisee !== null) {
	// Une session était admise : la reconnexion demande à nouveau le mot de
	// passe. Un ancien stockage l'ayant conservé est effacé.
	ecrireSession({ connue: true });
	element("erreur-connexion").textContent = "Session précédente : saisissez à nouveau le mot de passe.";
}

// En démonstration, la manette ouverte depuis le lien reste elle aussi en démonstration
// (chaque page simule son propre serveur).
const lien = estModeDemo() ? `${urlManette()}?mock=1` : urlManette();
element("url-manette").textContent = lien;
element("url-manette").href = lien;
element("copier-url").addEventListener("click", async () => {
	try {
		await navigator.clipboard.writeText(lien);
		notifier("Lien de la manette copié.", "succes");
	} catch (erreur) {
		notifier("Copie impossible : sélectionnez le lien manuellement.", "erreur");
	}
});

element("formulaire-connexion").addEventListener("submit", seConnecter);
element("mot-de-passe").addEventListener("input", () => {
	element("mot-de-passe").removeAttribute("aria-invalid");
	element("erreur-connexion").textContent = "";
});
for (const bouton of boutons) {
	bouton.addEventListener("click", () => {
		if (bouton.dataset.commande === "stop")
			demanderArret();
		else
			envoyerCommande(bouton.dataset.commande);
	});
}
element("dialogue-arret").addEventListener("close", () => {
	if (element("dialogue-arret").returnValue === "confirmer")
		envoyerCommande("stop");
});

lierIndicateurConnexion(element("connexion"), connexion, estModeDemo());
actualiser();
connexion.ouvrir();
