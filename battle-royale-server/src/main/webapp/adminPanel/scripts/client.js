// Variables /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////
let ws;

// Fonctions /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Initialisations ////////////////////
///////////////////////////////////////

// Initialise tous les "paramètres globaux" du script
function initializeClient() {
    return new Promise(function(resolve, reject) {
        ws = new WebSocket("ws://" + window.location.hostname + ":8080/battle-royale-server/websocketserver");
        ws.onopen = function(event) {
            handleWebSocketOpening(event);
            resolve(ws);
        };
        ws.onerror = function(event) {
            handleWebSocketError(event);
            reject(new Error('WebSocket error'));
        };
        ws.onclose = function(event) {
            handleWebSocketClosing(event);
            reject(new Error('WebSocket closed'));
        };
        // ws.onmessage = receptMessage;
    });
}


// Communication //////////////////////
///////////////////////////////////////

// Traite l'ouverture de la webSocket
function handleWebSocketOpening() {
    console.log("WebSocket is open !! Yeahhhh !!");
}

// Traite la fermeture de la websocket
function handleWebSocketClosing(event) {
	displayMessage("Fermeture");
}

// Traite les erreurs
function handleWebSocketError(event) {
	displayMessage("Erreur");
}

// Envoie un message
function sendMessage(message) {
    let formatedMessage = JSON.stringify(message);
    ws.send(formatedMessage);
}

// Attend un message
function waitForMessage(loadingFunction, time) {
	return new Promise(resolve => {
	    let loadingIntervalId = null;
	    let message = null;
	    
		// Vérifie si un message a été reçu
	    function checkForMessage() {
	      	if (ws.readyState !== WebSocket.OPEN) {
	        	clearInterval(loadingIntervalId);
	        	return;
	      	}
	      
	      	if (ws.hasMessage) {
	        	clearInterval(loadingIntervalId);
	        	resolve(message);

				// Après réception du message, on enlève l'écouteur
				ws.removeEventListener('message', messageListener);
	     	}
	    }
	    
	    loadingIntervalId = setInterval(() => {
	      	// Appel de la fonction de chargement
	      	loadingFunction();
	      
	      	checkForMessage();
	    }, time);
	    
		// Écouteur de messages
		function messageListener(event) {
			message = event.data;
			ws.hasMessage = true;
			ws.message = message;
		}
	    ws.addEventListener('message', messageListener);
  	});
}

// Ajoute la fonction donnée comme écouteur de messages
function addMessageListener(func) {
	ws.addEventListener('message', func);
}

// Retire la fonction donnée des écouteurs de messages
function removeMessageListener(func) {
	ws.removeEventListener('message', func);
}

// Affichage //////////////////////////
///////////////////////////////////////

// Affiche un message
function displayMessage(message) {
    console.log(message);
}


// Exports ///////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

export {addMessageListener, removeMessageListener, initializeClient, sendMessage, waitForMessage};
