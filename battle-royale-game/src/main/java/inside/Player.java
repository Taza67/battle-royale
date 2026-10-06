package inside;

/**
 * Classe représentant un joueur sur le plateau de jeu
 * @author mourtaza
 *
 * @see Element
 */
public class Player extends Element {
	/**
	 * Vitesses de déplacement associées aux niveaux de vitesse 0 à 4, en pixels par seconde
	 */
	private static final float[] SPEEDS = { 0, 70, 105, 140, 175 };

	/**
	 * Identifiant du joueur
	 */
	private final int ID;
	/**
	 * Pseudo du joueur
	 */
	private final String PSEUDO;
	/**
	 * Indique si le joueur est contrôlé par l'ordinateur
	 */
	private final boolean BOT;
	/**
	 * Équipement du joueur
	 */
	private final Weapon WEAPON;
	/**
	 * Points de vie du joueur
	 */
	private int lifePoints;
	/**
	 * Indique si le joueur est en vie
	 */
	private boolean isAlive;
	/**
	 * Direction du regard du joueur
	 */
	private int viewDirection;
	/**
	 * Direction du déplacement demandé
	 */
	private int moveDirection;
	/**
	 * Niveau de vitesse du déplacement demandé (0 = arrêt)
	 */
	private int moveSpeed;
	/**
	 * Pas de simulation à partir duquel le déplacement demandé expire
	 */
	private long moveExpiresAt;
	/**
	 * Indique si le joueur s'est effectivement déplacé au dernier pas
	 */
	private boolean moving;
	/**
	 * Nombre de joueurs éliminés par ce joueur
	 */
	private int kills;
	/**
	 * Identifiant du dernier joueur ayant blessé ce joueur (-1 si aucun)
	 */
	private int lastAttacker = -1;
	/**
	 * Origine des derniers dégâts subis
	 */
	private DamageCause lastDamageCause;
	/**
	 * Pas de simulation des derniers dégâts subis (-1 si aucun)
	 */
	private long lastHitTick = -1;
	/**
	 * Dégâts de lave accumulés mais pas encore appliqués (fraction de point de vie)
	 */
	private float lavaDebt;
	/**
	 * Indique si le joueur était dans la lave au dernier pas
	 */
	private boolean inLava;
	/**
	 * Classement final du joueur (0 tant que le joueur est en vie)
	 */
	private int rank;
	/**
	 * Ordre d'élimination (1 pour le premier éliminé, 0 tant que le joueur est en vie)
	 */
	private int eliminationOrder;
	/**
	 * Pas de simulation de l'élimination (-1 tant que le joueur est en vie)
	 */
	private long eliminationTick = -1;
	/**
	 * Zone de la grille dans laquelle est référencé le joueur
	 */
	private Zone zone;


	/**
	 * Construit un joueur
	 * @param id Identifiant
	 * @param pseudo Pseudo
	 * @param bot true si le joueur est contrôlé par l'ordinateur
	 * @param x Abscisse initiale
	 * @param y Ordonnée initiale
	 */
	public Player(int id, String pseudo, boolean bot, float x, float y) {
		super(x, y, PLAYER_RADIUS_X, PLAYER_RADIUS_Y);
		ID = id;
		PSEUDO = pseudo;
		BOT = bot;
		WEAPON = new Weapon();
		lifePoints = MAX_LIFE_POINTS;
		isAlive = true;
		viewDirection = (x > MAP_WIDTH / 2f) ? WEST : EAST;
		moveDirection = viewDirection;
	}


	/**
	 * Retourne la vitesse en pixels par seconde associée à un niveau de vitesse
	 * @param speed Niveau de vitesse (borné entre 0 et 4)
	 * @return Vitesse en pixels par seconde
	 */
	public static float pixelsPerSecond(int speed) {
		return SPEEDS[Math.max(0, Math.min(MAX_SPEED_LEVEL, speed))];
	}

	/**
	 * Retourne l'identifiant du joueur
	 * @return Identifiant
	 */
	public int getID() { return ID; }
	/**
	 * Retourne le pseudo du joueur
	 * @return Pseudo
	 */
	public String getPseudo() { return PSEUDO; }
	/**
	 * Indique si le joueur est contrôlé par l'ordinateur
	 * @return true pour un bot
	 */
	public boolean isBot() { return BOT; }
	/**
	 * Retourne l'équipement du joueur
	 * @return Équipement
	 */
	public Weapon getWeapon() { return WEAPON; }
	/**
	 * Retourne les points de vie du joueur
	 * @return Points de vie
	 */
	public int getLifePoints() { return lifePoints; }
	/**
	 * Indique si le joueur est en vie
	 * @return true si le joueur est en vie
	 */
	public boolean getIsAlive() { return isAlive; }
	/**
	 * Retourne la direction du regard
	 * @return Direction (0 à 7)
	 */
	public int getViewDirection() { return viewDirection; }
	/**
	 * Retourne la direction du déplacement demandé
	 * @return Direction (0 à 7)
	 */
	public int getMoveDirection() { return moveDirection; }
	/**
	 * Retourne le niveau de vitesse demandé
	 * @return Niveau de vitesse (0 à 4)
	 */
	public int getMoveSpeed() { return moveSpeed; }
	/**
	 * Indique si le joueur s'est déplacé au dernier pas de simulation
	 * @return true si le joueur bouge
	 */
	public boolean isMoving() { return moving; }
	/**
	 * Retourne le nombre d'éliminations
	 * @return Nombre d'éliminations
	 */
	public int getKills() { return kills; }
	/**
	 * Retourne l'identifiant du dernier attaquant
	 * @return Identifiant, ou -1
	 */
	public int getLastAttacker() { return lastAttacker; }
	/**
	 * Retourne l'origine des derniers dégâts subis
	 * @return Origine, ou null
	 */
	public DamageCause getLastDamageCause() { return lastDamageCause; }
	/**
	 * Retourne le pas de simulation des derniers dégâts subis
	 * @return Pas de simulation, ou -1
	 */
	public long getLastHitTick() { return lastHitTick; }
	/**
	 * Indique si le joueur était dans la lave au dernier pas
	 * @return true si le joueur brûle
	 */
	public boolean isInLava() { return inLava; }
	/**
	 * Retourne le classement final
	 * @return Classement, 0 tant que le joueur est en vie
	 */
	public int getRank() { return rank; }
	/**
	 * Retourne l'ordre d'élimination
	 * @return Ordre, 0 tant que le joueur est en vie
	 */
	public int getEliminationOrder() { return eliminationOrder; }
	/**
	 * Retourne le pas de simulation de l'élimination
	 * @return Pas de simulation, ou -1
	 */
	public long getEliminationTick() { return eliminationTick; }
	/**
	 * Retourne la zone de la grille du joueur
	 * @return Zone
	 */
	public Zone getZone() { return zone; }

	/**
	 * Change la zone de la grille du joueur
	 * @param z Nouvelle zone
	 */
	void setZone(Zone z) { zone = z; }
	/**
	 * Change le classement final
	 * @param r Classement
	 */
	void setRank(int r) { rank = r; }
	/**
	 * Indique si le joueur est dans la lave
	 * @param v true si le joueur brûle
	 */
	void setInLava(boolean v) { inLava = v; }
	/**
	 * Indique si le joueur s'est déplacé
	 * @param v true si le joueur bouge
	 */
	void setMoving(boolean v) { moving = v; }


	/**
	 * Enregistre une intention de déplacement
	 * @param direction Direction (0 à 7)
	 * @param speed Niveau de vitesse (0 = arrêt)
	 * @param now Pas de simulation courant
	 */
	public void setMoveIntent(int direction, int speed, long now) {
		if (!isAlive || !Direction.isValid(direction)) return;
		moveSpeed = Math.max(0, Math.min(MAX_SPEED_LEVEL, speed));
		if (moveSpeed > 0) {
			moveDirection = direction;
			viewDirection = direction;
		}
		moveExpiresAt = now + MOVE_INTENT_TICKS;
	}

	/**
	 * Arrête le déplacement en cours
	 */
	public void stop() {
		moveSpeed = 0;
	}

	/**
	 * Retourne la vitesse effective au pas donné (0 si l'intention a expiré)
	 * @param now Pas de simulation courant
	 * @return Vitesse en pixels par seconde
	 */
	public float currentSpeed(long now) {
		if (!isAlive || moveSpeed == 0 || now >= moveExpiresAt) return 0;
		return pixelsPerSecond(moveSpeed);
	}

	/**
	 * Place le joueur à une nouvelle position
	 * @param nx Nouvelle abscisse
	 * @param ny Nouvelle ordonnée
	 */
	void setPosition(float nx, float ny) {
		x = nx;
		y = ny;
	}

	/**
	 * Inflige des dégâts au joueur
	 * @param amount Points de vie retirés
	 * @param attackerId Identifiant de l'attaquant (-1 pour la lave)
	 * @param cause Origine des dégâts
	 * @param now Pas de simulation courant
	 * @return Points de vie réellement retirés
	 */
	public int reduceLifePoints(int amount, int attackerId, DamageCause cause, long now) {
		if (!isAlive || lifePoints <= 0 || amount <= 0) return 0;

		int applied = Math.min(amount, lifePoints);
		lifePoints -= applied;
		lastDamageCause = cause;
		lastHitTick = now;
		if (attackerId >= 0 && attackerId != ID) lastAttacker = attackerId;
		return applied;
	}

	/**
	 * Ajoute des dégâts de lave et retourne la partie entière à appliquer
	 * @param amount Dégâts (fractionnaires) accumulés pendant le pas
	 * @return Points de vie entiers à retirer
	 */
	int accumulateLava(float amount) {
		lavaDebt += amount;
		int whole = (int)lavaDebt;
		lavaDebt -= whole;
		return whole;
	}

	/**
	 * Indique si le joueur doit être éliminé à la fin du pas
	 * @return true si le joueur est en vie mais n'a plus de points de vie
	 */
	public boolean isDying() {
		return isAlive && lifePoints <= 0;
	}

	/**
	 * Ajoute une élimination au compteur du joueur
	 */
	void addKill() {
		kills++;
	}

	/**
	 * Élimine le joueur
	 * @param order Ordre d'élimination
	 * @param finalRank Classement final
	 * @param now Pas de simulation courant
	 */
	void kill(int order, int finalRank, long now) {
		isAlive = false;
		lifePoints = 0;
		moveSpeed = 0;
		moving = false;
		inLava = false;
		eliminationOrder = order;
		rank = finalRank;
		eliminationTick = now;
		WEAPON.cancelSwing();
	}

	@Override
	public String toString() {
		return "Joueur N°" + ID + " (" + PSEUDO + ") - Position (" + x + ", " + y + ") - Vie " + lifePoints;
	}
}
