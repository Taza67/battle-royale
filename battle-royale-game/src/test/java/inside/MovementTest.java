package inside;

import static inside.Boards.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import inside.Obstacle.TypeObstacle;
import inside.geometry.Rectangle;

class MovementTest implements IConfig {
	private static final float FULL_SPEED = Player.pixelsPerSecond(MAX_SPEED_LEVEL);

	@Test
	void niveauxDeVitesseCroissantsEtZeroImmobile() {
		assertEquals(0, Player.pixelsPerSecond(0));
		for (int s = 1; s <= MAX_SPEED_LEVEL; s++)
			assertTrue(Player.pixelsPerSecond(s) > Player.pixelsPerSecond(s - 1));
		assertEquals(Player.pixelsPerSecond(MAX_SPEED_LEVEL), Player.pixelsPerSecond(99), "vitesse bornée à 4");
	}

	@Test
	void deplacementContinuAVitesseConstante() {
		Board b = empty(1, 0);
		b.teleport(0, 300, 360);
		hold(b, 0, EAST, MAX_SPEED_LEVEL, TICKS_PER_SECOND);

		Player p = b.getPlayer(0);
		assertEquals(300 + FULL_SPEED, p.getX(), 0.5f);
		assertEquals(360, p.getY(), 1e-3f);
	}

	@Test
	void diagonaleNormalisee() {
		Board b = empty(1, 0);
		b.teleport(0, 300, 400);
		hold(b, 0, NORTH_EAST, MAX_SPEED_LEVEL, TICKS_PER_SECOND);

		Player p = b.getPlayer(0);
		float dx = p.getX() - 300, dy = p.getY() - 400;
		assertEquals(FULL_SPEED, Math.sqrt(dx * dx + dy * dy), 0.5f, "la diagonale ne doit pas être plus rapide");
		assertEquals(dx, -dy, 1e-3f);
	}

	@Test
	void intentionExpireApres250ms() {
		Board b = empty(1, 0);
		b.teleport(0, 300, 360);
		b.enqueue(new Command.Move(0, EAST, MAX_SPEED_LEVEL));
		run(b, TICKS_PER_SECOND);

		float moved = b.getPlayer(0).getX() - 300;
		float perTick = FULL_SPEED / TICKS_PER_SECOND;
		assertTrue(moved > (MOVE_INTENT_TICKS - 2) * perTick && moved <= MOVE_INTENT_TICKS * perTick + 1e-3f,
			"déplacement de " + moved + " px pour une seule commande");
		assertFalse(b.getPlayer(0).isMoving());
	}

	@Test
	void vitesseZeroArreteLeJoueur() {
		Board b = empty(1, 0);
		b.teleport(0, 300, 360);
		hold(b, 0, EAST, MAX_SPEED_LEVEL, 10);
		float x = b.getPlayer(0).getX();

		b.enqueue(new Command.Move(0, EAST, 0));
		run(b, 10);
		assertEquals(x, b.getPlayer(0).getX(), 1e-4f);
		assertEquals(EAST, b.getPlayer(0).getViewDirection(), "l'arrêt conserve l'orientation");
	}

	@Test
	void glissementLeLongDUnObstacle() {
		Rectangle rock = new Rectangle(330, 250, 380, 470);
		Board b = on(new Map(List.of(new Obstacle(TypeObstacle.ROCHER, rock))), 1, 0);
		b.teleport(0, 315, 360);
		hold(b, 0, NORTH_EAST, MAX_SPEED_LEVEL, 30);

		Player p = b.getPlayer(0);
		assertEquals(rock.getX1() - PLAYER_RADIUS_X, p.getX(), 1e-3f, "bloqué au contact du rocher");
		assertTrue(p.getY() < 360 - 50, "glisse vers le nord : y = " + p.getY());
		assertFalse(p.getRepresentation().intersect(rock));
	}

	@Test
	void lEauBloqueAussiLesJoueurs() {
		Rectangle water = new Rectangle(330, 250, 380, 470);
		Board b = on(new Map(List.of(new Obstacle(TypeObstacle.EAU, water))), 1, 0);
		b.teleport(0, 300, 360);
		hold(b, 0, EAST, MAX_SPEED_LEVEL, 30);
		assertEquals(water.getX1() - PLAYER_RADIUS_X, b.getPlayer(0).getX(), 1e-3f);
	}

	@Test
	void lesJoueursSeBloquentEntreEux() {
		Board b = empty(2, 0);
		b.teleport(0, 300, 360);
		b.teleport(1, 360, 360);
		hold(b, 0, EAST, MAX_SPEED_LEVEL, 30);

		Player p = b.getPlayer(0), q = b.getPlayer(1);
		assertEquals(q.getX() - 2 * PLAYER_RADIUS_X, p.getX(), 1e-3f);
		assertFalse(p.getRepresentation().intersect(q.getRepresentation()));
	}

	@Test
	void joueurBorneAuxCoinsDeLaCarte() {
		Board b = empty(1, 0);
		b.teleport(0, 30, 30);
		hold(b, 0, NORTH_WEST, MAX_SPEED_LEVEL, TICKS_PER_SECOND);
		assertEquals(PLAYER_RADIUS_X, b.getPlayer(0).getX(), 1e-3f);
		assertEquals(PLAYER_RADIUS_Y, b.getPlayer(0).getY(), 1e-3f);

		b.teleport(0, MAP_WIDTH - 30, MAP_HEIGHT - 30);
		hold(b, 0, SOUTH_EAST, MAX_SPEED_LEVEL, TICKS_PER_SECOND);
		Player p = b.getPlayer(0);
		assertEquals(MAP_WIDTH - PLAYER_RADIUS_X, p.getX(), 1e-3f);
		assertEquals(MAP_HEIGHT - PLAYER_RADIUS_Y, p.getY(), 1e-3f);
		assertSame(b.getMap().getArea(AREAS_HEIGHT - 1, AREAS_WIDTH - 1), p.getZone());
	}

	@Test
	void directionInvalideIgnoree() {
		Board b = empty(1, 0);
		b.teleport(0, 300, 360);
		b.enqueue(new Command.Move(0, 9, 4));
		b.enqueue(new Command.Move(0, -1, 4));
		b.enqueue(new Command.Move(57, EAST, 4));
		run(b, 10);
		assertEquals(300, b.getPlayer(0).getX(), 1e-4f);
	}

	@Test
	void apparitionSansChevauchementSurUneCarteGeneree() {
		for (int n : new int[] { 30, 50, 75, MAX_PLAYERS - 1, MAX_PLAYERS }) {
			for (long seed = 0; seed < 20; seed++) {
				Board b = new Board(GameSettings.defaults(seed), specs(n));
				List<Player> players = b.getPlayers();
				for (Player p : players) {
					assertTrue(b.getMap().getBounds().contain(p.getRepresentation()));
					assertTrue(b.getMap().isFree(p.getRepresentation()), "joueur dans un obstacle (graine " + seed + ")");
					for (Player q : players)
						if (p != q) assertFalse(p.getRepresentation().intersect(q.getRepresentation()),
							n + " joueurs, graine " + seed + " : " + p.getID() + " chevauche " + q.getID());
				}
			}
		}
	}

	@Test
	void tousLesJoueursPeuventBougerAvec99Joueurs() {
		for (long seed = 0; seed < 5; seed++) {
			Board b = new Board(GameSettings.defaults(seed).withWarmup(60), specs(MAX_PLAYERS - 1));
			for (Player p : b.getPlayers()) {
				float x = p.getX(), y = p.getY();
				boolean moved = false;
				for (int d = 0; d < 8 && !moved; d += 2) {
					hold(b, p.getID(), d, MAX_SPEED_LEVEL, 3);
					moved = p.getX() != x || p.getY() != y;
				}
				assertTrue(moved, "joueur " + p.getID() + " bloqué dans toutes les directions (graine " + seed + ")");
			}
		}
	}

	@Test
	void joueursSuperposesPeuventSeSeparer() {
		Board b = empty(2, 0);
		b.teleport(0, 300, 360);
		b.teleport(1, 305, 362);
		hold(b, 0, WEST, MAX_SPEED_LEVEL, 30);
		hold(b, 1, EAST, MAX_SPEED_LEVEL, 30);

		Player p = b.getPlayer(0), q = b.getPlayer(1);
		assertTrue(p.getX() < 300 - 50, "le joueur 0 s'éloigne : x = " + p.getX());
		assertTrue(q.getX() > 305 + 50, "le joueur 1 s'éloigne : x = " + q.getX());
		assertFalse(p.getRepresentation().intersect(q.getRepresentation()));
	}

	@Test
	void unChevauchementInitialNeTraversePasLesAutresJoueurs() {
		Board b = empty(3, 0);
		b.teleport(0, 300, 360);
		b.teleport(1, 305, 360);
		b.teleport(2, 360, 360);
		hold(b, 0, EAST, MAX_SPEED_LEVEL, 60);

		Player p = b.getPlayer(0), r = b.getPlayer(2);
		assertEquals(r.getX() - 2 * PLAYER_RADIUS_X, p.getX(), 1e-3f, "arrêté par le nouveau contact");
		assertFalse(p.getRepresentation().intersect(r.getRepresentation()));
	}
}
