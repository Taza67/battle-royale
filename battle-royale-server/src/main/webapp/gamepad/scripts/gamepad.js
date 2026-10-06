import {sendMessage} from "./client.js";
import { initializeMinimap } from "./minimap.js";
// Constantes ////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Couleurs
//// Arrière-plan
const JOYSTICK_BACKGROUND_COLOR = "rgba(0, 0, 0, 0.5)";
//// Socle du joystick
const JOYSTICK_STAND_COLOR = "#54515D";
//// Joystick
const JOYSTICK_COLOR = "rgba(120, 113, 129, 0.9)";
//// Contour du joystick
const JOYSTICK_OUTLINE_COLOR = "#787181";

// Communication
const MOVE_SENDING_TIMER = 50;

// Variables /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Joystick
let joystick;
let joystickContainer, cssJoystickContainer;
let width, height;

// Déplacement
let direction, speed, isMoving;

// Boutons
let buttonA, buttonB;

// Data
let pseudoContainer, statusContainer, healthBarContainer, scoreContainer, avatarContainer;
let healthBarContainerWidth;

// Fonctions /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Initialisations ////////////////////
///////////////////////////////////////

// Initialise tous les "paramètres globaux" du script
function initializeGamepad(event) {
    // Boutons
    buttonA = document.getElementById("game-button-A");
	buttonB = document.getElementById("game-button-B");

    // Joystick
    //// Récupération de l'élément html
    joystickContainer = document.getElementById("joystick");
    cssJoystickContainer = getComputedStyle(joystickContainer);

    //// Récupération de ses dimensions
    width = parseFloat(cssJoystickContainer.getPropertyValue('width'));
    height = parseFloat(cssJoystickContainer.getPropertyValue('height'));

    if (joystick != null)
        joystick.destroy();

    //// Initialisation du joystick
    joystick = nipplejs.create({
        zone: joystickContainer,
        mode: 'static',
        position: {
            left: '50%',
            top: '50%'
        },
        size: Math.min(width, height) * 0.7,
        color: JOYSTICK_COLOR,
    });

    // Boutons
    buttonA.ontouchstart = handleButtonTouchEvent;
    buttonA.ontouchend = handleButtonTouchEvent;
    buttonA.ontouchcancel = handleButtonTouchEvent;

    buttonB.ontouchstart = handleButtonTouchEvent;
    buttonB.ontouchend = handleButtonTouchEvent;
    buttonB.ontouchcancel = handleButtonTouchEvent;

    // Déplacement
    speed = 0;
    direction = 0;
    isMoving = false;
    joystick.on('move', joystickMoveHandler);
    joystick.on('end', joystickEndHandler)

    // Communication
    if (typeof initializeGamepad.moveSendingIsEnabled == 'undefined') {
        setInterval(sendMove, MOVE_SENDING_TIMER);
        initializeGamepad.moveSendingIsEnabled = true;
    }

    // Fenêtre
    window.onresize = initializeGamepad;

    // Data
    pseudoContainer = document.getElementById("pseudo-container");
    statusContainer = document.getElementById("status-container");

    healthBarContainer = document.getElementById("health-status");
    healthBarContainerWidth = parseFloat(getComputedStyle(healthBarContainer).getPropertyValue('width'));

    scoreContainer = document.getElementById("score-container");
    avatarContainer = document.getElementById("avatar-container");

    // Mini-map
    initializeMinimap();
}

// Infos //////////////////////////////
///////////////////////////////////////

// Change le pseudo du joueur sur le gamepad
function changePseudo(ps) {
    pseudoContainer.innerText = ps;
}

// Change le statut du joueur sur le gamepad
function changeStatus(st) {
    statusContainer.innerText = st;
}

// Change le poucentage de vie du joueur
function changeHealthBar(lifePoints) {
    let part = (lifePoints / 50);

    console.log(lifePoints, part);

    // Couleur
    if (part < 0.2) healthBarContainer.style.backgroundColor = "red";
    else if (part < 0.1) healthBarContainer.style.backgroundColor = "orange";

    // Largeur
    healthBarContainer.style.width = (part * healthBarContainerWidth) + "px";
}

// Change le score du joueur
function changeScore(sc) {
    scoreContainer.innerText = sc + " JOUEUR(S)";
}

// Change l'avatar
function changeAvatar(id) {
    avatarContainer.src = "./images/" + id + ".png";
}

// Calculs ////////////////////////////
///////////////////////////////////////

// Calcule la direction du joystick
function calculateDirection(angle) {
    let direction;

    // Liste des intervalles de directions
    let directions = [
        [-22.5, 22.5], [22.5, 67.5], [67.5, 112.5],
        [112.5, 157.5], [157.5, 202.5], [202.5, 247.5], 
        [247.5, 292.5], [292.5, 337.5]
    ];

    // Calcul de l'angle
    if (angle >= 337.5)
        angle = -(360 - angle);
    
    // Recherche de la direction
    for (let interval of directions) {
        if (angle > interval[0] && angle <= interval[1]) {
            direction = directions.indexOf(interval);
            break;
        }
    }

    return direction;
}


// Interactions ///////////////////////
///////////////////////////////////////

// Gère les événement de déplacements du joystick
function joystickMoveHandler(event, data) {
    // Direction du joystick
    direction = calculateDirection(data.angle.degree);
    speed = Math.min(Math.floor(data.force * 4), 4);
    isMoving = true;
}

// Gère le relâchement du joystick
function joystickEndHandler(event, data) {
    speed = 0;
    isMoving = false;
}

// Gère les clics sur les boutons
function handleButtonTouchEvent(event) {
    let message;

    event.preventDefault();
    event.stopPropagation();

    const button = event.target;
    const buttonName = button.id[12];

    if (event.type === 'touchstart') {
        button.classList.add('game-button-active');

        // Envoi du message
        if (buttonName == "A") {
            // Bouton A
            message = {
                type: "attack",
                form: 2
            }
        } else if (buttonName == "B") {
            // Bouton B
            message = {
                type: "attack",
                form: 1
            }
        }
        sendMessage(message);
    } else if (event.type === 'touchend' || event.type === 'touchcancel') {
        button.classList.remove('game-button-active');
    }
}


// Communication //////////////////////
///////////////////////////////////////

// Envoie les données de déplacement du joystick
function sendMove() {
    let message;

    if (isMoving) {
        // Message
        message = {
            type: "movement",
            direction: direction,
            speed: speed
        }

        sendMessage(message);
    }
}



// Exports ///////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

export { initializeGamepad, changeAvatar, changePseudo, changeStatus, changeHealthBar, changeScore };