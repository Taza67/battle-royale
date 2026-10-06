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
	 * Position de la case dans la grille (colonne, ligne)
	 */
	private final int column, row;
	/**
	 * Rectangle occupé par la case
	 */
	private final Rectangle representation;
	/**
	 * Obstacles chevauchant la case
	 */
	private final List<Obstacle> obstacles;
	/**
	 * Joueurs vivants présents dans la case
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
	 * Retourne la colonne de la case
	 * @return Colonne
	 */
	public int getColumn() { return column; }
	/**
	 * Retourne la ligne de la case
	 * @return Ligne
	 */
	public int getRow() { return row; }
	/**
	 * Retourne le rectangle occupé par la case
	 * @return Rectangle
	 */
	public Rectangle getRepresentation() { return representation; }
	/**
	 * Retourne les obstacles chevauchant la case
	 * @return Liste non modifiable
	 */
	public List<Obstacle> getObstacles() { return Collections.unmodifiableList(obstacles); }
	/**
	 * Retourne les joueurs présents dans la case
	 * @return Liste non modifiable
	 */
	public List<Player> getPlayers() { return Collections.unmodifiableList(players); }


	/**
	 * Ajoute un obstacle à la case
	 * @param o Obstacle
	 */
	void addObstacle(Obstacle o) {
		obstacles.add(o);
	}

	/**
	 * Ajoute un joueur à la case
	 * @param p Joueur
	 */
	void addPlayer(Player p) {
		if (!players.contains(p)) players.add(p);
	}

	/**
	 * Retire un joueur de la case
	 * @param p Joueur
	 */
	void deletePlayer(Player p) {
		players.remove(p);
	}

	@Override
	public String toString() {
		return "(" + column + ", " + row + ") - Nombre de joueurs dans la case = " + players.size();
	}
}
