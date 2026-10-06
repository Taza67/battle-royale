package inside;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import inside.geometry.Rectangle;
import inside.geometry.Vertice;

public class Zone implements IConfig {
	private final Player[] PLAYERS;
	private final List<Integer> PLAYERS_IDS;
	private final Player[] DEADS;
	private final List<Integer> DEADS_IDS;
	private final List<Obstacle> OBSTACLES;
	private final List<Bullet> BULLETS;
	private final Vertice TOP_LEFT_CORNER;
	private final Vertice BOTTOM_RIGHT_CORNER;
	private final int X, Y;
	private final Rectangle REPRESENTATION;
	private final List<Zone> NEIGHBORS;


	// Constructeurs
	public Zone(int x, int y) {
		PLAYERS = new Player[PLAYERS_NUMBER];
		PLAYERS_IDS = new ArrayList<>();
		DEADS = new Player[PLAYERS_NUMBER];
		DEADS_IDS = new ArrayList<>();
		OBSTACLES = new ArrayList<>();
		BULLETS = new ArrayList<>();
		TOP_LEFT_CORNER = new Vertice(x * ONE_ZONE_WIDTH, y * ONE_ZONE_HEIGHT);
		BOTTOM_RIGHT_CORNER = new Vertice((x + 1) * ONE_ZONE_WIDTH, (y + 1) * ONE_ZONE_HEIGHT);
		X = x;
		Y = y;
		REPRESENTATION = new Rectangle(
			new Vertice((x + 1) * ONE_ZONE_WIDTH / 2f, (y + 1) * ONE_ZONE_HEIGHT / 2f),
			TOP_LEFT_CORNER, BOTTOM_RIGHT_CORNER);
		NEIGHBORS = new ArrayList<>();
	}


	// Accesseurs
	public synchronized List<Integer> getPLAYERS_IDS() {
		return PLAYERS_IDS;
	}
	public List<Obstacle> getOBSTACLES() { return OBSTACLES; }
	public synchronized Player getPlayer(int id) throws IndexOutOfBoundsException {
		return PLAYERS[id];
	}
	public List<Zone> getNEIGHBORS() {
		return NEIGHBORS;
	}


	// Méthodes
	@Override
	public String toString() {
		return "(" + X + ", " + Y + ") - Nombre de joueurs dans la zone = " + PLAYERS_IDS.size();
	}

	// Collecte tous les voisins de la zone
	public void collectNeigbors(Zone[][] areas) {
		verticalAxe : for (int i = Y - 1; i <= Y + 1; i++)
			for (int j = X - 1; j <= X + 1; j++) {
				if (j == X && i == Y) continue;
				if (i < 0 || i >= AREAS_HEIGHT) continue verticalAxe;
				if (j < 0 || j >= AREAS_WIDTH) continue;
				NEIGHBORS.add(areas[i][j]);
			}
	}

	// Ajoute le joueur à la zone
	public synchronized void addPlayer(int id, Player p) {
		PLAYERS[id] = p;
		PLAYERS_IDS.add(id);
	}

	// Ajoute un joueur à la liste des joueurs morts
	public synchronized void addDead(int id, Player p) {
		DEADS[id] = p;
		DEADS_IDS.add(id);
	}

	// Ajoute une balle dans la zone
	public synchronized void addBullet(Bullet b) {
		BULLETS.add(b);
	}

	// Ajoute l'obstacle à la zone
	public synchronized void addObstacle(Obstacle o) {
		OBSTACLES.add(o);
	}

	// Enlèves le joueur de la zone
	public synchronized void deletePlayer(int id) throws IndexOutOfBoundsException {
		PLAYERS[id] = null;
		if (PLAYERS_IDS.contains(id)) PLAYERS_IDS.remove((Integer)id);
	}

	// Enlève la balle de la zone
	public synchronized void deleteBullet(Bullet b) {
		BULLETS.remove(b);
	}


	// Méthodes graphiques
	public synchronized void draw() {
		GL11.glLineWidth(2.0f);  // Définir la largeur de la ligne
        GL11.glColor3f(0.0f, 0.0f, 0.0f);

        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < 4; i++) {
            GL11.glVertex2f(REPRESENTATION.getVertice(i).getX(), REPRESENTATION.getVertice(i).getY());
        }
        GL11.glEnd();
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
	}
}
