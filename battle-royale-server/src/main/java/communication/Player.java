package communication;

import javax.websocket.Session;

/**
 * Classe représentant un joueur vis-à-vis du serveur web(socket)
 * @author mourtaza
 *
 */
public class Player {
	/**
	 * Taille maximale d'un pseudo de joueur
	 * @see Player#pseudo
	 */
	private final static int MAX_LENGTH_PSEUDO = 19;
	/**
	 * Identifiant associé à un joueur
	 */
	private final int ID;
	/**
	 * Pseudo associé au joueur
	 */
	private String pseudo;
	/**
	 * Session à partir de laquelle joue le joueur
	 */
	private volatile Session session;
	private volatile boolean isConnected;
	
	
	/**
	 * Construit une instance de joueur
	 * @param id Identifiant associé à ce joueur
	 * @param ps Pseudo associé à ce joueur
	 * @param s Session sur laquelle est connecté le joueur
	 * 
	 * @see Player#ID
	 * @see Player#init(String)
	 * @see Player#session
	 */
	public Player(int id, String ps, Session s) {
		ID = id;
		// Nommage du joueur
		init(ps);
		// Session
		session = s;
		
		System.out.println("- Création du joueur "+ ID);
	}
	
	
	/**
	 * Retourne l'identifiant du joueur
	 * @return Identifiant du joueur
	 * 
	 * @see Player#ID
	 */
	public int getID() { return ID; }
	/**
	 * Retourne le pseudo du joueur
	 * @return Pseudo du joueur
	 * 
	 * @see Player#pseudo
	 */
	public String getPseudo() { return pseudo; }
	/**
	 * Retourne la session associée au joueur
	 * @return Session du joueur
	 * 
	 * @see Player#session
	 */
	public synchronized Session getSession() { return session; }
	
	/**
	 * Change la valeur de la variable contenant la session du joueur
	 * @param s Nouvelle session
	 * 
	 * @see Player#session
	 */
	public synchronized void setSession(Session s) { 
		session = s; 
		isConnected = true;
	}
	
	
	/**
	 * Initialise le pseudo du joueur
	 * @param ps Pseudo entré
	 * 
	 * @see Player#MAX_LENGTH_PSEUDO
	 * @see Player#pseudo
	 */
	public void init(String ps) {
		if(ps.length() > 20)
			pseudo = ps.substring(0, MAX_LENGTH_PSEUDO);
		else if (ps.length() == 0)
			pseudo = Integer.toString(ID);
		else
			pseudo = ps;
	}
	
	/**
	 * Récupère la requête de déplacement envoyé par le joueur dans le tableau de données
	 * 
	 * @param direction Direction du déplacement
	 * @param speed Vitesse du déplacement
	 * 
	 * @see Player#ID
	 * @see Admin#MAX_PLAYERS
	 * @see Admin#checkPlayerMove(int)
	 * @see Admin#putPlayerMove(int, byte[])
	 */
	public void putMoveAction(byte direction, byte speed) {
		// Le joueur ne doit pas déjà avoir 
		// effectué un déplacement
		if (ID >= Admin.MAX_PLAYERS || Admin.checkPlayerMove(ID))
			return;
		
		// Traitement de l'action
		byte[] action = new byte[4];
		action[0] = 1;
		action[1] = direction;
		action[2] = speed;
		
		Admin.putPlayerMove(ID, action);
	}
	
	/**
	 * Récupère la requête d'attaque du joueur (type 1 -> épée, type 2 -> pistolet) dans le tableau de données
	 * @param type Type de l'attaque
	 * 
	 * @see Admin#MAX_PLAYERS
	 * @see Admin#checkPlayerAttack(int)
	 * @see Admin#putPlayerAttack(int, byte)
	 */
	public void putAttackAction(byte type) {
		// Le joueur ne doit pas déjà avoir
		// effectué une attaque
		if (ID >= Admin.MAX_PLAYERS || Admin.checkPlayerAttack(ID))
			return;
		
		Admin.putPlayerAttack(ID, type);
	}
	
	/**
	 * Retire la session du joueur
	 * 
	 * @see Player#session
	 */
	public synchronized void destroySession() {
		session = null;
		isConnected = false;
	}
	
	/**
	 * Retourne la classe sous forme d'un objet JSon
	 * @return Objet JSon représentant l'instance
	 * 
	 * @see Player#ID
	 * @see Player#pseudo
	 * @see Player#session
	 */
	public String getJSon() {
		String json = "{"
			+ "\"id\": " + ID + ","
			+ "\"pseudo\": \"" + pseudo + "\","
			+ "\"session\": " + session.getId() +
			"}";
		
		return json;
	}
}
