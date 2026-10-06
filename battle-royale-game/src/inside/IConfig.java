package inside;

import outside.communication.TCommunicationHandler;
import outside.graphic.Color;

/**
 * Interface regroupant toutes les constantes de configuration du jeu
 * @author mourtaza
 *
 */
public interface IConfig {
	/**
	 * Couleurs utilisées pour l'interface graphique
	 * @see Color
	 */
	Color COLOR_PLAIN = new Color(187, 214, 184),
		  COLOR_LAVA = new Color(255, 102, 0),
		  COLOR_ROCK = new Color(157, 132, 92),
		  COLOR_FOREST = new Color(34, 139, 34),
		  COLOR_WATER = new Color(0, 119, 190),
		  COLOR_DEAD = new Color(0, 0, 0),
		  COLOR_WEAPON = new Color(100, 100, 100),
		  COLOR_BULLET = new Color(100, 100, 100),
		  COLOR_WHITE = new Color(256, 256, 256);

	/**
	 * Dimensions de la fenêtre
	 */
	int WINDOW_WIDTH = 1280, WINDOW_HEIGHT = 720;

	/**
	 * Dimensions de la représentation d'un joueur (longueur, largeur)
	 */
	float PLAYER_WIDTH = 20, PLAYER_HEIGHT = 20;
	/**
	 * Rayons de la représentation d'un joueur (rayon vertical, rayon horizontal)
	 * @see IConfig#PLAYER_HEIGHT
	 * @see IConfig#PLAYER_WIDTH
	 */
	float PLAYER_RADIUS_X = PLAYER_WIDTH / 2,
		  PLAYER_RADIUS_Y = PLAYER_HEIGHT / 2;

	/**
	 * Dimensions de la représentation d'une "épée" (longueur, largeur)
	 */
	float WEAPON_WIDTH = 15, WEAPON_HEIGHT = 5;

	/**
	 * Dimensions de la représentation d'un obstacle (longueur, largeur)
	 */
	float OBSTACLE_WIDTH = 40, OBSTACLE_HEIGHT = 40;
	/**
	 * Rayons de la représentation d'un obstacle (rayon vertical, rayon horizontal)
	 * @see IConfig#OBSTACLE_HEIGHT
	 * @see IConfig#OBSTACLE_WIDTH
	 */
	float OBSTACLE_RADIUS_X = OBSTACLE_WIDTH / 2,
		  OBSTACLE_RADIUS_Y = OBSTACLE_HEIGHT / 2;

	/**
	 * Dimensions du champ de bataille (carte) (longueur, largeur)
	 */
	float MAP_WIDTH = WINDOW_WIDTH, MAP_HEIGHT = WINDOW_HEIGHT;

	/**
	 * Dimensions d'une zone (longueur, largeur)
	 */
	float ONE_ZONE_WIDTH = MAP_WIDTH / 10, ONE_ZONE_HEIGHT = MAP_HEIGHT / 10;
	/**
	 * Dimensions du tableau de zones divisant la carte (longueur , largeur en cases)
	 * @see IConfig#MAP_HEIGHT
	 * @see IConfig#MAP_WIDTH
	 * @see IConfig#ONE_ZONE_HEIGHT
	 * @see IConfig#ONE_ZONE_WIDTH
	 */
	int AREAS_WIDTH = (int)(MAP_WIDTH / ONE_ZONE_WIDTH),
		AREAS_HEIGHT = (int)(MAP_HEIGHT / ONE_ZONE_HEIGHT);

	/**
	 * Dimensions de la représentation d'une "balle" (longueur, largeur)
	 */
	float BULLET_WIDTH = 3, BULLET_HEIGHT = 3;
	/**
	 * Rayons de la représentation d'une "balle" (rayon vertical, rayon horizontal)
	 * @see IConfig#BULLET_WIDTH
	 * @see IConfig#BULLET_HEIGHT
	 */
	float BULLET_RADIUS_X = BULLET_WIDTH / 2,
		  BULLET_RADIUS_Y = BULLET_HEIGHT / 2;

	/**
	 * Distance de chaque pas d'une balle lors de son déplacement
	 */
	int BULLET_STEP_DISTANCE = 20;
	/**
	 * Nombre de pas à réaliser par une balle
	 */
	int BULLET_STEPS_TO_MAKE = 3;

	/**
	 * Nombre de joueurs à générer
	 */
	int PLAYERS_NUMBER = 100;
	/**
	 * Nombre d'obstacles à générer
	 */
	int OBSTACLES_NUMBER = 60;
	/**
	 * Nombre de sols à générer
	 */
	int LANDS_NUMBER = 10;

	/**
	 * Codes identifiant chaque direction
	 */
	int EAST = 0, NORTH_EAST = 1, NORTH = 2, NORTH_WEST = 3,
		WEST = 4, SOUTH_WEST = 5, SOUTH = 6, SOUTH_EAST = 7;


	/**
	 * Constantes à appeler pour effectuer des rotations d'un certain degré
	 */
	float DEGREES_45 = (float)(Math.PI / 4f),
		  DEGREES_90 = (float)(Math.PI / 2f),
		  DEGREES_30 = (float)(Math.PI / 6f),
		  DEGREES_60 = (float)(Math.PI / 3f);

	
	/**
	 * Vitesse de déplacement de la lave
	 * @see TLavaMovement
	 */
	int LAVA_MOVEMENT_SPEED = 40;
	/**
	 * Temps d'attente avant le début du mouvement de la lave (première vague)
	 * @see TLavaMovement
	 */
	int TIME_BEFORE_LAVA_MOVEMENT_START = 1000;
	/**
	 * Temps d'attente entre chaque vague
	 * @see TLavaMovement
	 */
	int TIME_BETWEEN_LAVA_WAVES = 500;
	/**
	 * Temps d'une vague
	 * @see TLavaMovement
	 */
	int LAVA_WAVE_TIME = 250;

	/**
	 * Vitesse de déplacement d'un joueur (par défaut)
	 */
	int PLAYER_SPEED = 5;
	/**
	 * Temps d'attente entre chaque checkup réalise par le thread de gestion de vie des jouers
	 * @see TPlayersHandler
	 */
	int PLAYERS_CHECKING = 100;

	/**
	 * Durée du mode bac à sable
	 * @see TSandbox
	 */
	int LOBBY_TIME = 1000;

	/**
	 * Points de vie d'un joueur
	 */
	int LIFE_POINTS = 50;

	/**
	 * Points de dégâts de la lave
	 */
	int LAVA_DAMAGE = 1;
	/**
	 * Points de dégâts d'un coup d'épée
	 */
	int WEAPON_DAMAGE = 5;
	/**
	 * Points de dégâts d'une balle
	 */
	int BULLET_DAMAGE = 2;
	
	
	/**
	 * Nombre d'itérations dans le mode sandbox
	 * @see TSandbox
	 */
	int SANDBOX_DELAY = 100000;
	
	/**
	 * Temps d'attente entre chaque recéption de données
	 * @see TCommunicationHandler
	 */
	int TIME_BETWEEN_RECEPTIONS = 50;
	
	
	/**
	 * Dimensions d'un arbre (texture)
	 */
	int TREE_WIDTH = 32, TREE_HEIGHT = 52;
	
	/**
	 * Dimensions d'une forêt (texture)
	 */
	int FOREST_MIN_WIDTH = 1, FOREST_MIN_HEIGHT = 1,
		FOREST_MAX_WIDTH = 3, FOREST_MAX_HEIGHT = 2;
	
	/**
	 * Dimensions d'une parcelle d'eau (texture)
	 */
	int WATER_WIDTH = 20, WATER_HEIGHT = 7;
	
	/**
	 * Dimensions d'un lac (texture)
	 */
	int LAKE_MIN_WIDTH = 3, LAKE_MIN_HEIGHT = 1,
		LAKE_MAX_WIDTH = 4, LAKE_MAX_HEIGHT = 3;
	
	/**
	 * Dimensions d'un rocher (texture)
	 */
	int STONE_WIDTH = 38, STONE_HEIGHT = 28;
	
	/**
	 * Dimensions d'une montagne (texture)
	 */
	int MOUNTAIN_MIN_WIDTH = 1, MOUNTAIN_MIN_HEIGHT = 1,
		MOUNTAIN_MAX_WIDTH = 2, MOUNTAIN_MAX_HEIGHT = 1;
	
	/**
	 * Numéros de textures
	 */
	int TEXTURE_FOREST = 0, TEXTURE_ROCK = 1, TEXTURE_WATER = 2, TEXTURE_BATTLEFIELD = 3,
		TEXTURE_TRAIL = 4, TEXTURE_BUSH = 5, TEXTURE_FLOWERS = 6;
	
	/**
	 * Dimensions d'une parcelle de fleurs
	 */
	int FLOWER_WIDTH = 10, FLOWER_HEIGHT = 10;
	
	/**
	 * Dimensions d'un ensemble de fleurs
	 */
	int FLOWERS_MIN_WIDTH = 1, FLOWERS_MIN_HEIGHT = 1,
		FLOWERS_MAX_WIDTH = 5, FLOWERS_MAX_HEIGHT = 5;
	
	/**
	 * Dimensions d'une parcelle de sentier
	 */
	int TRAIL_WIDTH = 10, TRAIL_HEIGHT = 10;
	
	/**
	 * Dimensions d'un ensemble de sentiers (route)
	 */
	int ROAD_MIN_WIDTH = 1, ROAD_MIN_HEIGHT = 1,
		ROAD_MAX_WIDTH = 5, ROAD_MAX_HEIGHT = 1;
	
	/**
	 * Dimensions d'une parcelle de buisson
	 */
	int BUSH_WIDTH = 10, BUSH_HEIGHT = 10;
	
	/**
	 * Dimensions d'un ensemble de buissons
	 */
	int BUSHES_MIN_WIDTH = 1, BUSHES_MIN_HEIGHT = 1,
		BUSHES_MAX_WIDTH = 5, BUSHES_MAX_HEIGHT = 5;
}
