package outside;

import static inside.IConfig.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import inside.Board;
import inside.Board.PlayerSpec;
import inside.BotController;
import inside.Command;
import inside.GameEvent;
import inside.GameSettings;
import inside.IConfig;
import inside.Phase;

@Timeout(20)
class SimulationLoopTest {
	private static final long T = SimulationLoop.TICK_NANOS;
	private SimulationLoop loop;

	private static Board board(float warmup, boolean bots) {
		return new Board(GameSettings.defaults(5).withWarmup(warmup),
			List.of(new PlayerSpec(0, "Alice", false), new PlayerSpec(1, "Bob", bots), new PlayerSpec(2, "Chloé", bots)));
	}

	@AfterEach
	void close() {
		if (loop != null) loop.close();
	}

	@Test
	void pasFixesEtRattrapageBorne() {
		loop = new SimulationLoop();
		Board b = board(30, false);
		assertEquals(0, loop.advance(0), "aucune partie");
		assertNull(loop.getFrame());

		loop.play(SimulationLoop.Session.of(b, 0));
		long t0 = 1_000_000_000L;
		assertEquals(1, loop.advance(t0), "premier pas immédiat");
		assertEquals(0, loop.advance(t0 + T / 2));
		assertEquals(1, loop.advance(t0 + T));
		assertEquals(3, loop.advance(t0 + 4 * T));
		assertEquals(5, b.getTick());

		assertEquals(SimulationLoop.MAX_CATCH_UP_TICKS, loop.advance(t0 + 10_000 * T), "retard abandonné au-delà de 0,25 s");
		assertEquals(5 + SimulationLoop.MAX_CATCH_UP_TICKS, b.getTick());
		assertEquals(0, loop.advance(t0 + 10_000 * T + T / 2), "pas de rattrapage résiduel");
		assertEquals(1, loop.advance(t0 + 10_001 * T));
	}

	@Test
	void imagesPubliesPourLInterpolation() {
		loop = new SimulationLoop();
		Board b = board(30, false);
		SimulationLoop.Session s = SimulationLoop.Session.of(b, 0);
		assertNull(s.bots(), "aucun robot");
		loop.play(s);

		long t0 = 5_000_000_000L;
		loop.advance(t0);
		loop.advance(t0 + 3 * T);
		SimulationLoop.Frame f = loop.getFrame();
		assertSame(s, f.session());
		assertEquals(4, f.current().tick());
		assertEquals(3, f.previous().tick(), "image précédant le dernier pas");
		assertEquals(t0 + 3 * T, f.time());
		assertEquals(0, f.alpha(t0 + 3 * T));
		assertEquals(0.5f, f.alpha(t0 + 3 * T + T / 2), 1e-3f);
		assertEquals(1, f.alpha(t0 + 10 * T));
	}

	@Test
	void evenementsTransmisAvecLeurPartie() {
		loop = new SimulationLoop();
		Board first = board(0, false);
		SimulationLoop.Session s1 = SimulationLoop.Session.of(first, -1);
		loop.play(s1);
		loop.advance(0);

		List<SimulationLoop.Events> batches = loop.drainEvents();
		assertEquals(1, batches.size());
		assertSame(s1, batches.get(0).session());
		assertEquals(GameEvent.Type.BATTLE_STARTED, batches.get(0).events().get(0).type());
		assertEquals(Phase.BATTLE, batches.get(0).snapshot().phase());
		assertTrue(loop.drainEvents().isEmpty(), "lots vidés");

		Board second = board(30, true);
		SimulationLoop.Session s2 = SimulationLoop.Session.of(second, -1);
		assertNotNull(s2.bots());
		loop.play(s2);
		loop.advance(T);
		assertSame(s2, loop.getFrame().session(), "nouvelle partie prise en charge");
		long frozen = first.getTick();
		loop.advance(10 * T);
		assertEquals(frozen, first.getTick(), "l'ancienne partie n'avance plus");
		assertEquals(10, second.getTick());
	}

	@Test
	void lotsDEvenementsBornes() {
		loop = new SimulationLoop();
		Board b = board(30, false);
		loop.play(SimulationLoop.Session.of(b, -1));
		loop.advance(0);
		for (int i = 0; i < SimulationLoop.MAX_EVENT_BATCHES + 50; i++) {
			b.enqueue(new Command.Control(i % 2 == 0 ? Command.ControlType.PAUSE : Command.ControlType.RESUME));
			loop.advance((i + 1) * T);
		}
		List<SimulationLoop.Events> batches = loop.drainEvents();
		assertEquals(SimulationLoop.MAX_EVENT_BATCHES, batches.size(), "les plus anciens lots sont abandonnés");
		assertEquals(GameEvent.Type.RESUMED, batches.get(batches.size() - 1).events().get(0).type());
	}

	@Test
	void laPartieAvanceSansLeFilDAffichage() throws InterruptedException {
		loop = new SimulationLoop();
		Board b = board(30, true);
		loop.start();
		loop.play(SimulationLoop.Session.of(b, -1));

		Thread.sleep(500);
		long ticks = loop.getFrame().current().tick();
		assertTrue(ticks >= 20 && ticks <= 40, ticks + " pas en 0,5 s");

		b.enqueue(new Command.Control(Command.ControlType.PAUSE));
		Thread.sleep(100);
		long paused = loop.getFrame().current().tick();
		Thread.sleep(200);
		assertEquals(paused, loop.getFrame().current().tick(), "pause respectée");
		assertTrue(loop.getFrame().current().paused());

		b.enqueue(new Command.Control(Command.ControlType.STOP));
		Thread.sleep(100);
		assertTrue(loop.getFrame().current().isOver(), "arrêt appliqué");
		assertNull(loop.getFailure());

		loop.close();
		long last = b.getTick();
		Thread.sleep(100);
		assertEquals(last, b.getTick(), "fil arrêté");
	}

	@Test
	void erreurDeLaSimulationRemonteeALAffichage() throws InterruptedException {
		loop = new SimulationLoop();
		Board b = board(30, true);
		loop.start();
		loop.play(new SimulationLoop.Session(b, new BotController(b, 1) {
			@Override
			public void update() {
				throw new IllegalStateException("panne");
			}
		}, -1));

		long deadline = System.currentTimeMillis() + 2000;
		while (loop.getFailure() == null && System.currentTimeMillis() < deadline) Thread.sleep(10);
		assertEquals("panne", loop.getFailure().getMessage());
	}
}
