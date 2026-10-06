package inside;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe représentant le plateau de jeu
 * @author mourtaza
 *
 * @see IConfig
 */
public class Board implements IConfig {
	/**
	 * Variables de classe indiquant le type des actions d'un joueur
	 */
	private final static int MOVE_ACTION = 0, ATTACK_ACTION = 1;
	/**
	 * Variable contenant toutes les informations sur la carte du jeu (obstacles, etc)
	 * @see Map
	 */
	private final Map MAP;
	/**
	 * Variable contenant un tableau des objets représentant les joueurs (vivants)
	 * @see Player
	 */
	private final Player[] PLAYERS;
	/**
	 * Variable contenant une liste des identifiants des joueurs vivants
	 */
	private final List<Integer> PLAYERS_IDS;
	/**
	 * Variable contenant un tableau des objets répresentant les joueurs (éliminés)
	 * @see Player
	 */
	private final Player[] DEADS;
	/**
	 * Variable contenant une liste des identifiants des joueurs éliminés
	 * @see Player
	 */
	private final List<Integer> DEADS_IDS;
	/**
	 * Variable contenant une liste des projectiles actifs
	 * @see Bullet
	 */
	private final List<Bullet> BULLETS;
	/**
	 * Variable contenant un tableau à 2 dimensions représentant les zones de la carte
	 * @see Zone
	 */
	private final Zone[][] AREAS;
	/**
	 * Variable contenant le thread qui gère le mode à sable
	 * @see TSandbox
	 */
	private final TSandbox SANDBOX_THREAD;
	/**
	 * Variable contenant le thread qui gère le mouvement de la lave
	 * @see TLavaMovement
	 */
	private final TLavaMovement LAVA_MOVER_THREAD;
	/**
	 * Variable contenant le thread qui gère tout ce qui est relatif à la vie des joueurs
	 * @see TPlayersHandler
	 */
	private final TPlayersHandler PLAYERS_HANDLER_THREAD;
	/**
	 * Variable indiquant si la fenêtre doit être actualisée ou pas
	 */
	private volatile boolean isWindowDirty;
	/**
	 * Variable indiquand la fin de la partie
	 */
	private volatile boolean isDone = false; 

	/**
	 * Construit une instance du plateau de jeu
	 * 
	 * @see Board#PLAYERS
	 * @see Board#PLAYERS_IDS
	 * @see Board#DEADS
	 * @see Board#DEADS_IDS
	 * @see Board#BULLETS
	 * @see Board#AREAS
	 * @see Board#generateAreas()
	 * @see Board#MAP
	 * @see Board#SANDBOX_THREAD
	 * @see Board#LAVA_MOVER_THREAD
	 * @see Board#PLAYERS_HANDLER_THREAD
	 * @see Board#isWindowDirty
	 */
	public Board() {
		PLAYERS = new Player[PLAYERS_NUMBER];
		PLAYERS_IDS = new ArrayList<>();
		DEADS = new Player[PLAYERS_NUMBER];
		DEADS_IDS = new ArrayList<>();
		BULLETS = new ArrayList<>();
		AREAS = new Zone[AREAS_HEIGHT][AREAS_WIDTH];

		generateAreas();
		MAP = new Map(AREAS);

		// Threads
		SANDBOX_THREAD = new TSandbox(this);
		LAVA_MOVER_THREAD = new TLavaMovement(this, MAP);
		PLAYERS_HANDLER_THREAD = new TPlayersHandler(this);

		isWindowDirty = true;
	}


	/**
	 * Retourne la variable indiquant si la fenêtre est sale ou pas
	 * @return true si la fenêtre est sale, false sinon
	 *
	 * @see Board#isWindowDirty
	 */
	public synchronized boolean getIsWindowDirty() { return isWindowDirty; }
	/**
	 * Retourne le tableau contenant les joueurs actifs
	 * @return Tableau contenant la liste des joueurs
	 * 
	 * @see Board#PLAYERS
	 */
	public synchronized Player[] getPLAYERS() { return PLAYERS; }
	/**
	 * Retourne la liste des identifiants des joueurs actifs
	 * @return Liste des identifiants des joueurs actifs
	 * 
	 * @see Board#PLAYERS_IDS
	 */
	public synchronized List<Integer> getPLAYERS_IDS() { return PLAYERS_IDS; }
	/**
	 * Retourne le tableau des joueurs éliminés
	 * @return le tableau des joueurs éliminés
	 * 
	 * @see Board#PLAYERS_IDS
	 */
	public synchronized Player[] getDEADS() { return DEADS; }
	
	/**
	 * Retourne la liste des projectiles actifs
	 * @return Liste des projectile actifs
	 * 
	 * @see Board#BULLETS
	 */
	public synchronized List<Bullet> getBULLETS() { return BULLETS; }
	/**
	 * Retourne la variable indiquant la fin de la partie
	 * @return true si la partie est terminée, false sinon
	 * 
	 * @see Board#isDone
	 */
	public synchronized boolean getIsDone() { return isDone; }

	/**
	 * Change la valeur de la variable indiquant le besoin d'actualisation de la fenêtre
	 * @param value true (actualisation nécessaire) ou false (actualisation non nécessaire)
	 * 
	 * @see Board#isWindowDirty
	 */
	public synchronized void setIsWindowDirty(boolean value) {
		isWindowDirty = value;
	}


	/**
	 * Génère des joueurs aléatoirement placés sur le plateau
	 * @param playersNumber Nombre de joueurs à générer
	 * 
	 * @see Board#PLAYERS
	 * @see Board#AREAS
	 * @see Weapon
	 * @see IConfig#PLAYER_RADIUS_X
	 * @see IConfig#PLAYER_RADIUS_Y
	 * @see IConfig#ONE_ZONE_HEIGHT
	 * @see IConfig#ONE_ZONE_WIDTH
	 */
	public void generatePlayers(int playersNumber) {
		for (int i = 0; i < playersNumber; i++) {
			// Création d'un joueur placé aléatoirement
			Player p = new Player(this, MAP, i, Element.getFreePosition(
				MAP.getOBSTACLES(), PLAYERS_IDS, PLAYERS, PLAYER_RADIUS_X, PLAYER_RADIUS_Y)
			);

			// Ajout du joueur à la zone adéquate
			int zoneX = (int)(p.getPosition().getX() / ONE_ZONE_WIDTH),
				zoneY = (int)(p.getPosition().getY() / ONE_ZONE_HEIGHT);
			AREAS[zoneY][zoneX].addPlayer(p.getID(), p);
			p.setZone(AREAS[zoneY][zoneX]);

			// Ajout de l'arme
			p.setWeapon(new Weapon(MAP, p));

			// Ajout du joueur à la liste des joueurs
			PLAYERS[i] = p;
			PLAYERS_IDS.add(i);
		}
		
		// Actualisation de la fenêtre requis
		setIsWindowDirty(true);
	}

	/**
	 * Génère et ajoute un joueur sur le plateau
	 * @param playerId Identifiant du joueur à générer
	 * @return Objet représentant le joueur
	 * 
	 * @see Board#PLAYERS
	 * @see Board#AREAS
	 * @see Weapon
	 * @see IConfig#PLAYER_RADIUS_X
	 * @see IConfig#PLAYER_RADIUS_Y
	 * @see IConfig#ONE_ZONE_HEIGHT
	 * @see IConfig#ONE_ZONE_WIDTH
	 */
	public Player addPlayer(int playerId) {
		// Création d'un joueur placé aléatoirement
		Player p = new Player(this, MAP, playerId, Element.getFreePosition(
			MAP.getOBSTACLES(), PLAYERS_IDS, PLAYERS, PLAYER_RADIUS_X, PLAYER_RADIUS_Y)
		);

		// Ajout du joueur à la zone adéquate
		int zoneX = (int)(p.getPosition().getX() / ONE_ZONE_WIDTH),
			zoneY = (int)(p.getPosition().getY() / ONE_ZONE_HEIGHT);
		AREAS[zoneY][zoneX].addPlayer(p.getID(), p);
		p.setZone(AREAS[zoneY][zoneX]);

		// Ajout de l'arme
		p.setWeapon(new Weapon(MAP, p));
		
		// Ajout du joueur à la liste des joueurs
		PLAYERS[playerId] = p;
		PLAYERS_IDS.add(playerId);

		// Actualisation de la fenêtre requis
		setIsWindowDirty(true);
		
		return p;
	}

	/**
	 * Ajoute une projectile sur le plateau
	 * @param Objet représentant le projectile
	 * 
	 * @see Board#BULLETS
	 */
	public synchronized void addBullet(Bullet b) {
		BULLETS.add(b);
	}

	/**
	 * Retire une balle du plateau de jeu
	 * @param b Objet représentant la balle
	 * 
	 * @see Board#BULLETS
	 */
	public synchronized void destroyBullet(Bullet b) {
		BULLETS.remove(b);
	}

	/**
	 * Génère les zones de la carte
	 * 
	 * @see Board#AREAS
	 * @see IConfig#AREAS_HEIGHT
	 * @see IConfig#AREAS_WIDTH
	 */
	public void generateAreas() {
		// Création des zones
		for (int i = 0; i < AREAS_HEIGHT; i++)
			for (int j = 0; j < AREAS_WIDTH; j++)
				AREAS[i][j] = new Zone(j, i);

		// Affectation des voisins de chaque zone
		for (int i = 0; i < AREAS_HEIGHT; i++)
			for (int j = 0; j < AREAS_WIDTH; j++)
				AREAS[i][j].collectNeigbors(AREAS);
	}

	/**
	 * Déplace un joueur
	 * 
	 * @param id Identifiant du joueur
	 * @param direction Direction du déplacement
	 * @param speed Vitesse du déplacement
	 * 
	 * @see Board#PLAYERS
	 */
	public synchronized void movePlayer(int id, int direction, int speed) {
		if (PLAYERS[id] != null)
			PLAYERS[id].move(direction, speed);
	}

	/**
	 *  Stoppe les threads liés au plateau de jeu
	 *  
	 *  @see Board#SANDBOX_THREAD
	 *  @see Board#LAVA_MOVER_THREAD
	 *  @see Board#PLAYERS_HANDLER_THREAD
	 */
	public synchronized void stopThreads() {
		SANDBOX_THREAD.interrupt();
		LAVA_MOVER_THREAD.interrupt();
		PLAYERS_HANDLER_THREAD.interrupt();
	}

	/**
	 * Traite une tableau d'octets représentant des actions à réaliser aux joueurs
	 * @param data Tableau d'octets représentant les actions
	 * 
	 * @see Board#movePlayer(int, int, int)
	 * @see Board#drawPlayerWeapon(int)
	 * @see Board#shootBulletPlayer(int)
	 * @see Board#MOVE_ACTION
	 * @see Board#ATTACK_ACTION
	 */
	public synchronized void treatData(byte[] data) {
		int dataSize = data.length,
			it = 0;
		
		// Traitement de toutes les actions
		while (it < dataSize) {
			int playerId = data[it++],
				actionType = data[it++];
			
			if (actionType == MOVE_ACTION) {
				// Déplacement
				int direction = data[it++],
					speed = data[it++];
				movePlayer(playerId, direction, speed);
			} else if (actionType == ATTACK_ACTION) {
				// Attaque
				int typeAttaque = data[it++];
				
				// 2 types d'attaque (distance et corps-à-corps)
				if (typeAttaque == 1) drawPlayerWeapon(playerId);
				else if (typeAttaque == 2) shootBulletPlayer(playerId);
			}
		}
		
		if (dataSize > 0) setIsWindowDirty(true);
	}
	
	// id, vivant, vie, position, score
	
	/**
	 * Retourne les états des joueurs
	 * @return Ensemble de données représentant les états des joueurs
	 */
	public synchronized byte[] getPlayersStates() {
		byte[] data = new byte[(PLAYERS_IDS.size() + DEADS_IDS.size()) * 7];
		int it = 0;
		
		if (PLAYERS_IDS.size() == 1) {
			Integer id = PLAYERS_IDS.get(0);
			// Identifiant
			data[it++] = id.byteValue();
			// Vie
			data[it++] = 2;
			// Points de vie
			data[it++] = (byte)PLAYERS[id].getLifePoints();
			// Position
			// // X
			int posX = (int)PLAYERS[id].getPosition().getX();
			short x = (short)(posX & 0xFFFF);
			data[it++] = (byte)(x >> 8);
			data[it++] = (byte)x;
			// // Y
			int posY = (int)PLAYERS[id].getPosition().getY();
			short y = (short)(posY & 0xFFFF);
			data[it++] = (byte)(y >> 8);
			data[it++] = (byte)(y);
		} else {
			// Joueurs vivants
			for (Integer id : PLAYERS_IDS) {
				// Identifiant
				data[it++] = id.byteValue();
				// Vie
				data[it++] = (byte)(PLAYERS[id].getIsAlive() ? 1 : 0);
				// Points de vie
				data[it++] = (byte)PLAYERS[id].getLifePoints();
				// Position
				// // X
				int posX = (int)PLAYERS[id].getPosition().getX();
				short x = (short)(posX & 0xFFFF);
				data[it++] = (byte)(x >> 8);
				data[it++] = (byte)x;
				// // Y
				int posY = (int)PLAYERS[id].getPosition().getY();
				short y = (short)(posY & 0xFFFF);
				data[it++] = (byte)(y >> 8);
				data[it++] = (byte)(y);
			}
		}
		
		// Joueurs éliminés
		for (Integer id : DEADS_IDS) {
			// Identifiant
			data[it++] = id.byteValue();
			// Vie
			data[it++] = (byte)(DEADS[id].getIsAlive() ? 1 : 0);
			// Points de vie
			data[it++] = (byte)DEADS[id].getLifePoints();
			// Position
			// // X
			int posX = (int)DEADS[id].getPosition().getX();
			short x = (short)(posX & 0xFFFF);
			data[it++] = (byte)(x >> 8);
			data[it++] = (byte)x;
			// // Y
			int posY = (int)DEADS[id].getPosition().getY();
			short y = (short)(posY & 0xFFFF);
			data[it++] = (byte)(y >> 8);
			data[it++] = (byte)(y);
		}
		
		return data;
	}

	/**
	 * Élimine un joueur
	 * @param p Objet représentant le joueur
	 * 
	 * @see Board#PLAYERS
	 * @see Board#PLAYERS_IDS
	 * @see Board#DEADS
	 * @see Board#DEADS_IDS
	 * @see Board#isDone
	 */
	public synchronized void killPlayer(Player p) {
		PLAYERS[p.getID()] = null;
		PLAYERS_IDS.remove((Integer)p.getID());
		DEADS[p.getID()] = p;
		DEADS_IDS.add(p.getID());
		p.getZone().deletePlayer(p.getID());
		p.getZone().addDead(p.getID(), p);
		
		if (PLAYERS_IDS.size() == 1)
			isDone = true;
	}

	/**
	 * Fais dégainer l'arme d'un joueur
	 * @param id Identifiant du joueur
	 * 
	 * @see Board#PLAYERS
	 */
	public synchronized void drawPlayerWeapon(int id) {
		if (PLAYERS[id] != null)
			PLAYERS[id].drawWeapon();
	}

	/**
	 * Fais tirer une balle à un joueur
	 * @param id Identifiant du joueur
	 * 
	 * @see Board#PLAYERS
	 */
	public synchronized void shootBulletPlayer(int id) {
		if (PLAYERS[id] != null)
			PLAYERS[id].shoot();
	}

	/**
	 * Démarre le mode bac à sable
	 * 
	 * @see Board#SANDBOX_THREAD
	 */
	public synchronized void startSandbox() {
		SANDBOX_THREAD.start();
	}
	
	/**
	 * Démarre le jeu
	 * 
	 * @see Board#LAVA_MOVER_THREAD
	 * @see Board#PLAYERS_HANDLER_THREAD
	 */
	public synchronized void startGame() {
		startSandbox();
		LAVA_MOVER_THREAD.start();
		PLAYERS_HANDLER_THREAD.start();
	}


	/**
	 * Réalise l'affichage du plateau de jeu
	 * 
	 * @see Board#MAP
	 * @see Board#PLAYERS
	 * @see Board#DEADS
	 * @see Board#BULLETS
	 */
	public synchronized void draw() {
		MAP.draw();

		// Dessin des joueurs
		for (Integer id : PLAYERS_IDS)
			if (PLAYERS[id] != null) PLAYERS[id].draw();

		// Dessin des morts
		for (Integer id : DEADS_IDS)
			if (DEADS[id] != null) DEADS[id].draw();

		// Dessin des balles
		for (Bullet b : BULLETS)
			b.draw();
	}
}
