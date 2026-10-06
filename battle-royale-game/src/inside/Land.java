package inside;

import inside.Obstacle.TypeObstacle;
import inside.geometry.Polygon;
import inside.geometry.Rectangle;
import inside.geometry.Vertice;
import outside.graphic.GraphicUtilities;

/**
 * Classe représentant un sol sur le plateau de jeu
 * @author mourtaza
 *
 * @see Element
 */
public class Land extends Element {
	/**
	 * Énumération représentant les types de sol possibles
	 * @author mourtaza
	 *
	 */
	public enum TypeLand {
		/**
		 * Différents sols possibles
		 * @see IConfig
		 */
		TRAIL(TEXTURE_TRAIL), BUSH(TEXTURE_BUSH), FLOWERS(TEXTURE_FLOWERS);

		/**
		 * Variable contenant le numéro de texture associée au sol
		 */
		private final int TEXTURE_NUMBER;

		/**
		 * Construit une instance de type de sol
		 * 
		 * @param texNum Numéro de texture du type de sol
		 */
		private TypeLand(int texNum) { TEXTURE_NUMBER = texNum; }

		/**
		 * Retourne la variable contenant le numéro de texture
		 * @return Numéro de texture
		 */
		public int getTEXTURE_NUMBER() { return TEXTURE_NUMBER; }

		/**
		 * Retourne un type de sol généré aléatoirement parmi ceux qui existent
		 * @return Type de sol aléatoire
		 */
		public static TypeLand random() {
			return values() [(int) (Math.random() * values().length)];
		}
	}
	
	/**
	 * Variable contenant le type du sol
	 * @see TypeObstacle
	 */
	private final TypeLand TYPE;
	
	
	/**
	 * Construit une instance de sol
	 * 
	 * @param map Carte du jeu
	 * @param t Type du sol
	 * @param p Position du sol
	 */
	public Land(Map map, TypeLand t, Vertice p) {
		super(map);
		TYPE = t;
		position = p;
		textureNumber = t.getTEXTURE_NUMBER();
		representation = Polygon.getLandRepresentation(this);
	}

	
	/**
	 * Retourn le type du sol
	 * @return Type du sol
	 * 
	 * @see Land#TYPE
	 */
	public TypeLand getTYPE() { return TYPE; }
	
	
	/**
	 * Dessine le sol
	 * 
	 * @see GraphicUtilities#drawRectangleTexture(Polygon, int)
	 */
	public void draw() {
		GraphicUtilities.drawRectangleTexture((Rectangle)representation, textureNumber);
	}
}
