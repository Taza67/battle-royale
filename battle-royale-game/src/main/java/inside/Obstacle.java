package inside;

import static inside.IConfig.*;
import java.util.Random;

import inside.geometry.Rectangle;

/**
 * Classe représentant un obstacle sur le plateau de jeu
 * @author mourtaza
 *
 * @see Element
 */
public class Obstacle extends Element {
	/**
	 * Énumération représentant les types d'obstacle possibles
	 * @author mourtaza
	 *
	 */
	public enum TypeObstacle {
		/**
		 * Différents obstacles possibles : tous bloquent les joueurs, l'eau laisse passer les balles
		 * @see IConfig
		 */
		ROCHER(TEXTURE_ROCK, true), FORET(TEXTURE_FOREST, true), EAU(TEXTURE_WATER, false);

		/**
		 * Numéro de texture associée à l'obstacle
		 */
		private final int textureNumber;
		/**
		 * Indique si l'obstacle arrête les balles
		 */
		private final boolean blocksBullets;

		/**
		 * Construit une instance de type d'obstacle
		 * @param texNum Numéro de texture du type d'obstacle
		 * @param blocksBullets true si l'obstacle arrête les balles
		 */
		TypeObstacle(int texNum, boolean blocksBullets) {
			textureNumber = texNum;
			this.blocksBullets = blocksBullets;
		}

		/**
		 * Retourne le numéro de texture
		 * @return Numéro de texture
		 */
		public int getTEXTURE_NUMBER() { return textureNumber; }

		/**
		 * Indique si l'obstacle arrête les balles
		 * @return true si les balles sont détruites à son contact
		 */
		public boolean blocksBullets() { return blocksBullets; }

		/**
		 * Retourne un type d'obstacle aléatoire
		 * @param random Générateur aléatoire
		 * @return Type d'obstacle aléatoire
		 */
		public static TypeObstacle random(Random random) {
			return values()[random.nextInt(values().length)];
		}
	}

	/**
	 * Type de l'obstacle
	 * @see TypeObstacle
	 */
	private final TypeObstacle type;


	/**
	 * Construit un obstacle
	 * @param t Type de l'obstacle
	 * @param x Abscisse du centre
	 * @param y Ordonnée du centre
	 * @param radiusX Rayon horizontal
	 * @param radiusY Rayon vertical
	 */
	public Obstacle(TypeObstacle t, float x, float y, float radiusX, float radiusY) {
		super(x, y, radiusX, radiusY);
		type = t;
	}

	/**
	 * Construit un obstacle occupant un rectangle
	 * @param t Type de l'obstacle
	 * @param r Rectangle occupé
	 */
	public Obstacle(TypeObstacle t, Rectangle r) {
		this(t, r.getCenterX(), r.getCenterY(), r.getWidth() / 2f, r.getHeight() / 2f);
	}

	/**
	 * Génère un obstacle aléatoire dont les dimensions sont des multiples de sa texture
	 * @param t Type de l'obstacle
	 * @param random Générateur aléatoire
	 * @param x Abscisse du centre
	 * @param y Ordonnée du centre
	 * @return Nouvel obstacle
	 */
	public static Obstacle random(TypeObstacle t, Random random, float x, float y) {
		float w, h;

		switch (t) {
		case EAU:
			w = WATER_WIDTH * between(random, LAKE_MIN_WIDTH, LAKE_MAX_WIDTH);
			h = WATER_HEIGHT * between(random, LAKE_MIN_HEIGHT, LAKE_MAX_HEIGHT);
			break;
		case FORET:
			w = TREE_WIDTH * between(random, FOREST_MIN_WIDTH, FOREST_MAX_WIDTH);
			h = TREE_HEIGHT * between(random, FOREST_MIN_HEIGHT, FOREST_MAX_HEIGHT);
			break;
		case ROCHER:
		default:
			w = STONE_WIDTH * between(random, MOUNTAIN_MIN_WIDTH, MOUNTAIN_MAX_WIDTH);
			h = STONE_HEIGHT * between(random, MOUNTAIN_MIN_HEIGHT, MOUNTAIN_MAX_HEIGHT);
		}

		return new Obstacle(t, x, y, w / 2f, h / 2f);
	}

	/**
	 * Retourne un entier aléatoire entre deux bornes incluses
	 * @param random Générateur aléatoire
	 * @param min Borne inférieure
	 * @param max Borne supérieure
	 * @return Entier aléatoire
	 */
	private static int between(Random random, int min, int max) {
		return min + random.nextInt(max - min + 1);
	}


	/**
	 * Retourne le type de l'obstacle
	 * @return Type de l'obstacle
	 */
	public TypeObstacle getTYPE() { return type; }

	/**
	 * Retourne le numéro de texture de l'obstacle
	 * @return Numéro de texture
	 */
	public int getTextureNumber() { return type.getTEXTURE_NUMBER(); }
}
