// Variables /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// WebSocket
let WebSocketServer;
let fs;
let wss;
let nbClients;

// Clients
let clients;

// Fonctions /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Initialisations ////////////////////
///////////////////////////////////////

// Initialise tous les "paramètres globaux" du script
function initialize(event) {
    // Socket serveur
    WebSocketServer = require("ws").Server;
    fs = require("fs");
    wss = new WebSocketServer( { port: 8100 } );

    // Clients
    nbClients = 0;
    //// Sockets clients
    clients = new Set();

    console.log("Server ready...");
}


// Communication //////////////////////
///////////////////////////////////////

// Traite l'initialisation de la webSocket
function handleWebSocketConnection(ws) {
    let message;

    clients.add(ws);
    // Affichage
    console.log(`Client ${nbClients} is connected online !! Yeahhhh !!`);
    
    // Envoi du numéro attribué au client
    message = {
        type: 'number',
        content: nbClients++,
    }
    sendMessage(ws, message);

    // Ouverture des flux
    ws.on("message", receptMessage);
    ws.on("close", handleWebSocketClose);
}

// Traite la fermeture de la webSocket
function handleWebSocketClose() {
    console.log('Client disconnected...');
}

// Traite la réception de messages
function receptMessage(formatedMessage) {
    let message = JSON.parse(formatedMessage);

    displayMessage("Réception " + message.content);
}

// Envoie un message complexe
function sendMessage(ws, message) {
    let formatedMessage = JSON.stringify(message);

    ws.send(formatedMessage);
}

// exemple sendMessage(' { "type" : "image", "content" : "Envoie moi une image" }');


// Affichage //////////////////////////
///////////////////////////////////////

// Affiche un message
function displayMessage(message) {
    console.log(message);
    // Peut-être écrit d'une manière plus complexe.
}


// Programme /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

initialize();
wss.on('connection', handleWebSocketConnection);