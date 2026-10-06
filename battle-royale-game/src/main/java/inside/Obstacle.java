package inside;

import inside.geometry.Polygon;
import inside.geometry.Rectangle;
import inside.geometry.Vertice;
import outside.graphic.GraphicUtilities;

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
		 * Différents obstacles possibles
		 * @see IConfig
		 */
		ROCHER(TEXTURE_ROCK), FORET(TEXTURE_FOREST), EAU(TEXTURE_WATER);

		/**
		 * Variable contenant le numéro de texture associée à l'obstacle
		 */
		private final int TEXTURE_NUMBER;

		/**
		 * Construit une instance de type d'obstacle
		 * 
		 * @param texNum Numéro de texture du type d'obstacle
		 */
		private TypeObstacle(int texNum) { TEXTURE_NUMBER = texNum; }

		
		/**
		 * Retourne la variable contenant le numéro de texture
		 * @return Numéro de texture
		 */
		public int getTEXTURE_NUMBER() { return TEXTURE_NUMBER; }

		/**
		 * Retourne un type d'obstacle généré aléatoirement parmi ceux qui existent
		 * @return Type d'obstacle aléatoire
		 */
		public static TypeObstacle random() {
			return values() [(int) (Math.random() * values().length)];
		}
	}

	/**
	 * Variable contenant le type de l'obstacle
	 * @see TypeObstacle
	 */
	private final TypeObstacle TYPE;

	
	/**
	 * Construit une instance d'obtacle
	 * 
	 * @param m Carte du jeu
	 * @param t Type de l'obstacle
	 * @param p Position de l'obstacle
	 * 
	 * @see Element
	 * @see Obstacle#TYPE
	 */
	public Obstacle(Map m, TypeObstacle t, Vertice p) {
		super(m);
		TYPE = t;
		position = p;
		textureNumber = t.getTEXTURE_NUMBER();
		representation = Polygon.getObstacleRepresentation(this);
	}


	/**
	 * Retourn le type de l'obstacle
	 * @return Type de l'obstacle
	 * 
	 * @see Obstacle#TYPE
	 */
	public TypeObstacle getTYPE() { return TYPE; }
	
	
	/**
	 * Dessine l'obstacle
	 * 
	 * @see GraphicUtilities#drawRectangleTexture(Polygon, int)
	 */
	public void draw() {
		GraphicUtilities.drawRectangleTexture((Rectangle)representation, textureNumber);
	}
}
