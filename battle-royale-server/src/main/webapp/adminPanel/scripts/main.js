import { addMessageListener, initializeClient, removeMessageListener, sendMessage, waitForMessage } from "./client.js";
import { changeMainScreen, putBackMainScreen} from "./dynamicStyle.js"
import { getPseudo } from "./registration.js";
import { setButtonLoading, simulateLoading, simulateButtonLoading, reinitButtonsLoading } from "./loading.js";
import { addClientHTML } from "./panel.js";
// Variables /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Initialisation de la webSocket
let wsIsInitialized = false;

// Fonctions /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Initialisations ////////////////////
///////////////////////////////////////

// Envoie le pseudo de l'admin au jeu et lance le panneau d'administration
function connect() {
    !wsIsInitialized ? initializeClient()
    .then(() => {
        sendPseudo();

        // WebSocket initialisée
        wsIsInitialized = true;
    })
    .catch(function(error) {
        console.error(error);
    }) : sendPseudo();
}


// Envoie le pseudo du joueur
async function sendPseudo() {
    let message, pseudo, response;

    // Récupération du pseudo entré
    pseudo = getPseudo();

    // Envoi du pseudo
    message = {
        type: "informations",
        pseudo: pseudo
    };
    sendMessage(message);

    // On passe à l'écran de chargement
    changeMainScreen("waiting-container");
    putBackMainScreen();

    // Attente de la confirmation
    response = await waitForMessage(simulateLoading, 500);
    
    // Échec de la connexion ?
    if (response == "ko") {
        alert("Échec de la connexion !");

        // Retour à l'écran de connexion
        changeMainScreen("registration-container");
        putBackMainScreen();

        return;
    }

    // On passe au panneau d'admin
    changeMainScreen("panel-container");
    putBackMainScreen();

    // On ajoute l'écouteur d'inscriptions de clients
    addMessageListener(addClient);
}

// Communication //////////////////////
///////////////////////////////////////

// Envoie un message indiquant le début du jeu
async function startGame() {
    // On enlève l'écouteur d'inscriptions de clients
    removeMessageListener(addClient);

    // Envoi du message
    sendMessage("admin-debut");

    // Attente de la confirmation
    setButtonLoading("start");
    let response = await waitForMessage(simulateButtonLoading, 500);
    if (response != "ok") alert("La requête de début n'a pas réussie !");
    reinitButtonsLoading();

    // Le bouton start doit être désactivé
    document.getElementById("start").removeEventListener("click", startGame);
    document.getElementById("start").classList.add("panel-button-disabled");

    // Les boutons pause et stop sont désormais fonctionnels
    document.getElementById("pause").addEventListener("click", pauseGame);
    document.getElementById("pause").classList.toggle("panel-button-disabled");
    document.getElementById("stop").addEventListener("click", stopGame);
    document.getElementById("stop").classList.toggle("panel-button-disabled");
}

// Envoie un message indiquant une pause du jeu
async function pauseGame() {
    // Envoi du message
    sendMessage("admin-pause");

    // Attente de la confirmation
    setButtonLoading("pause");
    let response = await waitForMessage(simulateButtonLoading, 500);
    if (response != "ok") alert("La requête de pause n'a pas réussie !");
    reinitButtonsLoading();

    // Les boutons stop et pause doivent être désactivés
    document.getElementById("pause").removeEventListener("click", pauseGame);
    document.getElementById("pause").classList.toggle("panel-button-disabled");
    document.getElementById("stop").removeEventListener("click", stopGame);
    document.getElementById("stop").classList.toggle("panel-button-disabled");

    // Le bouton continue est fonctionnel
    document.getElementById("continue").addEventListener("click", continueGame);
    document.getElementById("continue").classList.toggle("panel-button-disabled");
}

// Envoie un message indiquant la reprise du jeu
async function continueGame() {
    // Envoi du message
    sendMessage("admin-continue");

    // Attente de la confirmation
    setButtonLoading("continue");
    let response = await waitForMessage(simulateButtonLoading, 500);
    if (response != "ok") alert("La requête de pause n'a pas réussie !");
    reinitButtonsLoading();

    // Le bouton continue doit être désactivé
    document.getElementById("continue").removeEventListener("click", continueGame);
    document.getElementById("continue").classList.toggle("panel-button-disabled");

    // Les boutons pause et stop sont fonctionnels
    document.getElementById("pause").addEventListener("click", pauseGame);
    document.getElementById("pause").classList.toggle("panel-button-disabled");
    document.getElementById("stop").addEventListener("click", stopGame);
    document.getElementById("stop").classList.toggle("panel-button-disabled");
}

// Envoie un message indiquant la fin du jeu
async function stopGame() {
    // Envoi du message
    sendMessage("admin-stop");

    // Attente de la confirmation
    setButtonLoading("stop");
    let response = await waitForMessage(simulateButtonLoading, 500);
    if (response != "ok") alert("La requête de fin n'a pas réussie !");
    reinitButtonsLoading();

    // Tous les boutons doivent être désactivés
    document.getElementById("pause").removeEventListener("click", pauseGame);
    document.getElementById("pause").classList.toggle("panel-button-disabled");
    document.getElementById("stop").removeEventListener("click", stopGame);
    document.getElementById("stop").classList.toggle("panel-button-disabled");
}

// Gestion des clients ////////////////
///////////////////////////////////////

// Ajoute le client reçu en message à la liste des clients 
function addClient(event) {
    let client = JSON.parse(event.data);

    addClientHTML(client);
}


// Écouteurs /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

window.addEventListener("load", () => {
    // Écouteurs
    // // Boutons
    document.getElementById("register-button").addEventListener("click", connect);
    document.getElementById("start").addEventListener("click", startGame);
});
