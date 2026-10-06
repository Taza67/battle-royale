package communication.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import communication.game.FakeGameServer;
import communication.game.GameLink;
import communication.game.GameLinkSettings;
import communication.game.Participant;
import communication.game.SnapshotBytes;
import communication.message.ClientMessage.Command;

class GameSessionTest {
	private FakeGameServer game;
	private GameSession session;
	private final FakeConnection admin = new FakeConnection("admin");
	private final FakeConnection taza = new FakeConnection("taza");
	private final FakeConnection emile = new FakeConnection("emile");

	@BeforeEach
	void setUp() throws Exception {
		game = new FakeGameServer();
		session = new GameSession(GameLinkSettings.of("localhost", FakeGameServer.PORT), null);
	}

	@AfterEach
	void tearDown() throws Exception {
		session.close();
		if (game != null)
			game.close();
	}

	private void ack(FakeConnection c, String command, boolean ok, String error) throws InterruptedException {
		JsonObject ack = c.next("ack");
		assertEquals(command, ack.get("command").getAsString(), ack.toString());
		assertEquals(ok, ack.get("ok").getAsBoolean(), ack.toString());
		if (error == null)
			assertTrue(ack.get("error").isJsonNull(), ack.toString());
		else
			assertEquals(error, ack.get("error").getAsString());
	}

	/** Attend que les actions reçues contiennent chacune des séquences données */
	private void awaitActions(int[]... expected) throws InterruptedException {
		List<byte[]> missing = new ArrayList<>();
		for (int[] chunk : expected) {
			byte[] bytes = new byte[chunk.length];
			for (int i = 0; i < chunk.length; i++)
				bytes[i] = (byte) chunk[i];
			missing.add(bytes);
		}
		long deadline = System.currentTimeMillis() + 2000;
		while (!missing.isEmpty()) {
			byte[] payload = game.awaitActions(Math.max(1, deadline - System.currentTimeMillis()));
			missing.removeIf(chunk -> contains(payload, chunk));
		}
	}

	private static boolean contains(byte[] payload, byte[] chunk) {
		for (int i = 0; i + chunk.length <= payload.length; i++)
			if (Arrays.equals(Arrays.copyOfRange(payload, i, i + chunk.length), chunk))
				return true;
		return false;
	}

	private static int[] action(int... bytes) { return bytes; }

	private void startRound() throws InterruptedException {
		session.command(admin, Command.START);
		ack(admin, "start", true, null);
		assertEquals(GameState.RUNNING, session.state());
	}

	@Test
	void welcomesPlayersAndKeepsTheAdminInformed() throws Exception {
		assertTrue(session.claimAdmin(admin, ""));
		assertEquals("lobby", admin.next("admin-welcome").get("state").getAsString());
		assertEquals(0, admin.next("players").getAsJsonArray("players").size());

		Player p = session.join(taza, "Taza");
		assertNotNull(p);
		JsonObject welcome = taza.next("welcome");
		assertEquals(0, welcome.get("id").getAsInt());
		assertEquals("Taza", welcome.get("pseudo").getAsString());
		assertEquals("lobby", welcome.get("state").getAsString());

		JsonArray players = admin.next("players").getAsJsonArray("players");
		assertEquals(1, players.size());
		JsonObject entry = players.get(0).getAsJsonObject();
		assertEquals("Taza", entry.get("pseudo").getAsString());
		assertTrue(entry.get("connected").getAsBoolean());
		assertEquals("alive", entry.get("status").getAsString());
		assertEquals(100, entry.get("life").getAsInt());

		assertEquals(1, session.join(emile, "Émile").getId());
	}

	@Test
	void rejectsPseudosAttachedToAnOpenSessionIgnoringCase() throws Exception {
		Player p = session.join(taza, "Taza");
		FakeConnection intruder = new FakeConnection("intruder");
		assertNull(session.join(intruder, "taza"));
		assertEquals(GameSession.PSEUDO_TAKEN, intruder.next("rejected").get("reason").getAsString());

		taza.drop();
		FakeConnection back = new FakeConnection("back");
		assertSame(p, session.join(back, "TAZA"));
		JsonObject welcome = back.next("welcome");
		assertEquals(0, welcome.get("id").getAsInt());
		assertEquals("Taza", welcome.get("pseudo").getAsString());
	}

	@Test
	void givesTheAdminSeatToTheFirstClaimantWhileConnected() throws Exception {
		FakeConnection other = new FakeConnection("other");
		assertTrue(session.claimAdmin(admin, "n'importe quoi"));
		assertFalse(session.claimAdmin(other, ""));
		assertEquals(GameSession.ADMIN_TAKEN, other.next("rejected").get("reason").getAsString());

		session.releaseAdmin(admin);
		assertTrue(session.claimAdmin(other, ""));
		other.next("admin-welcome");
	}

	@Test
	void requiresThePasswordWhenConfiguredAndLetsTheAdminReconnect() throws Exception {
		session.close();
		session = new GameSession(GameLinkSettings.of("localhost", FakeGameServer.PORT), "s3cret");
		assertTrue(session.requiresAdminPassword());

		FakeConnection guesser = new FakeConnection("guesser");
		assertFalse(session.claimAdmin(guesser, "admin"));
		assertEquals(GameSession.WRONG_PASSWORD, guesser.next("rejected").get("reason").getAsString());

		assertTrue(session.claimAdmin(admin, "s3cret"));
		admin.next("admin-welcome");

		FakeConnection reconnected = new FakeConnection("admin-2");
		assertTrue(session.claimAdmin(reconnected, "s3cret"));
		reconnected.next("admin-welcome");
		assertEquals(GameSession.ADMIN_REPLACED, admin.next("rejected").get("reason").getAsString());
		assertFalse(admin.isOpen());

		session.join(taza, "Taza");
		session.command(admin, Command.START);
		assertTrue(admin.receivesNo("ack", 200));
		assertEquals(GameState.LOBBY, session.state());
	}

	@Test
	void ignoresCommandsFromSessionsThatAreNotAdmin() throws Exception {
		session.claimAdmin(admin, "");
		session.join(taza, "Taza");
		session.command(taza, Command.START);
		session.command(taza, Command.STOP);
		assertTrue(taza.receivesNo("ack", 300));
		assertTrue(admin.ofType("ack").isEmpty());
		assertTrue(game.events().isEmpty(), "le jeu ne doit pas être contacté");
		assertEquals(GameState.LOBBY, session.state());
	}

	@Test
	void reportsCommandErrorsInsteadOfCrashing() throws Exception {
		session.claimAdmin(admin, "");
		session.command(admin, Command.START);
		ack(admin, "start", false, GameSession.NO_PLAYER);
		session.command(admin, Command.PAUSE);
		ack(admin, "pause", false, GameSession.NOT_RUNNING);
		session.command(admin, Command.RESUME);
		ack(admin, "resume", false, GameSession.NOT_PAUSED);
		session.command(admin, Command.STOP);
		ack(admin, "stop", false, GameSession.NOTHING_TO_STOP);
		assertEquals(GameState.LOBBY, session.state());
	}

	@Test
	void reportsAnUnreachableGameAndAllowsARetry() throws Exception {
		game.close();
		game = null;
		session.claimAdmin(admin, "");
		session.join(taza, "Taza");

		session.command(admin, Command.START);
		ack(admin, "start", false, GameLink.UNREACHABLE);
		assertEquals(GameState.LOBBY, session.state());
		assertFalse(session.isStarting());
		assertEquals("lobby", taza.ofType("welcome").get(0).get("state").getAsString());
		assertTrue(taza.ofType("game").isEmpty());

		game = new FakeGameServer();
		startRound();
		assertEquals(List.of(new Participant(0, "Taza")), game.await(FakeGameServer.Handshake.class, 2000).participants());
		assertEquals("running", taza.next("game").get("state").getAsString());
	}

	@Test
	void reportsRefusedGames() throws Exception {
		game.accept(false);
		session.claimAdmin(admin, "");
		session.join(taza, "Taza");
		session.command(admin, Command.START);
		ack(admin, "start", false, GameLink.REFUSED);
		assertEquals(GameState.LOBBY, session.state());
	}

	@Test
	void playsAWholeRound() throws Exception {
		session.claimAdmin(admin, "");
		Player p0 = session.join(taza, "Taza");
		Player p1 = session.join(emile, "Émile");
		game.state(SnapshotBytes.battle().counts(2, 2).secondsLeft(12)
			.player(0, 1, 87, 640, 360, 2, 0)
			.player(1, 1, 55, 100, 200, 0, 0)
			.build());

		startRound();
		assertEquals(List.of(new Participant(0, "Taza"), new Participant(1, "Émile")),
			game.await(FakeGameServer.Handshake.class, 2000).participants());
		assertEquals("running", taza.next("game").get("state").getAsString());
		assertEquals("running", admin.next("game").get("state").getAsString());

		JsonObject state = taza.next("state");
		assertEquals("alive", state.get("status").getAsString());
		assertEquals(87, state.get("life").getAsInt());
		assertEquals(100, state.get("maxLife").getAsInt());
		assertEquals(640, state.get("x").getAsInt());
		assertEquals(2, state.get("kills").getAsInt());
		assertEquals(2, state.get("total").getAsInt());
		assertEquals("battle", state.get("phase").getAsString());
		assertEquals(12, state.get("secondsLeft").getAsInt());
		assertEquals(1280, state.getAsJsonObject("map").get("width").getAsInt());
		assertEquals(55, emile.next("state").get("life").getAsInt());

		assertNull(session.join(new FakeConnection("late"), "Retard"));

		session.move(taza, p0, 2, 4);
		session.attack(emile, p1, 1);
		awaitActions(action(0, 0, 2, 4), action(1, 1, 1));
		session.move(emile, p0, 6, 4);

		session.command(admin, Command.PAUSE);
		ack(admin, "pause", true, null);
		assertEquals(GameLink.CODE_PAUSE, game.awaitControl(2000).code());
		assertEquals("paused", emile.next("game").get("state").getAsString());
		session.attack(taza, p0, 2);

		session.command(admin, Command.RESUME);
		ack(admin, "resume", true, null);
		assertEquals(GameLink.CODE_RESUME, game.awaitControl(2000).code());
		assertEquals("running", emile.next("game").get("state").getAsString());

		game.finish(SnapshotBytes.battle().phase(2).counts(1, 2).winner(1)
			.player(0, 0, 0, 640, 360, 2, 2)
			.player(1, 2, 30, 100, 200, 1, 1)
			.build());
		assertEquals("over", emile.next("game").get("state").getAsString());
		JsonObject end = taza.next("end");
		assertEquals(List.of("running", "paused", "running", "over"),
			taza.ofType("game").stream().map(m -> m.get("state").getAsString()).toList());
		assertEquals(1, end.getAsJsonObject("winner").get("id").getAsInt());
		assertEquals("Émile", end.getAsJsonObject("winner").get("pseudo").getAsString());
		JsonArray ranking = end.getAsJsonArray("ranking");
		assertEquals(1, ranking.get(0).getAsJsonObject().get("id").getAsInt());
		assertEquals(1, ranking.get(0).getAsJsonObject().get("rank").getAsInt());
		assertEquals("Taza", ranking.get(1).getAsJsonObject().get("pseudo").getAsString());
		assertEquals(2, ranking.get(1).getAsJsonObject().get("kills").getAsInt());
		assertEquals(end, admin.next("end"));
		assertEquals(GameState.OVER, session.state());

		List<JsonObject> states = taza.ofType("state");
		assertEquals("eliminated", states.get(states.size() - 1).get("status").getAsString());

		FakeConnection replay = new FakeConnection("replay");
		taza.drop();
		session.disconnect(taza, p0);
		assertSame(p0, session.join(replay, "Taza"));
		assertEquals("over", replay.next("welcome").get("state").getAsString());
		assertEquals(end, replay.next("end"));

		game.state(SnapshotBytes.battle().counts(2, 2).player(0, 1, 100, 1, 1, 0, 0).player(1, 1, 100, 2, 2, 0, 0).build());
		startRound();
		assertEquals(List.of(new Participant(0, "Taza"), new Participant(1, "Émile")),
			game.await(FakeGameServer.Handshake.class, 2000).participants());
	}

	@Test
	void reconnectingPlayersLearnThatTheGameStarted() throws Exception {
		session.claimAdmin(admin, "");
		Player p0 = session.join(taza, "Taza");
		game.state(SnapshotBytes.battle().counts(1, 1).player(0, 1, 64, 10, 20, 0, 0).build());
		startRound();
		taza.next("state");

		session.move(taza, p0, 4, 3);
		awaitActions(action(0, 0, 4, 3));
		taza.drop();
		session.disconnect(taza, p0);
		awaitActions(action(0, 0, 0, 0));

		FakeConnection phone = new FakeConnection("phone");
		assertSame(p0, session.join(phone, "Taza"));
		JsonObject welcome = phone.next("welcome");
		assertEquals("running", welcome.get("state").getAsString());
		assertEquals(0, welcome.get("id").getAsInt());
		assertEquals(64, phone.next("state", 500).get("life").getAsInt());

		session.move(taza, p0, 1, 1);
		session.move(phone, p0, 1, 2);
		awaitActions(action(0, 0, 1, 2));
		assertArrayEquals(new byte[] { 0, 0, 1, 2 }, game.awaitActions(2000), "le rafraîchissement garde la dernière intention");
	}

	@Test
	void throttlesThePlayersListDuringTheGame() throws Exception {
		session.claimAdmin(admin, "");
		session.join(taza, "Taza");
		startRound();

		admin.skipAll();
		int before = admin.ofType("players").size();
		for (int life = 100; life > 60; life--) {
			game.state(SnapshotBytes.battle().counts(1, 1).player(0, 1, life, 10, 10, 0, 0).build());
			Thread.sleep(30);
		}
		int sent = admin.ofType("players").size() - before;
		assertTrue(sent >= 1 && sent <= 4, sent + " listes envoyées en ~1,2 s");
		assertTrue(taza.ofType("state").size() >= 15, "états envoyés au joueur : " + taza.ofType("state").size());
	}

	@Test
	void stopsTheRoundAndAllowsANewOne() throws Exception {
		session.claimAdmin(admin, "");
		session.join(taza, "Taza");
		startRound();
		assertEquals("running", taza.next("game").get("state").getAsString());
		game.await(FakeGameServer.Actions.class, 2000);

		session.command(admin, Command.STOP);
		ack(admin, "stop", true, null);
		assertEquals(GameLink.CODE_STOP, game.awaitControl(2000).code());
		assertEquals("stopped", taza.next("game").get("state").getAsString());
		assertEquals(GameState.STOPPED, session.state());
		game.await(FakeGameServer.Closed.class, 2000);

		Player newcomer = session.join(emile, "Émile");
		assertEquals(1, newcomer.getId());
		startRound();
		assertEquals(2, game.await(FakeGameServer.Handshake.class, 2000).participants().size());
	}

	@Test
	void dropsPlayersAbsentAtLaunch() throws Exception {
		session.claimAdmin(admin, "");
		Player p0 = session.join(taza, "Taza");
		Player p1 = session.join(emile, "Émile");
		emile.drop();
		session.disconnect(emile, p1);

		startRound();
		assertEquals(List.of(new Participant(p0.getId(), "Taza")),
			game.await(FakeGameServer.Handshake.class, 2000).participants());
		assertEquals(1, session.playerEntries().size());
	}

	@Test
	void stopsTheRoundWhenTheGameDisappears() throws Exception {
		session.claimAdmin(admin, "");
		session.join(taza, "Taza");
		startRound();
		assertEquals("running", taza.next("game").get("state").getAsString());
		game.dropNext();
		assertEquals("stopped", taza.next("game").get("state").getAsString());
		assertEquals(GameState.STOPPED, session.state());
	}

	@Test
	void cancelsAPendingLaunch() throws Exception {
		game.silentHandshake(true);
		session.claimAdmin(admin, "");
		session.join(taza, "Taza");
		session.command(admin, Command.START);
		game.await(FakeGameServer.Handshake.class, 2000);
		assertTrue(session.isStarting());

		session.command(admin, Command.START);
		ack(admin, "start", false, GameSession.ALREADY_STARTING);
		session.command(admin, Command.STOP);
		ack(admin, "start", false, GameSession.START_CANCELLED);
		ack(admin, "stop", true, null);
		assertFalse(session.isStarting());
		assertEquals(GameState.LOBBY, session.state());
	}
}
