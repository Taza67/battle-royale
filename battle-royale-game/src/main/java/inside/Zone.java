package inside;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import inside.geometry.Rectangle;

/**
 * Classe représentant une case de la grille spatiale découpant la carte.
 * Elle référence les obstacles qui la chevauchent et les joueurs vivants dont le centre s'y trouve.
 * @author mourtaza
 *
 * @see Map
 */
public class Zone implements IConfig {
	/**
	 * Position de la zone dans la grille (colonne, ligne)
	 */
	private final int X, Y;
	/**
	 * Rectangle occupé par la zone
	 */
	private final Rectangle REPRESENTATION;
	/**
	 * Obstacles chevauchant la zone
	 */
	private final List<Obstacle> OBSTACLES;
	/**
	 * Joueurs vivants présents dans la zone
	 */
	private final List<Player> PLAYERS;


	/**
	 * Construit une zone de la grille
	 * @param x Colonne
	 * @param y Ligne
	 */
	public Zone(int x, int y) {
		X = x;
		Y = y;
		REPRESENTATION = new Rectangle(x * ONE_ZONE_WIDTH, y * ONE_ZONE_HEIGHT,
			(x + 1) * ONE_ZONE_WIDTH, (y + 1) * ONE_ZONE_HEIGHT);
		OBSTACLES = new ArrayList<>();
		PLAYERS = new ArrayList<>();
	}


	/**
	 * Retourne la colonne de la zone
	 * @return Colonne
	 */
	public int getX() { return X; }
	/**
	 * Retourne la ligne de la zone
	 * @return Ligne
	 */
	public int getY() { return Y; }
	/**
	 * Retourne le rectangle occupé par la zone
	 * @return Rectangle
	 */
	public Rectangle getRepresentation() { return REPRESENTATION; }
	/**
	 * Retourne les obstacles chevauchant la zone
	 * @return Liste non modifiable
	 */
	public List<Obstacle> getOBSTACLES() { return Collections.unmodifiableList(OBSTACLES); }
	/**
	 * Retourne les joueurs présents dans la zone
	 * @return Liste non modifiable
	 */
	public List<Player> getPLAYERS() { return Collections.unmodifiableList(PLAYERS); }


	/**
	 * Ajoute un obstacle à la zone
	 * @param o Obstacle
	 */
	void addObstacle(Obstacle o) {
		OBSTACLES.add(o);
	}

	/**
	 * Ajoute un joueur à la zone
	 * @param p Joueur
	 */
	void addPlayer(Player p) {
		if (!PLAYERS.contains(p)) PLAYERS.add(p);
	}

	/**
	 * Retire un joueur de la zone
	 * @param p Joueur
	 */
	void deletePlayer(Player p) {
		PLAYERS.remove(p);
	}

	@Override
	public String toString() {
		return "(" + X + ", " + Y + ") - Nombre de joueurs dans la zone = " + PLAYERS.size();
	}
}
