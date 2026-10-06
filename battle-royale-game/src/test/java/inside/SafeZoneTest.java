package inside;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import inside.GameSettings.ZoneWave;
import inside.SafeZone.Stage;
import inside.SafeZone.Transition;
import inside.geometry.Rectangle;

class SafeZoneTest implements IConfig {
	private static final Rectangle BOUNDS = new Rectangle(0, 0, MAP_WIDTH, MAP_HEIGHT);

	@Test
	void inactiveAvantLeCombat() {
		SafeZone z = new SafeZone(BOUNDS, GameSettings.DEFAULT_WAVES, new Random(1), null);
		assertEquals(Stage.INACTIVE, z.getStage());
		assertEquals(Transition.NONE, z.update());
		assertEquals(BOUNDS, z.getCurrent());
		assertEquals(0, z.getLavaDamagePerSecond());
		assertTrue(z.isSafe(1, 1));
	}

	@Test
	void vaguesAttenteResserrementEtFermeture() {
		for (long seed = 0; seed < 40; seed++) {
			SafeZone z = new SafeZone(BOUNDS, GameSettings.DEFAULT_WAVES, new Random(seed), null);
			z.start();

			Rectangle previous = z.getCurrent();
			int lastLava = 0, shrinks = 0;
			Stage lastStage = Stage.WAITING;
			for (int t = 0; t < 600 * TICKS_PER_SECOND && z.getStage() != Stage.CLOSED; t++) {
				Rectangle next = z.getNext();
				assertTrue(z.getCurrent().contain(next), "la prochaine zone est incluse dans la zone actuelle");

				Transition tr = z.update();
				Rectangle current = z.getCurrent();
				assertTrue(previous.contain(current), "la zone ne grandit jamais");
				previous = current;

				if (tr == Transition.SHRINK_STARTED) shrinks++;
				if (z.getStage() != Stage.CLOSED) {
					assertTrue(z.getLavaDamagePerSecond() >= lastLava, "dégâts de lave croissants");
					lastLava = z.getLavaDamagePerSecond();
				}
				if (lastStage == Stage.WAITING && z.getStage() == Stage.SHRINKING)
					assertEquals(Transition.SHRINK_STARTED, tr);
				lastStage = z.getStage();
			}

			assertEquals(Stage.CLOSED, z.getStage(), "graine " + seed);
			assertEquals(GameSettings.DEFAULT_WAVES.size(), shrinks);
			assertEquals(0, z.getCurrent().getWidth(), 1e-3f);
			assertEquals(0, z.getSecondsLeft());
		}
	}

	@Test
	void tailleEtDureesDesVagues() {
		List<ZoneWave> waves = List.of(new ZoneWave(2, 1, 0.5f, 4), new ZoneWave(1, 1, 0.25f, 9));
		SafeZone z = new SafeZone(BOUNDS, waves, new Random(3), null);
		z.start();

		assertEquals(Stage.WAITING, z.getStage());
		assertEquals(2, z.getSecondsLeft());
		assertEquals(4, z.getLavaDamagePerSecond());
		assertEquals(MAP_WIDTH * 0.5f, z.getNext().getWidth(), 1e-3f);
		assertEquals(MAP_HEIGHT * 0.5f, z.getNext().getHeight(), 1e-3f);

		Rectangle target = z.getNext();
		for (int i = 0; i < 2 * TICKS_PER_SECOND; i++) z.update();
		assertEquals(Stage.SHRINKING, z.getStage());

		for (int i = 0; i < TICKS_PER_SECOND / 2; i++) z.update();
		Rectangle mid = z.getCurrent();
		assertTrue(mid.contain(target) && BOUNDS.contain(mid) && !mid.equals(target) && !mid.equals(BOUNDS));

		for (int i = 0; i < TICKS_PER_SECOND; i++) z.update();
		assertEquals(Stage.WAITING, z.getStage());
		assertEquals(target, z.getCurrent());
		assertEquals(9, z.getLavaDamagePerSecond());
		assertTrue(target.contain(z.getNext()));
	}

	@Test
	void laveInfligeDesDegatsHorsDeLaZone() {
		List<ZoneWave> waves = List.of(new ZoneWave(0.1f, 0.1f, 0.2f, 30), new ZoneWave(100, 1, 0.1f, 30));
		Board b = new Board(new GameSettings(0, 5, 0, waves), new GameMap(List.of()), Boards.specs(2));
		Boards.run(b, 30);
		Rectangle safe = b.getSafeZone().getCurrent();

		b.teleport(0, safe.getCenterX(), safe.getCenterY());
		float[][] corners = { { 15, 15 }, { MAP_WIDTH - 15, 15 }, { 15, MAP_HEIGHT - 15 }, { MAP_WIDTH - 15, MAP_HEIGHT - 15 } };
		for (float[] c : corners) {
			if (safe.expand(PLAYER_RADIUS_X).contains(c[0], c[1])) continue;
			b.teleport(1, c[0], c[1]);
			break;
		}
		assertFalse(safe.contains(b.getPlayer(1).getX(), b.getPlayer(1).getY()));
		int life0 = b.getPlayer(0).getLifePoints(), life1 = b.getPlayer(1).getLifePoints();
		Boards.run(b, TICKS_PER_SECOND);

		assertEquals(life0, b.getPlayer(0).getLifePoints());
		assertEquals(life1 - 30, b.getPlayer(1).getLifePoints(), 1);
		assertTrue(b.getSnapshot().player(1).inLava());
		assertEquals(DamageCause.LAVA, b.getPlayer(1).getLastDamageCause());
	}
}
