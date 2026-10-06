package inside;

import java.util.List;

import inside.geometry.Polygon;
import inside.geometry.Rectangle;
import inside.geometry.Vertice;
import outside.graphic.Color;
import outside.graphic.GraphicUtilities;

/**
 * Classe représentant un élément sur le plateau de jeu
 * @author mourtaza
 *
 * @see IConfig
 */
public abstract class Element implements IConfig {
	/**
	 * Variable contenant la carte du jeu
	 * @see Map
	 */
	protected final Map MAP;
	/**
	 * Variable contenant la position de l'élément sur la carte
	 * @see Vertice
	 */
	protected volatile Vertice position;
	/**
	 * Variable contenant une représentation de l'élément
	 * @see Polygon
	 */
	protected volatile Polygon representation;
	/**
	 * Variable contenant la couleur de la représentation de l'élément
	 * @see Color
	 */
	protected Color color;
	/**
	 * Variable contenant le numéro de texture de la représentation de l'élément
	 * @see Color
	 */
	protected int textureNumber;
	/**
	 * Variable contenant la zone dans laquelle est l'élément
	 * @see Zone
	 */
	protected volatile Zone zone;


	/**
	 * Construit une instance d'élément
	 * @param map Carte du jeu
	 * 
	 * @see Element#MAP
	 */
	public Element(Map map) {
		MAP = map;
	}


	/**
	 * Retourne la représenation de l'élément
	 * @return Représentation de l'élément
	 * 
	 * @see Element#representation
	 */
	public Polygon getRepresentation() { return representation; }
	/**
	 * Retourne la zone dans lequel est l'élément
	 * @return Zone de l'élément
	 * 
	 * @see Element#zone
	 */
	public Zone getZone() { return zone; }
	/**
	 * Retourne la position de l'élément
	 * @return Position de l'élément
	 * 
	 * @see Element#position
	 */
	public Vertice getPosition() { return position; }

	/**
	 * Change la zone dans lequel est l'élément
	 * @param z Nouvelle zone
	 * 
	 * @see Element#zone
	 */
	public void setZone(Zone z) { zone = z; }


	/**
	 * Dessine l'élément
	 * 
	 * @see GraphicUtilities#drawPolygon(Polygon, Color)
	 */
	public void draw() {
		GraphicUtilities.drawPolygon(representation, color);
	}

	/**
	 * Renvoie une position de joueur qui ne le superposerai pas sur un autre joueur ou un autre obstacle
	 * @param obs Liste d'obstacles
	 * @param playersIds Liste des identifiants de joueurs
	 * @param players Tableau contenant les joueurs
	 * @param radiusX Rayon horizontal de la représentation de l'élément
	 * @param radiusY Rayon Vertical de la représentation de l'élément
	 * @return Position vide
	 * 
	 * @see Rectangle
	 * @see Rectangle#intersect(Rectangle)
	 * @see IConfig#MAP_WIDTH
	 * @see IConfig#MAP_HEIGHT
	 */
	public static Vertice getFreePosition(List<Obstacle> obs, List<Integer> playersIds, Player[] players, float radiusX, float radiusY) {
		boolean intersecting = false;
		Vertice randomPosition;
		float researchXUp = MAP_WIDTH - radiusX,
			  researchYUp = MAP_HEIGHT - radiusY;
		float elementWidth = radiusX * 2,
			  elementHeight = radiusY * 2;


		freePositionResearch : do {
			// Position aléatoire
			randomPosition = Vertice.random(
				radiusX, researchXUp,
				radiusY, researchYUp
			);

			// Rectangle centré sur cette position
			Rectangle rep = new Rectangle(
				randomPosition,
				randomPosition.substract(new Vertice(radiusX, radiusY)),
				elementWidth, elementHeight
			);

			// Recherche d'intersection
			intersecting = false;
			// // Avec les obstacles
			for (Element oneObs : obs) {
				intersecting = ((Rectangle)oneObs.representation)
				.intersect(rep);
				// S'il y a intersection, on saute à la prochaine itération
				if (intersecting)
					continue freePositionResearch;
			}
			// // Avec les autres joueurs
			for (Integer onePlaId: playersIds) {
				intersecting = ((Rectangle)players[onePlaId].representation)
				.intersect(rep);
				// S'il y a intersection, on saute à la prochaine itération
				if (intersecting)
					continue freePositionResearch;
			}
		} while(intersecting);

		return randomPosition;
	}
}
