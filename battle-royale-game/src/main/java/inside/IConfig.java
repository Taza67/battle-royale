package inside;

/**
 * Interface regroupant les constantes de la simulation du jeu
 * @author mourtaza
 *
 */
public interface IConfig {
	/**
	 * Dimensions du champ de bataille (carte), en pixels
	 */
	float MAP_WIDTH = 1280, MAP_HEIGHT = 720;

	/**
	 * Fréquence de la simulation (nombre de pas par seconde)
	 */
	int TICKS_PER_SECOND = 60;
	/**
	 * Durée d'un pas de simulation, en secondes
	 * @see IConfig#TICKS_PER_SECOND
	 */
	float TICK_DURATION = 1f / TICKS_PER_SECOND;

	/**
	 * Nombre maximum de joueurs dans une partie
	 */
	int MAX_PLAYERS = 100;
	/**
	 * Longueur maximale d'un pseudo
	 */
	int PSEUDO_MAX_LENGTH = 16;

	/**
	 * Points de vie maximum d'un joueur
	 */
	int MAX_LIFE_POINTS = 100;

	/**
	 * Dimensions de la représentation d'un joueur (longueur, largeur)
	 */
	float PLAYER_WIDTH = 20, PLAYER_HEIGHT = 20;
	/**
	 * Rayons de la représentation d'un joueur (rayon horizontal, rayon vertical)
	 */
	float PLAYER_RADIUS_X = PLAYER_WIDTH / 2,
		  PLAYER_RADIUS_Y = PLAYER_HEIGHT / 2;
	/**
	 * Vitesse maximale reçue dans une commande de déplacement
	 */
	int MAX_SPEED_LEVEL = 4;
	/**
	 * Durée de validité d'une commande de déplacement sans nouvelle commande, en pas de simulation (250 ms)
	 */
	int MOVE_INTENT_TICKS = TICKS_PER_SECOND / 4;
	/**
	 * Distance minimale entre deux joueurs à l'apparition
	 */
	float SPAWN_MIN_DISTANCE = 70;

	/**
	 * Dégâts d'un coup d'épée
	 */
	int MELEE_DAMAGE = 20;
	/**
	 * Durée pendant laquelle un coup d'épée peut toucher, en pas de simulation (150 ms)
	 */
	int MELEE_ACTIVE_TICKS = 9;
	/**
	 * Temps de recharge entre deux coups d'épée, en pas de simulation (450 ms)
	 */
	int MELEE_COOLDOWN_TICKS = 27;
	/**
	 * Portée d'un coup d'épée (distance maximale entre les centres des joueurs)
	 */
	float MELEE_RANGE = 40;
	/**
	 * Demi-angle d'ouverture d'un coup d'épée, en radians (65°)
	 */
	float MELEE_HALF_ANGLE = (float)Math.toRadians(65);

	/**
	 * Dégâts d'une balle
	 */
	int BULLET_DAMAGE = 10;
	/**
	 * Temps de recharge entre deux tirs, en pas de simulation (500 ms)
	 */
	int SHOOT_COOLDOWN_TICKS = 30;
	/**
	 * Vitesse d'une balle, en pixels par seconde
	 */
	float BULLET_SPEED = 540;
	/**
	 * Portée d'une balle, en pixels
	 */
	float BULLET_RANGE = 440;
	/**
	 * Rayon de la représentation d'une balle
	 */
	float BULLET_RADIUS = 3;
	/**
	 * Distance maximale parcourue par une balle entre deux tests de collision
	 */
	float BULLET_SUBSTEP = 4;

	/**
	 * Nombre de colonnes et de lignes de la grille de zones découpant la carte
	 */
	int AREAS_WIDTH = 10, AREAS_HEIGHT = 10;
	/**
	 * Dimensions d'une zone de la grille
	 * @see IConfig#AREAS_WIDTH
	 * @see IConfig#AREAS_HEIGHT
	 */
	float ONE_ZONE_WIDTH = MAP_WIDTH / AREAS_WIDTH, ONE_ZONE_HEIGHT = MAP_HEIGHT / AREAS_HEIGHT;

	/**
	 * Nombre d'obstacles générés par défaut
	 */
	int OBSTACLES_NUMBER = 40;
	/**
	 * Espace libre minimal entre deux obstacles
	 */
	float OBSTACLES_GAP = 26;

	/**
	 * Codes identifiant chaque direction (sens trigonométrique, axe Y vers le bas)
	 */
	int EAST = 0, NORTH_EAST = 1, NORTH = 2, NORTH_WEST = 3,
		WEST = 4, SOUTH_WEST = 5, SOUTH = 6, SOUTH_EAST = 7;
	/**
	 * Nombre de directions
	 */
	int DIRECTIONS_NUMBER = 8;

	/**
	 * Codes des formes d'attaque
	 */
	int ATTACK_MELEE = 1, ATTACK_SHOOT = 2;

	/**
	 * Dimensions d'un arbre (texture)
	 */
	int TREE_WIDTH = 32, TREE_HEIGHT = 52;
	/**
	 * Dimensions d'une forêt, en nombre d'arbres
	 */
	int FOREST_MIN_WIDTH = 1, FOREST_MIN_HEIGHT = 1,
		FOREST_MAX_WIDTH = 3, FOREST_MAX_HEIGHT = 2;
	/**
	 * Dimensions d'une parcelle d'eau (texture)
	 */
	int WATER_WIDTH = 20, WATER_HEIGHT = 7;
	/**
	 * Dimensions d'un lac, en nombre de parcelles
	 */
	int LAKE_MIN_WIDTH = 3, LAKE_MIN_HEIGHT = 3,
		LAKE_MAX_WIDTH = 6, LAKE_MAX_HEIGHT = 6;
	/**
	 * Dimensions d'un rocher (texture)
	 */
	int STONE_WIDTH = 38, STONE_HEIGHT = 28;
	/**
	 * Dimensions d'une montagne, en nombre de rochers
	 */
	int MOUNTAIN_MIN_WIDTH = 1, MOUNTAIN_MIN_HEIGHT = 1,
		MOUNTAIN_MAX_WIDTH = 2, MOUNTAIN_MAX_HEIGHT = 2;

	/**
	 * Numéros de textures
	 */
	int TEXTURE_FOREST = 0, TEXTURE_ROCK = 1, TEXTURE_WATER = 2, TEXTURE_BATTLEFIELD = 3,
		TEXTURE_FIRST_PLAYER = 4;
}
