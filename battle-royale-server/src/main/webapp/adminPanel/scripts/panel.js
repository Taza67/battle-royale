// Variables /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////



// Fonctions /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// HTML ///////////////////////////////
///////////////////////////////////////

// Ajoute le client à la page
function addClientHTML(client) {
    let playersPanel = document.querySelector("#players-panel");

    // Si le joueur existe déjà
    let playerInfos = document.getElementById(client.pseudo);
    if (playerInfos !== null) {
        // On change juste le numéro de session
        playerInfos.children[2].textContent = "Session : " + client.session;
        return;
    }

    // Création du bloc d'infos du joueur
    playerInfos = document.createElement("div");
    playerInfos.id = client.pseudo;
    playerInfos.classList.add("player-infos");

    // Création des infos
    //// Pseudo
    let pseudo = document.createElement("span");
    pseudo.textContent = "Pseudo : " + client.pseudo;
    //// Identifiant
    let id = document.createElement("span");
    id.textContent = "Identifiant : " + client.id;
    //// Session
    let session = document.createElement("span");
    session.textContent = "Session : " + client.session;

    // Ajout des infos
    playerInfos.appendChild(pseudo);
    playerInfos.appendChild(id);
    playerInfos.appendChild(session);

    // Ajout du bloc
    playersPanel.appendChild(playerInfos);
}


// Exports ///////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

export {addClientHTML};