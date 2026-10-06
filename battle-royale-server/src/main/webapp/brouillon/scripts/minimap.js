// Constantes ////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Couleurs
//// Champ de bataille
const BATTLE_FIELD_COLOR = "rgba(187, 214, 184, 1)";

// Variables /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Joueurs
let playerPosition;

// Obstacles
let obstacles;

// Minimap
let canvas, context, cssCanvas;
let width, height;

// Dimensions de la grille et d'une zone
let gridHeight, gridWidth;
let zoneHeight, zoneWidth;

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
    height = parseFloat(cssCanvas.getPropertyValue('height'));

    //// Initialisation de l'élémént canvas
    context = canvas.getContext("2d");
    context.canvas.width = width;
    context.canvas.height = height;

    // Dimensions
    gridHeight = 10;
    gridWidth = 7;
    zoneHeight = height / gridHeight;
    zoneWidth = width / gridWidth;

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
function drawGrid() {
    context.beginPath();
    
    // for (let i = 0; i < gridHeight; i++)
    //     for (let j = 0; j < gridWidth; j++) 

    //         // Bords
    //     }

    context.rect(0, 0, Math.floor(zoneWidth), Math.floor(zoneHeight));

    /// Contour
    context.StrokeStyle = "black";
    context.linewidth = 1;
    context.stroke();
}

// Dessine la minimap
function drawMinimap() {
    drawBattlefield();
    drawGrid();
}


// Exports ///////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

export { initializeMinimap };