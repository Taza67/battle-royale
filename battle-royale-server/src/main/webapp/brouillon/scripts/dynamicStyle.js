// Variables /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Fullscreen button
let fullscreenButton = document.getElementById("fullscreen-button");

// Écran principal
let mainScreen = "registration-container";
let allScreens = ["registration-container", "fullscreen-request-container", "gamepad-container", "waiting-container"];


// Fonctions /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////


// Vérificateurs //////////////////////
///////////////////////////////////////

// Vérifie si le mode plein écran est activé
function isFullScreen(){
    let zoom = window.outerWidth / window.innerWidth;
    return (window.innerHeight * zoom) == screen.height && (window.innerWidth * zoom) == screen.width;
}

// Affichage //////////////////////////
///////////////////////////////////////

// Met le site en plein écran
function launchFullScreen() {
    let element = document.documentElement;
    if (element.requestFullScreen) {
        element.requestFullScreen();
    } else if (element.mozRequestFullScreen) {
        element.mozRequestFullScreen();
    } else if (element.webkitRequestFullScreen) {
        element.webkitRequestFullScreen();
    }
	putBackMainScreen();
}

// Remet l'écran principal
function putBackMainScreen() {
    let mainContainer = document.getElementById(mainScreen);

    // On cache tous les autres écrans
    for (screen of allScreens) {
        let container = document.getElementById(screen);

        container.style.display = "none";
    }

    // On affiche l'écran principal
    mainContainer.style.display = "flex";
    
    // Active la rotation de la fenêtre s'il s'agit de l'écran de connexion
    if (mainScreen == "registration-container") {
		if (typeof putBackMainScreen.rotationIsDone === 'undefined')
			putBackMainScreen.rotationIsDone = false;
		
		
		let registerFormContainer = document.getElementById("register-container");
		
		// Si la rotation n'a pas été faite
		if (!putBackMainScreen.rotationIsDone) {
			registerFormContainer.classList.add("register-container-animation");
			putBackMainScreen.rotationIsDone = true;
		}
	}
}


// Pose l'écran de demande de passage au plein écran
function putFullscreenRequestContainer() {
    let fullscreenRequestContainer = document.getElementById("fullscreen-request-container");

    // On cache tous les autres écrans
    for (screen in allScreens.values()) {
        console.log(screen);
        let container = document.getElementById(screen);

        container.style.display = "none";
    }

    fullscreenRequestContainer.style.display = "flex";
    fullscreenButton.addEventListener("click", launchFullScreen);
}

// Change l'écran principal
function changeMainScreen(name) {
	mainScreen = name;
}

// Écouteurs /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////


// Gère les événéments de passage au plein écran
function handleFullScreenChange() {
    if (!document.fullscreenElement) {
        putFullscreenRequestContainer();
        fullscreenButton.addEventListener("click", launchFullScreen);
    }
}

// Gère les événements de changement d'orientation
//// Version avec screen
function handleScreenOrientationChange() {
    if (screen.orientation.type.startsWith("portrait")) {
        // On force l'orientation en paysage
        screen.orientation.lock('landscape')
        .then(function() {
            console.log('Affichage verrouillé en mode paysage !');
        })
        .catch(function(error) {
            console.warn('Impossible de verrouiller l\'affichage : ', error);
        });
    }
}

//// Version avec window
function handleWindowOrientationChange() {
    if (window.orientation === 0) {
        // On force l'orientation en paysage
        window.screen.orientation.unlock();             // annulation de la rotation précédente
        window.screen.orientation.lock('landscape');
    }
}

// Exports ///////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

export {
    isFullScreen, launchFullScreen, putBackMainScreen, 
    putFullscreenRequestContainer, handleFullScreenChange, 
    handleScreenOrientationChange, handleWindowOrientationChange,
    changeMainScreen
};