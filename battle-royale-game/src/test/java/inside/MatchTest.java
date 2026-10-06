package inside;

import static inside.Boards.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import inside.Board.PlayerSpec;
import inside.GameSettings.ZoneWave;

class MatchTest implements IConfig {
	/**
	 * Durée maximale simulée d'une partie
	 */
	private static final int MAX_TICKS = 10 * 60 * TICKS_PER_SECOND;

	@Test
	void pauseFigeLaSimulationEtRepriseLaRelance() {
		Board b = empty(1, NO_BATTLE);
		b.teleport(0, 300, 360);
		b.enqueue(new Command.Control(Command.ControlType.PAUSE));
		long tick = b.getTick();
		hold(b, 0, EAST, MAX_SPEED_LEVEL, 30);

		assertTrue(b.isPaused());
		assertTrue(b.getSnapshot().paused());
		assertEquals(tick, b.getTick());
		assertEquals(300, b.getPlayer(0).getX());

		b.enqueue(new Command.Control(Command.ControlType.RESUME));
		hold(b, 0, EAST, MAX_SPEED_LEVEL, 30);
		assertFalse(b.isPaused());
		assertTrue(b.getPlayer(0).getX() > 300);
	}

	@Test
	void pausePendantLEchauffementGeleLeCompteARebours() {
		Board b = empty(2, 2);
		run(b, 30);
		b.enqueue(new Command.Control(Command.ControlType.PAUSE));
		run(b, 10 * TICKS_PER_SECOND);
		assertEquals(Phase.WARMUP, b.getPhase());
		assertEquals(2, b.getSnapshot().secondsLeft());
	}

	@Test
	void arretParLAdministrateur() {
		Board b = empty(3, 0);
		run(b, 10);
		b.enqueue(new Command.Control(Command.ControlType.STOP));
		List<GameEvent> events = run(b, 1);

		BoardSnapshot s = b.getSnapshot();
		assertTrue(s.isOver());
		assertTrue(s.stopped());
		assertEquals(-1, s.winnerId());
		assertEquals(List.of(1, 2, 3), s.players().stream().map(BoardSnapshot.PlayerState::rank).sorted().toList());
		assertEquals(1, count(events, GameEvent.Type.GAME_OVER));

		// Plus rien ne bouge après la fin
		long tick = b.getTick();
		hold(b, 0, EAST, 4, 10);
		assertEquals(tick, b.getTick());
	}

	@Test
	void partieSoloEstGagneeDesLeDebutDuCombat() {
		List<ZoneWave> waves = List.of(new ZoneWave(0.5f, 0.5f, 0f, 200));
		Board b = new Board(new GameSettings(0, 9, 0, waves), new GameMap(List.of()), specs(1));
		run(b, 20 * TICKS_PER_SECOND);

		assertTrue(b.isOver());
		assertEquals(0, b.getWinnerId());
		assertEquals(1, b.getAliveCount());
		assertEquals(1, b.getPlayer(0).getRank());
		assertEquals(2, b.getSnapshot().player(0).status());
	}

	@Test
	void imageImmuable() {
		Board b = empty(2, 0);
		BoardSnapshot before = b.getSnapshot();
		hold(b, 0, EAST, 4, 10);
		assertNotSame(before, b.getSnapshot());
		assertThrows(UnsupportedOperationException.class, () -> before.players().clear());
		assertEquals(0, before.tick());
	}

	@ParameterizedTest(name = "graine {0}, {1} robots")
	@CsvSource({ "1, 2", "2, 10", "3, 10", "4, 30", "5, 30", "6, 10", "7, 50", "8, 99" })
	void partieComplèteEntreRobotsSansFenêtre(long seed, int bots) {
		List<PlayerSpec> specs = new ArrayList<>();
		for (int i = 0; i < bots; i++) specs.add(new PlayerSpec(i, BotController.botName(i), true));
		Board b = new Board(GameSettings.defaults(seed).withWarmup(3), specs);
		BotController controller = new BotController(b, seed);

		List<GameEvent> events = new ArrayList<>();
		int damageDuringWarmup = 0;
		for (int t = 0; t < MAX_TICKS && !b.isOver(); t++) {
			controller.update();
			b.tick();
			for (GameEvent e : b.drainEvents()) {
				events.add(e);
				if (e.type() == GameEvent.Type.HIT && b.getPhase() == Phase.WARMUP) damageDuringWarmup += e.amount();
			}
			for (Player p : b.getPlayers()) {
				assertTrue(b.getMap().getBounds().contain(p.getRepresentation()), "joueur hors carte : " + p);
				assertTrue(p.getLifePoints() >= 0 && p.getLifePoints() <= MAX_LIFE_POINTS);
			}
		}

		assertTrue(b.isOver(), "la partie doit se terminer (graine " + seed + ")");
		assertEquals(0, damageDuringWarmup);
		BoardSnapshot s = b.getSnapshot();
		assertTrue(s.alive() <= 1);
		assertEquals(bots, s.total());

		// Chaque joueur éliminé une seule fois, classements cohérents
		Set<Integer> eliminated = new HashSet<>();
		int kills = 0, lavaDeaths = 0;
		for (GameEvent e : events) {
			if (e.type() != GameEvent.Type.ELIMINATION) continue;
			assertTrue(eliminated.add(e.targetId()), "joueur " + e.targetId() + " éliminé deux fois");
			if (e.actorId() >= 0) kills++;
			else lavaDeaths++;
		}
		assertEquals(bots - s.alive(), eliminated.size());
		assertEquals(kills, s.players().stream().mapToInt(BoardSnapshot.PlayerState::kills).sum());
		assertEquals(eliminated.size(), kills + lavaDeaths);

		for (BoardSnapshot.PlayerState p : s.players()) {
			assertTrue(p.rank() >= 1 && p.rank() <= bots, "classement " + p.rank());
			if (p.alive()) assertEquals(1, p.rank());
		}
		if (s.winnerId() >= 0) {
			assertEquals(1, s.alive());
			assertEquals(1, s.player(s.winnerId()).rank());
			assertEquals(2, s.player(s.winnerId()).status());
		}
		assertEquals(1, count(events, GameEvent.Type.GAME_OVER));
		assertTrue(count(events, GameEvent.Type.SHOT) > 0 || bots <= 2);
	}
}
