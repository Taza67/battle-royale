package inside;

import static inside.IConfig.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import inside.geometry.Rectangle;

/**
 * Classe représentant une case de la grille spatiale découpant la carte.
 * Elle référence les obstacles qui la chevauchent et les joueurs vivants dont le centre s'y trouve.
 * @author mourtaza
 *
 * @see GameMap
 */
public class GridCell {
	/**
	 * Position de la zone dans la grille (colonne, ligne)
	 */
	private final int column, row;
	/**
	 * Rectangle occupé par la zone
	 */
	private final Rectangle representation;
	/**
	 * Obstacles chevauchant la zone
	 */
	private final List<Obstacle> obstacles;
	/**
	 * Joueurs vivants présents dans la zone
	 */
	private final List<Player> players;


	/**
	 * Construit une case de la grille
	 * @param column Colonne
	 * @param row Ligne
	 */
	public GridCell(int column, int row) {
		this.column = column;
		this.row = row;
		representation = new Rectangle(column * ONE_ZONE_WIDTH, row * ONE_ZONE_HEIGHT,
			(column + 1) * ONE_ZONE_WIDTH, (row + 1) * ONE_ZONE_HEIGHT);
		obstacles = new ArrayList<>();
		players = new ArrayList<>();
	}


	/**
	 * Retourne la colonne de la zone
	 * @return Colonne
	 */
	public int getX() { return column; }
	/**
	 * Retourne la ligne de la zone
	 * @return Ligne
	 */
	public int getY() { return row; }
	/**
	 * Retourne le rectangle occupé par la zone
	 * @return Rectangle
	 */
	public Rectangle getRepresentation() { return representation; }
	/**
	 * Retourne les obstacles chevauchant la zone
	 * @return Liste non modifiable
	 */
	public List<Obstacle> getOBSTACLES() { return Collections.unmodifiableList(obstacles); }
	/**
	 * Retourne les joueurs présents dans la zone
	 * @return Liste non modifiable
	 */
	public List<Player> getPLAYERS() { return Collections.unmodifiableList(players); }


	/**
	 * Ajoute un obstacle à la zone
	 * @param o Obstacle
	 */
	void addObstacle(Obstacle o) {
		obstacles.add(o);
	}

	/**
	 * Ajoute un joueur à la zone
	 * @param p Joueur
	 */
	void addPlayer(Player p) {
		if (!players.contains(p)) players.add(p);
	}

	/**
	 * Retire un joueur de la zone
	 * @param p Joueur
	 */
	void deletePlayer(Player p) {
		players.remove(p);
	}

	@Override
	public String toString() {
		return "(" + column + ", " + row + ") - Nombre de joueurs dans la zone = " + players.size();
	}
}
