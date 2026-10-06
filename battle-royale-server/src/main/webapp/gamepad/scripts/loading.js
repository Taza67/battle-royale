// Variables /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// loadingTitle
let loadingTitle = document.getElementById("loading-title");
let initialLoadingTitle = "Connexion"

// Nombre de points
let pointsNumber = 0;

// Fonctions /////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

// Simule une animation de chargement
function simulateLoading() {
	let title = initialLoadingTitle;

	// Nombre de points
	if (pointsNumber == 3) pointsNumber = 0;
	pointsNumber++;
	
	// Ajout des points au titre
	for (let i = 0; i < pointsNumber; i++)
		title += ".";
		
	loadingTitle.innerHTML = title;
}

// Change l'initialLoadingTitle
function changeInitialLoadingTitle(newTitle) {
	initialLoadingTitle = newTitle;
	loadingTitle.innerHTML = initialLoadingTitle;
}


// Exports ///////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////

export { simulateLoading, changeInitialLoadingTitle };