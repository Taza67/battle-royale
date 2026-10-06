// Variables /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// loadingTitle
let loadingTitle = document.getElementById("loading-title");

// Nombre de points
let pointsNumber = 0;

// Bouton de chargement
let loadingButtonId;
let allButtons = ["start", "stop", "pause"];
let buttonPointsNumber = 0;

// Fonctions /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Simule une animation de chargement
function simulateLoading() {
	let initialLoadingTitle = "Chargement en cours"
	
	// Nombre de points
	if (pointsNumber == 3) pointsNumber = 0;
	pointsNumber++;
	
	// Ajout des points au titre
	for (let i = 0; i < pointsNumber; i++)
		initialLoadingTitle += ".";
		
	loadingTitle.innerHTML = initialLoadingTitle;
}

// Simule une animation de chargement sur un boutton
function simulateButtonLoading() {
	let button = document.getElementById(loadingButtonId);
	let buttonInitialContent = loadingButtonId.toUpperCase();

	// Réinitialisation de tous les boutons
	for (let buttonId of allButtons) {
		let oneButton = document.getElementById(buttonId);
		oneButton.innerHTML = buttonId.toUpperCase();
	}

	// Nombre de points
	if (buttonPointsNumber == 3) buttonPointsNumber = 0;
	buttonPointsNumber++;
	
	// Ajout des points au titre
	for (let i = 0; i < buttonPointsNumber; i++)
		buttonInitialContent += ".";
		
	button.innerHTML = buttonInitialContent;
}

// Change le bouton affecté par la simulation de chargement
function setButtonLoading(buttonId) {
	loadingButtonId = buttonId;
}

// Réinitialise la simulation de chargement sur les boutons
function reinitButtonsLoading() {
	// Réinitialisation de tous les boutons
	for (let buttonId of allButtons) {
		let oneButton = document.getElementById(buttonId);
		oneButton.innerHTML = buttonId.toUpperCase();
	}

	buttonPointsNumber = 0;
}

// Exports ///////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

export {reinitButtonsLoading, setButtonLoading, simulateLoading, simulateButtonLoading};