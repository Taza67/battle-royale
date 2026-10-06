package communication.game;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import communication.session.PendingActions;

class GameLinkTest {
	private static final List<Participant> ROSTER = List.of(new Participant(0, "Taza"), new Participant(3, "Émile"));

	private FakeGameServer game;
	private GameLink link;
	private final Recorder recorder = new Recorder();

	/** Enregistre les événements du lien */
	static final class Recorder implements GameLinkListener {
		final BlockingQueue<String> events = new LinkedBlockingQueue<>();
		volatile GameSnapshot lastSnapshot;
		volatile GameSnapshot finalSnapshot;
		volatile GameSnapshot stoppedSnapshot;

		@Override public void onStarted(GameLink l) { events.add("started"); }
		@Override public void onStartFailed(GameLink l, String reason) { events.add("failed:" + reason); }
		@Override public void onSnapshot(GameLink l, GameSnapshot s) { lastSnapshot = s; events.add("snapshot"); }
		@Override public void onFinished(GameLink l, GameSnapshot s) { finalSnapshot = s; events.add("finished"); }
		@Override public void onStopped(GameLink l, GameSnapshot s) { stoppedSnapshot = s; events.add("stopped"); }
		@Override public void onLinkLost(GameLink l, String reason) { events.add("lost:" + reason); }

		String next(String expectedPrefix) throws InterruptedException {
			while (true) {
				String e = events.poll(3, TimeUnit.SECONDS);
				if (e == null)
					throw new AssertionError("Événement attendu : " + expectedPrefix);
				if (e.startsWith(expectedPrefix))
					return e;
				if (!e.equals("snapshot"))
					throw new AssertionError("Événement inattendu " + e + " au lieu de " + expectedPrefix);
			}
		}
	}

	@BeforeEach
	void setUp() throws Exception {
		game = new FakeGameServer();
	}

	@AfterEach
	void tearDown() throws Exception {
		if (link != null)
			link.close(1000);
		game.close();
	}

	private GameLink start(ActionSource actions) {
		return start(actions, GameLinkSettings.of("localhost", FakeGameServer.PORT));
	}

	private GameLink start(ActionSource actions, GameLinkSettings settings) {
		link = new GameLink(settings, ROSTER, actions, recorder);
		link.start();
		return link;
	}

	@Test
	void sendsHandshakeThenExchangesEvery50msEvenWithoutActions() throws Exception {
		start(now -> new byte[0]);

		FakeGameServer.Handshake handshake = game.await(FakeGameServer.Handshake.class, 2000);
		assertEquals(ROSTER, handshake.participants());
		recorder.next("started");

		long begin = System.nanoTime();
		for (int i = 0; i < 10; i++)
			assertEquals(0, game.await(FakeGameServer.Actions.class, 1000).payload().length);
		long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
		assertTrue(elapsedMillis >= 400 && elapsedMillis < 1500, "10 échanges en " + elapsedMillis + " ms");

		recorder.next("snapshot");
		assertNotNull(recorder.lastSnapshot);
		assertEquals(GameSnapshot.Phase.BATTLE, recorder.lastSnapshot.phase());
	}

	@Test
	void forwardsMoveIntentsAndAttacks() throws Exception {
		PendingActions actions = new PendingActions(List.of(0, 3));
		start(actions);
		recorder.next("started");

		actions.move(0, 2, 4);
		actions.attack(3, 2);
		assertArrayEquals(new byte[] { 0, 0, 2, 4, 3, 1, 2 }, game.awaitActions(2000));

		actions.move(0, 2, 0);
		assertArrayEquals(new byte[] { 0, 0, 0, 0 }, game.awaitActions(2000));
	}

	@Test
	void relaysPauseResumeAndStop() throws Exception {
		start(now -> new byte[0]);
		recorder.next("started");
		game.await(FakeGameServer.Actions.class, 2000);

		link.pause();
		assertEquals(GameLink.CODE_PAUSE, game.awaitControl(2000).code());
		Thread.sleep(250);
		assertTrue(game.events().isEmpty(), "aucun échange pendant la pause : " + game.events());

		link.resume();
		assertEquals(GameLink.CODE_RESUME, game.awaitControl(2000).code());
		game.await(FakeGameServer.Actions.class, 2000);

		link.stop();
		assertEquals(GameLink.CODE_STOP, game.awaitControl(2000).code());
		game.await(FakeGameServer.Closed.class, 2000);
		recorder.next("stopped");
		assertTrue(link.awaitTermination(2000));
	}

	@Test
	void keepsExchangingAfterStopUntilTheFinalState() throws Exception {
		start(now -> new byte[0]);
		recorder.next("started");
		game.await(FakeGameServer.Actions.class, 2000);
		game.stopState(SnapshotBytes.battle().phase(2).counts(1, 2).winner(3)
			.player(0, 0, 0, 10, 10, 0, 2)
			.player(3, 2, 40, 20, 20, 1, 1)
			.build());

		link.stop();
		assertEquals(GameLink.CODE_STOP, game.awaitControl(2000).code());
		assertEquals(0, game.await(FakeGameServer.Actions.class, 1000).payload().length);
		recorder.next("stopped");
		assertNotNull(recorder.stoppedSnapshot);
		assertEquals(GameSnapshot.Phase.OVER, recorder.stoppedSnapshot.phase());
		assertEquals(2, recorder.stoppedSnapshot.player(0).rank());
		game.await(FakeGameServer.Closed.class, 2000);
		assertTrue(link.awaitTermination(2000));
	}

	@Test
	void givesUpWaitingForTheFinalStateAfterTheStopTimeout() throws Exception {
		game.onStop(FakeGameServer.StopBehavior.IGNORE);
		start(now -> new byte[0]);
		recorder.next("started");
		recorder.next("snapshot");

		long begin = System.nanoTime();
		link.stop();
		assertEquals(GameLink.CODE_STOP, game.awaitControl(2000).code());
		String event;
		do {
			event = recorder.events.poll(4, TimeUnit.SECONDS);
		} while ("snapshot".equals(event));
		assertEquals("stopped", event);
		long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
		assertTrue(elapsedMillis >= GameLink.STOP_TIMEOUT_MILLIS - 100 && elapsedMillis < GameLink.STOP_TIMEOUT_MILLIS + 1000,
			"arrêt abandonné après " + elapsedMillis + " ms");
		assertNotNull(recorder.stoppedSnapshot, "le dernier état reçu sert d'état final");
		int ticks = 0;
		FakeGameServer.Event e;
		while ((e = game.events().poll(2, TimeUnit.SECONDS)) != null && !(e instanceof FakeGameServer.Closed))
			if (e instanceof FakeGameServer.Actions)
				ticks++;
		assertTrue(e instanceof FakeGameServer.Closed, "connexion fermée après l'abandon");
		assertTrue(ticks >= 20, ticks + " échanges vides pendant l'attente");
		assertTrue(link.awaitTermination(2000));
	}

	@Test
	void reportsTheStopWhenTheGameClosesTheConnection() throws Exception {
		game.onStop(FakeGameServer.StopBehavior.CLOSE);
		start(now -> new byte[0]);
		recorder.next("started");
		recorder.next("snapshot");

		link.stop();
		recorder.next("stopped");
		assertNotNull(recorder.stoppedSnapshot);
		assertTrue(link.awaitTermination(2000));
	}

	@Test
	void stopsWhilePaused() throws Exception {
		start(now -> new byte[0]);
		recorder.next("started");

		link.pause();
		assertEquals(GameLink.CODE_PAUSE, game.awaitControl(2000).code());
		link.stop();
		assertEquals(GameLink.CODE_STOP, game.awaitControl(2000).code());
		recorder.next("stopped");
		assertTrue(link.awaitTermination(2000));
	}

	@Test
	void reportsTheEndOfTheGameWithTheFinalState() throws Exception {
		start(now -> new byte[0]);
		recorder.next("started");

		byte[] last = SnapshotBytes.battle().phase(2).counts(1, 2).winner(3)
			.player(0, 0, 0, 10, 10, 0, 2)
			.player(3, 2, 40, 20, 20, 1, 1)
			.build();
		game.finish(last);

		recorder.next("finished");
		assertEquals(3, recorder.finalSnapshot.winnerId());
		assertEquals(PlayerSnapshot.Status.WINNER, recorder.finalSnapshot.player(3).status());
		game.await(FakeGameServer.Closed.class, 2000);
		assertTrue(link.awaitTermination(2000));
	}

	@Test
	void reportsRefusedGames() throws Exception {
		game.accept(false);
		start(now -> new byte[0]);
		assertEquals("failed:" + GameLink.REFUSED, recorder.next("failed"));
		assertTrue(link.awaitTermination(2000));
	}

	@Test
	void reportsUnreachableGamesWithoutExiting() throws Exception {
		start(now -> new byte[0], GameLinkSettings.of("localhost", FakeGameServer.UNUSED_PORT));
		assertEquals("failed:" + GameLink.UNREACHABLE, recorder.next("failed"));
		assertTrue(link.awaitTermination(2000));
	}

	@Test
	void reportsGamesThatNeverAnswerTheHandshake() throws Exception {
		game.silentHandshake(true);
		start(now -> new byte[0], new GameLinkSettings("localhost", FakeGameServer.PORT, 50, 1000, 300));
		assertEquals("failed:" + GameLink.NO_HANDSHAKE, recorder.next("failed"));
	}

	@Test
	void reportsLostConnections() throws Exception {
		start(now -> new byte[0]);
		recorder.next("started");
		game.dropNext();
		assertTrue(recorder.next("lost").startsWith("lost:"));
		assertTrue(link.awaitTermination(2000));
	}

	@Test
	void ignoresMalformedStatesButKeepsExchanging() throws Exception {
		game.state(new byte[] { 9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
		start(now -> new byte[0]);
		recorder.next("started");
		for (int i = 0; i < 4; i++)
			game.await(FakeGameServer.Actions.class, 1000);
		assertNull(recorder.lastSnapshot);

		game.state(SnapshotBytes.battle().build());
		recorder.next("snapshot");
	}
}
