package communication;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.websocket.Session;

/**
 * Classe représentant l'administrateur du jeu
 * @author mourtaza
 *
 */
public class Admin {
	/**
	 * Variable de classe indiquant le nombre max de jouers
	 */
	public final static int MAX_PLAYERS = 50;
	/**
	 * Variable contenant le gestionnaire de communication
	 * @see TCommunicationHandler
	 */
	private TCommunicationHandler communicationHandler;
	/**
	 * Variable contenant un tableau de données représentant les déplacements des joueurs
	 * @see Admin#MAX_PLAYERS
	 */
	private final static byte[][] MOVES = new byte[MAX_PLAYERS][3];
	/**
	 * Variable contenant un tableau de données représentant les attaques des joueurs
	 * @see Admin#MAX_PLAYERS
	 */
	private final static byte[] ATTACKS = new byte[MAX_PLAYERS];
	
	/**
	 * Variable contenant un tableau représentant l'ensemble des joueurs
	 * @see Player
	 */
	private final static Player[] PLAYERS = new Player[MAX_PLAYERS];
	/**
	 * Variable contenant une hashmap associant les joueurs à leurs pseudos
	 * @see Player
	 */
	private final static Map<String, Player> CLIENTS = new HashMap<String, Player>();
	/**
	 * Variable contenant une liste des identifiants des joueurs
	 */
	private final static List<Integer> PLAYERS_IDS = new ArrayList<Integer>();
	/**
	 * Variable contenant le nombre d'instances de la classe Player
	 * @see Player
	 */
	private volatile static int playerInstances = 0;
	/**
	 * Variable indiquant la marche du jeu
	 */
	private volatile static boolean gameIsRunning = false;
	/**
	 * Variable indiquant la pause du jeu
	 */
	private volatile static boolean gameIsPaused = false;
	/**
	 * Variable indiquant l'arrêt du jeu
	 */
	private volatile static boolean gameIsStopped = false;
	/**
	 * Variable contenant la session associée à l'administrateur
	 */
	private volatile Session session;
	/**
	 * Verrou permettant de gérer de façon la lecture/écriture dans les tableaux d'actions
	 * @see Admin#MOVES
	 * @see Admin#ATTACKS
	 */
	private static ReentrantReadWriteLock lockActions = new ReentrantReadWriteLock();
	/**
	 * Verrou permettant de mettre en pause le jeu
	 */
	private Object pauseLock = new Object();
	
	/**
	 * Construit une instance de l'administrateur
	 * @param s Session associée à l'administrateur
	 * 
	 * @see Admin#session
	 * @see Admin#initArrays()
	 * @see Admin#communicationHandler
	 */
	public Admin(Session s) {
		session = s;
		// Initialisation des tableaux de données
		initArrays();
		// Création du gestionnaire de communication
		communicationHandler = new TCommunicationHandler(MOVES, ATTACKS, PLAYERS, PLAYERS_IDS, lockActions, pauseLock);
	}
	
	
	/**
	 * Retourne la session associée à l'administrateur
	 * @return Session de l'administrateur
	 * 
	 * @see Admin#session
	 */
	public synchronized Session getSession() { return session; }
	/**
	 * Retourne la valeur de l'indicateur de marche
	 * @return true si le jeu est en marche, false sinon
	 * 
	 * @see Admin#gameIsRunning
	 */
	public synchronized static boolean getGameIsRunning() { return gameIsRunning; }
	/**
	 * Retourne la valeur de l'indicateur de pause
	 * @return true si le jeu a été mis en pause, false sinon
	 * 
	 * @see Admin#gameIsPaused
	 */
	public synchronized static boolean getGameIsPaused() { return gameIsPaused; }
	/**
	 * Retourne la valeur de l'indicateur d'arrêt
	 * @return true si le jeu a été arrêté, false sinon
	 * 
	 * @see Admin#gameIsStopped
	 */
	public synchronized static boolean getGameIsStopped() { return gameIsStopped; }
	/**
	 * Retourne le nombre de joueurs
	 * @return Nombre de joueurs
	 * 
	 * @see Admin#playerInstances
	 */
	public synchronized static int getPlayerInstances() { return playerInstances; }
	public synchronized Player getClient(String name) { return CLIENTS.get(name); }
	
	
	/**
	 * Change la valeur de la variable indiquant le nombre de joueurs
	 * @param pi Nouveau nombre de joueurs
	 * 
	 * @see Admin#playerInstances
	 */
	public synchronized static void setPlayerInstances(int pi) { playerInstances = pi; }
	
	
	/**
	 * Démarre le jeu et l'indique à tous les jouers
	 * @return true (toujours)
	 * 
	 * @see Admin#gameIsRunning
	 * @see Admin#communicationHandler
	 * @see Admin#sendMessageToAll
	 */
	public synchronized boolean startGame() {
		gameIsRunning = true;
		communicationHandler.start();
		System.err.println("- Le jeu a démarré !");
		
		sendMessageToAll("start");
		
		return true;
	}
	
	/**
	 * Met en pause le jeu et l'indique à tous les joueurs
	 * @return true (toujours)
	 * 
	 * @see Admin#gameIsRunning
	 * @see Admin#gameIsPaused
	 * @see Admin#sendMessageToAll
	 */
	public synchronized boolean pauseGame() {
		gameIsRunning = false;
		gameIsPaused = true;
		System.err.println("- Le jeu a été mis en pause !");
		
		sendMessageToAll("pause");
		
		return true;
	}
	
	/**
	 * Reprend le jeu (normalement, le jeu doit avoir été mis en pause auparavant
	 * @return true (toujours)
	 * 
	 * @see Admin#pauseLock
	 * @see Admin#gameIsPaused
	 * @see Admin#gameIsRunning
	 * @see TCommunicationHandler#run()
	 * @see Admin#sendMessageToAll
	 */
	public synchronized boolean continueGame() {
		synchronized (pauseLock) {
			if (gameIsPaused) {
				gameIsPaused = false;
				gameIsRunning = true;
				pauseLock.notifyAll();
			}
		}
		System.err.println("- Le jeu a repris !");
		
		sendMessageToAll("continue");
		
		return true;
	}
	
	/**
	 * Arrête le jeu et l'indique aux joueurs
	 * @return true (toujours)
	 * 
	 * @see Admin#gameIsRunning
	 * @see Admin#gameIsStopped
	 * @see Admin#communicationHandler
	 */
	public synchronized boolean stopGame() {
		gameIsRunning = false;
		gameIsStopped = true;
		communicationHandler.interrupt();
		System.err.println("- Arrêt du jeu !");
		
		sendMessageToAll("stop");
		
		return true;
	}
	
	/**
	 * Initialise les tableaux de données représentant les actions des joueurs
	 * 
	 * @see Admin#lockActions
	 * @see Admin#MOVES
	 * @see Admin#ATTACKS
	 * @see Admin#MAX_PLAYERS
	 */
	public void initArrays() {
		
		lockActions.writeLock().lock();
		try {
			for (int i = 0; i < MAX_PLAYERS; i++) {
				MOVES[i][0] = 0;
				ATTACKS[i] = 0;
			}
		} finally {
			lockActions.writeLock().unlock();
		}
	}
	
	/**
	 * Vérifie si un client avec le pseudo donné existe déjà
	 * @param ps Pseudo entré par le client
	 * @return true si ce pseudo est déjà associé, false sinon
	 * 
	 * @see Admin#CLIENTS
	 */
	public synchronized boolean checkClientExistence(String ps) {
		return CLIENTS.containsKey(ps);
	}
	
	/**
	 * Réassocie un client avec la session donné
	 * @param ps Pseudo entré par le client/joueur
	 * @param s Session au client/joueur
	 * @return Instance du joueur associé au pseudo
	 * 
	 * @see Admin#CLIENTS
	 */
	public synchronized Player reassociateClient(String ps, Session s) {
		CLIENTS.get(ps).setSession(s);
		
		return CLIENTS.get(ps);
	}
	
	/**
	 * Crée et ajoute un nouveau joueur (associe le pseudo à ce joueur)
	 * @param ps Pseudo du joueur
	 * @param s Session associée à ce joueur
	 * @return Instance de la classe Player qui représente le joueur
	 * 
	 * @see Admin#playerInstances
	 * @see Player
	 * @see Admin#PLAYERS
	 * @see Admin#PLAYERS_IDS
	 * @see Admin#CLIENTS
	 */
	public synchronized Player addPlayer(String ps, Session s) {
		int id = playerInstances++;
		Player p = new Player(id, ps, s);
		
		PLAYERS[id] = p;
		PLAYERS_IDS.add(id);
		CLIENTS.put(ps, p);
		
		return p;
	}
	
	/**
	 * Vérifie le joueur associé à l'identifiant a déjà effectué une action de déplacement
	 * @param id Identifiant du joueur
	 * @return true si le joueur a déjà effectué un déplacement, false sinon
	 * 
	 * @see Admin#lockActions
	 * @see Admin#MOVES
	 */
	public static boolean checkPlayerMove(int id) {
		boolean ret;
		
		lockActions.readLock().lock();
		try {
			ret = MOVES[id][0] == 1;
		} finally {
			lockActions.readLock().unlock();
		}
		
		return ret;
	}
	
	/**
	 * Vérifie si le joueur associé à l'identifiant donné a déjà effectué une attaque
	 * @param id Identifiant du joueur
	 * @return true si le joueur a déjà effectué une attaque, false sinon
	 * 
	 * @see Admin#lockActions
	 * @see Admin#ATTACKS
	 */
	public static boolean checkPlayerAttack(int id) {
		boolean ret;
		
		lockActions.readLock().lock();
		try {
			ret = Admin.ATTACKS[id] > 0;
		} finally {
			lockActions.readLock().unlock();
		}
		
		return ret;
	}
	
	/**
	 * Ajoute le déplacement du joueur associé à l'identifiant donné
	 * @param id Identifiant du joueur
	 * @param move Tableau de byte représentant le déplacement (Vitesse, direction)
	 * 
	 * @see Admin#lockActions
	 * @see Admin#MOVES
	 */
	public static void putPlayerMove(int id, byte[] move) {
		lockActions.writeLock().lock();
		try {
			Admin.MOVES[id] = move;
		} finally {
			lockActions.writeLock().unlock();
		}
	}
	
	/**
	 * Ajoute l'attaque du joueur associé à l'identifiant donné
	 * @param id Identifiant du joueur
	 * @param attackType Type de l'attaque (1 : épée, 2 : pistolet)
	 * 
	 * @see Admin#lockActions
	 * @see Admin#ATTACKS
	 */
	public static void putPlayerAttack(int id, byte attackType) {
		
		lockActions.writeLock().lock();
		try {
			ATTACKS[id] = attackType;
		} finally {
			lockActions.writeLock().unlock();
		}
	}
	
	/**
	 * Supprime le joueur associé à l'identifiant donné
	 * @param id Identifiant du joueur
	 * 
	 * @see Admin#PLAYERS
	 * @see Admin#PLAYERS_IDS
	 */
	public static synchronized void deletePlayer(int id) {
		PLAYERS[id] = null;
		PLAYERS_IDS.remove((Integer)id);
	}
	
	/**
	 * Envoie un message au joueur associé à l'identifiant
	 * @param id Identifiant du joueur
	 * @param m Message à envoyer
	 * 
	 * @see Admin#PLAYERS_IDS
	 * @see Admin#PLAYERS
	 */
	public static synchronized void sendMessage(int id, String m) {
		if (PLAYERS_IDS.contains((Integer)id) && PLAYERS[id].getSession() != null)
			PLAYERS[id].getSession().getAsyncRemote().sendText(m);
	}
	
	/**
	 * Envoie un message à tous les joueurs
	 * @param m Message à envoyer
	 * 
	 * @see Admin#PLAYERS_IDS
	 * @see Admin#sendMessage(int, String)
	 */
	public static synchronized void sendMessageToAll(String m) {
		for (Integer id : PLAYERS_IDS)
			sendMessage(id, m);
	}
	
	
}
