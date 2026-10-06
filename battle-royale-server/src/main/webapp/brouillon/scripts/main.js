import { addMessageListener, initializeClient, removeMessageListener, sendMessage, waitForMessage } from "./client.js";
import {
    isFullScreen, putBackMainScreen, 
    putFullscreenRequestContainer, handleFullScreenChange, 
    handleScreenOrientationChange, handleWindowOrientationChange, changeMainScreen
} from "./dynamicStyle.js";
import { getPseudo } from "./registration.js";
import {
    initializeMinimap
} from "./minimap.js";
import {
    initializeGamepad
} from "./gamepad.js";
import { changeInitialLoadingTitle, simulateLoading } from "./loading.js";
// Variables /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Initialisation de la webSocket
let wsIsInitialized = false;

// Identifiant du joueur
let id;

// Fonctions /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Initialisations ////////////////////
///////////////////////////////////////

// Connecte le joueur et envoie le pseudo du joueur
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

    // Vérification du pseudo
    if (pseudo == "") {
        alert("Entrez un pseudo !");
        return;
    }

    // Envoi du pseudo
    message = {
        type: "informations",
        pseudo: pseudo
    };
    sendMessage(message);

    // On passe à l'écran d'attente
    changeInitialLoadingTitle("Connexion en cours");
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
    
    // Récupération de l'id du joueur
    id = +response;

    // On repasse à l'écran d'attente
    changeInitialLoadingTitle("Attente de démarrage");
    
    // Attente du démarrage
    response  = await waitForMessage(simulateLoading, 500);

    console.log(response);

    if (response == "start") {
        // On passe au gamepad
        changeMainScreen("gamepad-container");
        putBackMainScreen();

        // On initialise le gamepad
        initializeGamepad();

        // Chargement du gestionnaire de message
        addMessageListener(handleMessage);
    }
}

async function handleMessage(event) {
    let message = event.data, response;
    switch (message) {
        case "pause":
            // On passe à l'écran d'attente
            changeInitialLoadingTitle("Pause");
            changeMainScreen("waiting-container");
            putBackMainScreen();

            // Attente de reprise
            removeMessageListener(handleMessage);
            response = await waitForMessage(simulateLoading, 500);
            addMessageListener(handleMessage);

            // Message de reprise
            if (response == "continue") {
                // On repasse au gamepad
                changeMainScreen("gamepad-container");
                putBackMainScreen();
            } else {
                // Erreur
                alert("Il semble y avoir eu un problème ! Désolé :(")
                changeInitialLoadingTitle("Erreur");
                simulateLoading();
            }
            return;
        case "stop":
            // On passe à l'écran d'attente
            changeInitialLoadingTitle("Fin du jeu");
            changeMainScreen("waiting-container");
            putBackMainScreen();

            // Attente de reprise
            removeMessageListener(handleMessage);
            response = await waitForMessage(simulateLoading, 500);
            return;
    }

    console.log(message);
}

// Écouteurs /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

window.addEventListener("load", () => {

    initializeMinimap();

    // Écouteurs
    // // Plein écran
    document.addEventListener("fullscreenchange", handleFullScreenChange);

    // // Orientation
    if (screen.orientation && screen.orientation.type)
        screen.orientation.addEventListener("change", handleScreenOrientationChange);
    else
        window.addEventListener("orientationchange", handleWindowOrientationChange);

    // // Boutons
    document.getElementById("register-button").addEventListener("click", connect);

    // Plein écran
    if (isFullScreen()) putBackMainScreen();
    else putFullscreenRequestContainer();
});
