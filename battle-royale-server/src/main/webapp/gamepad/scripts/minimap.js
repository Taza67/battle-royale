// Constantes ////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Couleurs
//// Champ de bataille
const BATTLE_FIELD_COLOR = "rgba(187, 214, 184, 1)";

// Carte
const MAP_GAME_WIDTH = 1280,
      MAP_GAME_HEIGHT = 720;

// Joueur
const PLAYER_RADIUS = 20;
const PLAYER_COLOR = "black";

// Variables /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Joueurs
let playerPosition;

// Obstacles
let obstacles;

// Minimap
let canvas, context, cssCanvas;
let width, height;
let coefX, coefY;

// Map

// Fonctions /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Initialisations ////////////////////
///////////////////////////////////////

// Initialise tous les "paramètres globaux" du script
function initializeMinimap(event) {
    // Minimap
    //// Récupération de l'élément html
    canvas = document.getElementById("minimap");
    cssCanvas = getComputedStyle(canvas);

    //// Récupération de ses dimensions
    width = parseFloat(cssCanvas.getPropertyValue('width'));
    height = width / 1.78;

    //// Initialisation de l'élémént canvas
    context = canvas.getContext("2d");
    context.canvas.width = width;
    context.canvas.height = height;

    // Coefficients de mise à l'échelle
    coefX = width / MAP_GAME_WIDTH;
    coefY = height / MAP_GAME_HEIGHT;

    console.log(canvas.width, canvas.height, width, height, coefX, coefY);

    //// Dessin
    drawMinimap();
}


// Dessins ////////////////////////////
///////////////////////////////////////

// Dessine le champ de bataille
function drawBattlefield() {
    // Nettoyage préalable
    context.clearRect(0, 0, canvas.width, canvas.height);

    context.beginPath();
    context.rect(0, 0, width, height);

    // Remplissage
    context.fillStyle = BATTLE_FIELD_COLOR;
    context.fill();
}

// Dessine le joueur
function drawPlayer(x, y) {
    let scaleX, scaleY, scaleRadius;

    // On dessine d'aboard le champ de bataille
    drawBattlefield();

    // On met à l'échelle
    scaleX = x * coefX;
    scaleY = y * coefY;
    scaleRadius = PLAYER_RADIUS * Math.min(coefX, coefY);

    console.log(x, y, PLAYER_RADIUS, scaleX, scaleY, scaleRadius);

    context.beginPath();
    context.rect(scaleX, scaleY, scaleRadius, scaleRadius);

    // Remplissage
    context.fillStyle = PLAYER_COLOR;
    context.fill();
}

// Dessine la minimap
function drawMinimap() {
    drawBattlefield();
}

// Exports ///////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

export { initializeMinimap, drawPlayer };