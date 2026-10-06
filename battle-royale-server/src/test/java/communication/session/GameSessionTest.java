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
import communication.message.ServerMessage;

class GameSessionTest {
	private FakeGameServer game;
	private GameSession session;
	private final FakeConnection admin = new FakeConnection("admin");
	private final FakeConnection taza = new FakeConnection("taza");
	private final FakeConnection emile = new FakeConnection("emile");
	private Player emilePlayer;

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

	private static List<String> states(FakeConnection c) {
		return c.ofType("game").stream().map(m -> m.get("state").getAsString()).toList();
	}

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
	void givesEachPseudoARandomStableResumeToken() throws Exception {
		session.join(taza, "Taza");
		String token = taza.next("welcome").get("token").getAsString();
		assertTrue(token.matches("[0-9a-f]{32}"), token);
		session.join(emile, "Émile");
		String other = emile.next("welcome").get("token").getAsString();
		assertTrue(other.matches("[0-9a-f]{32}"), other);
		assertFalse(token.equals(other));

		taza.drop();
		FakeConnection back = new FakeConnection("back");
		session.join(back, "TAZA");
		assertEquals(token, back.next("welcome").get("token").getAsString());
	}

	@Test
	void letsTheRightTokenTakeOverAnOpenSession() throws Exception {
		assertTrue(session.claimAdmin(admin, ""));
		Player p = session.join(taza, "Taza");
		String token = taza.next("welcome").get("token").getAsString();
		taza.skipAll();

		FakeConnection resumed = new FakeConnection("resumed");
		assertSame(p, session.join(resumed, "taza", token));
		JsonObject welcome = resumed.next("welcome");
		assertEquals(0, welcome.get("id").getAsInt());
		assertEquals(token, welcome.get("token").getAsString());
		assertEquals(GameSession.SESSION_TAKEN_OVER, taza.next("rejected").get("reason").getAsString());
		assertFalse(taza.isOpen());
		assertEquals(GameSession.SESSION_TAKEN_OVER, taza.closeReason());
		assertSame(resumed, p.connection());

		// La fermeture de l'ancienne session ne déconnecte pas le joueur repris
		session.disconnect(taza, p);
		assertSame(resumed, p.connection());
		assertTrue(session.playerEntries().get(0).connected());
	}

	@Test
	void keepsRefusingAnOpenPseudoWithAWrongOrMissingToken() throws Exception {
		session.join(taza, "Taza");
		String token = taza.next("welcome").get("token").getAsString();
		session.join(emile, "Émile");
		String emileToken = emile.next("welcome").get("token").getAsString();

		for (String wrong : new String[] { null, "", emileToken, token.toUpperCase(), token + "0" }) {
			FakeConnection intruder = new FakeConnection("intruder");
			assertNull(session.join(intruder, "Taza", wrong));
			assertEquals(GameSession.PSEUDO_TAKEN, intruder.next("rejected").get("reason").getAsString());
			assertTrue(taza.isOpen());
		}
	}

	@Test
	void neverSendsTokensInPlayersOrEnd() throws Exception {
		assertTrue(session.claimAdmin(admin, ""));
		session.join(taza, "Taza");
		String token = taza.next("welcome").get("token").getAsString();
		startRound();
		game.finish(SnapshotBytes.battle().phase(2).counts(1, 1).winner(0).player(0, 2, 100, 10, 10, 0, 1).build());
		admin.next("end");
		taza.next("end");
		for (JsonObject m : admin.all())
			assertFalse(m.toString().contains(token), m.toString());
		for (JsonObject m : taza.all())
			if (!"welcome".equals(m.get("type").getAsString()))
				assertFalse(m.toString().contains(token), m.toString());
	}

	@Test
	void closesTheSessionAfterFiveWrongAdminPasswords() throws Exception {
		session.close();
		session = new GameSession(GameLinkSettings.of("localhost", FakeGameServer.PORT), "s3cret");
		FakeConnection guesser = new FakeConnection("guesser");
		for (int i = 1; i < GameSession.MAX_WRONG_PASSWORDS; i++) {
			assertFalse(session.claimAdmin(guesser, "essai" + i));
			assertEquals(GameSession.WRONG_PASSWORD, guesser.next("rejected").get("reason").getAsString());
			assertTrue(guesser.isOpen());
		}
		assertFalse(session.claimAdmin(guesser, "essai5"));
		assertEquals(GameSession.WRONG_PASSWORD, guesser.next("rejected").get("reason").getAsString());
		assertFalse(guesser.isOpen());
		assertEquals(GameSession.TOO_MANY_WRONG_PASSWORDS, guesser.closeReason());

		// La fenêtre est globale : fermer sa session ne réinitialise pas le compteur
		FakeConnection other = new FakeConnection("other");
		assertFalse(session.claimAdmin(other, "mauvais"));
		assertFalse(other.isOpen());
		assertEquals(GameSession.TOO_MANY_WRONG_PASSWORDS, other.closeReason());
		// Le bon mot de passe reste accepté
		FakeConnection legit = new FakeConnection("legit");
		assertTrue(session.claimAdmin(legit, "s3cret"));
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
		assertEquals(2, end.get("total").getAsInt());
		assertFalse(end.get("stopped").getAsBoolean());
		assertEquals("over", admin.next("game").get("state").getAsString());
		assertEquals(end, admin.next("end"));
		JsonArray finalPlayers = admin.next("players").getAsJsonArray("players");
		assertEquals(2, finalPlayers.get(0).getAsJsonObject().get("rank").getAsInt());
		assertEquals("winner", finalPlayers.get(1).getAsJsonObject().get("status").getAsString());
		assertEquals(30, finalPlayers.get(1).getAsJsonObject().get("life").getAsInt());
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
	void sendsEveryChangeToTheAdminWithinHalfASecond() throws Exception {
		session.claimAdmin(admin, "");
		session.join(taza, "Taza");
		game.state(SnapshotBytes.battle().counts(1, 1).player(0, 1, 90, 10, 10, 0, 0).build());
		startRound();
		awaitAdminLife(90, 1000);

		for (int life : new int[] { 80, 79, 50, 49 }) {
			long begin = System.nanoTime();
			game.state(SnapshotBytes.battle().counts(1, 1).player(0, 1, life, 10, 10, 0, 0).build());
			awaitAdminLife(life, GameSession.PLAYERS_INTERVAL_MILLIS + 300);
			long elapsed = (System.nanoTime() - begin) / 1_000_000;
			assertTrue(elapsed <= GameSession.PLAYERS_INTERVAL_MILLIS + 300, "vie " + life + " reçue après " + elapsed + " ms");
		}
		game.state(SnapshotBytes.battle().counts(0, 1).player(0, 0, 0, 10, 10, 0, 1).build());
		long deadline = System.currentTimeMillis() + GameSession.PLAYERS_INTERVAL_MILLIS + 300;
		JsonObject entry;
		do {
			entry = admin.next("players", Math.max(1, deadline - System.currentTimeMillis()))
				.getAsJsonArray("players").get(0).getAsJsonObject();
		} while (!"eliminated".equals(entry.get("status").getAsString()));
	}

	private void awaitAdminLife(int life, long timeoutMillis) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMillis;
		while (true) {
			JsonObject entry = admin.next("players", Math.max(1, deadline - System.currentTimeMillis()))
				.getAsJsonArray("players").get(0).getAsJsonObject();
			if (entry.get("life").getAsInt() == life)
				return;
		}
	}

	@Test
	void stopsTheRoundAndAllowsANewOne() throws Exception {
		session.claimAdmin(admin, "");
		Player p0 = session.join(taza, "Taza");
		game.state(SnapshotBytes.battle().counts(3, 3).player(0, 1, 90, 10, 10, 0, 0).build());
		startRound();
		assertEquals("running", taza.next("game").get("state").getAsString());
		game.await(FakeGameServer.Actions.class, 2000);
		game.stopState(SnapshotBytes.battle().phase(2).counts(1, 3).winner(-1)
			.player(0, 0, 0, 10, 10, 1, 2)
			.build());

		session.command(admin, Command.STOP);
		ack(admin, "stop", true, null);
		assertEquals(GameLink.CODE_STOP, game.awaitControl(2000).code());
		assertEquals("stopped", taza.next("game").get("state").getAsString());
		assertEquals(GameState.STOPPED, session.state());

		JsonObject end = taza.next("end");
		assertTrue(end.get("stopped").getAsBoolean());
		assertEquals(3, end.get("total").getAsInt(), "robots compris");
		assertTrue(end.get("winner").isJsonNull());
		JsonObject first = end.getAsJsonArray("ranking").get(0).getAsJsonObject();
		assertEquals(2, first.get("rank").getAsInt());
		assertEquals(1, first.get("kills").getAsInt());
		assertEquals(end, admin.next("end"));
		assertEquals(List.of("running", "stopped"), states(admin));
		JsonObject entry = admin.next("players").getAsJsonArray("players").get(0).getAsJsonObject();
		assertEquals("eliminated", entry.get("status").getAsString());
		assertEquals(0, entry.get("life").getAsInt());
		assertEquals(2, entry.get("rank").getAsInt());
		assertEquals(1, entry.get("kills").getAsInt());
		game.await(FakeGameServer.Closed.class, 2000);

		FakeConnection phone = new FakeConnection("phone");
		taza.drop();
		session.disconnect(taza, p0);
		assertSame(p0, session.join(phone, "Taza"));
		assertEquals("stopped", phone.next("welcome").get("state").getAsString());
		assertEquals(end, phone.next("end"));
		FakeConnection admin2 = new FakeConnection("admin-2");
		session.releaseAdmin(admin);
		assertTrue(session.claimAdmin(admin2, ""));
		assertEquals("stopped", admin2.next("admin-welcome").get("state").getAsString());
		assertEquals(end, admin2.next("end"));

		Player newcomer = session.join(emile, "Émile");
		assertEquals(1, newcomer.getId());
		session.command(admin2, Command.START);
		ack(admin2, "start", true, null);
		assertEquals(2, game.await(FakeGameServer.Handshake.class, 2000).participants().size());
	}

	@Test
	void endsTheStoppedRoundWithTheLastStateWhenTheGameNeverFinishes() throws Exception {
		game.onStop(FakeGameServer.StopBehavior.IGNORE);
		session.claimAdmin(admin, "");
		session.join(taza, "Taza");
		game.state(SnapshotBytes.battle().counts(4, 4).player(0, 1, 70, 10, 10, 3, 0).build());
		startRound();
		assertEquals(70, taza.next("state").get("life").getAsInt());

		session.command(admin, Command.STOP);
		ack(admin, "stop", true, null);
		JsonObject end = admin.next("end", GameLink.STOP_TIMEOUT_MILLIS + 2000);
		assertEquals(List.of("running", "stopped"), states(admin));
		assertTrue(end.get("stopped").getAsBoolean());
		assertEquals(4, end.get("total").getAsInt());
		assertEquals(3, end.getAsJsonArray("ranking").get(0).getAsJsonObject().get("kills").getAsInt());
		assertEquals(end, taza.next("end"));
		game.await(FakeGameServer.Closed.class, 2000);
	}

	@Test
	void stopsAPausedRoundWithAResult() throws Exception {
		session.claimAdmin(admin, "");
		session.join(taza, "Taza");
		startRound();
		session.command(admin, Command.PAUSE);
		ack(admin, "pause", true, null);
		assertEquals(GameLink.CODE_PAUSE, game.awaitControl(2000).code());
		session.command(admin, Command.STOP);
		ack(admin, "stop", true, null);
		assertTrue(taza.next("end").get("stopped").getAsBoolean());
		assertEquals(GameState.STOPPED, session.state());
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

	/** Joue une manche complète avec Taza et Émile, terminée par la victoire d'Émile */
	private JsonObject playRoundWonByEmile() throws InterruptedException {
		session.claimAdmin(admin, "");
		session.join(taza, "Taza");
		emilePlayer = session.join(emile, "Émile");
		startRound();
		game.finish(SnapshotBytes.battle().phase(2).counts(1, 2).winner(1)
			.player(0, 0, 0, 640, 360, 2, 2)
			.player(1, 2, 30, 100, 200, 1, 1)
			.build());
		JsonObject end = admin.next("end");
		game.await(FakeGameServer.Closed.class, 2000);
		return end;
	}

	private void assertPreviousRoundIntact(JsonObject end) throws InterruptedException {
		assertEquals(GameState.OVER, session.state());
		List<ServerMessage.PlayerEntry> entries = session.playerEntries();
		assertEquals(2, entries.size(), "les absents ne sont retirés qu'au lancement effectif");
		assertEquals(2, entries.get(0).rank());
		assertEquals("winner", entries.get(1).status());
		assertEquals(30, entries.get(1).life());

		FakeConnection back = new FakeConnection("back");
		assertSame(emilePlayer, session.join(back, "Émile"));
		assertEquals("over", back.next("welcome").get("state").getAsString());
		assertEquals(end, back.next("end"));
		FakeConnection admin2 = new FakeConnection("admin-2");
		session.releaseAdmin(admin);
		session.claimAdmin(admin2, "");
		assertEquals(end, admin2.next("end"));
	}

	@Test
	void keepsThePreviousResultsWhenTheGameRefusesTheNextRound() throws Exception {
		JsonObject end = playRoundWonByEmile();
		emile.drop();
		session.disconnect(emile, emilePlayer);

		game.accept(false);
		session.command(admin, Command.START);
		ack(admin, "start", false, GameLink.REFUSED);
		assertPreviousRoundIntact(end);
	}

	@Test
	void keepsThePreviousResultsWhenTheNextLaunchIsCancelled() throws Exception {
		JsonObject end = playRoundWonByEmile();
		emile.drop();
		session.disconnect(emile, emilePlayer);

		game.silentHandshake(true);
		session.command(admin, Command.START);
		assertEquals(List.of(new Participant(0, "Taza")), game.await(FakeGameServer.Handshake.class, 2000).participants());
		FakeConnection early = new FakeConnection("early");
		assertNull(session.join(early, "Émile"), "absent au lancement : pas de retour pendant le démarrage");
		assertEquals(GameSession.REGISTRATION_CLOSED, early.next("rejected").get("reason").getAsString());
		session.command(admin, Command.STOP);
		ack(admin, "start", false, GameSession.START_CANCELLED);
		ack(admin, "stop", true, null);
		assertPreviousRoundIntact(end);
	}

	@Test
	void forgetsThePreviousRoundOnlyOnceTheGameAccepts() throws Exception {
		playRoundWonByEmile();
		emile.drop();
		session.disconnect(emile, emilePlayer);

		game.state(SnapshotBytes.battle().counts(1, 1).player(0, 1, 100, 1, 1, 0, 0).build());
		session.command(admin, Command.START);
		ack(admin, "start", true, null);
		assertEquals(1, session.playerEntries().size());
		assertEquals(0, session.playerEntries().get(0).rank());
		FakeConnection back = new FakeConnection("back");
		assertNull(session.join(back, "Émile"));
		assertEquals(GameSession.REGISTRATION_CLOSED, back.next("rejected").get("reason").getAsString());
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
		assertTrue(taza.next("end").get("stopped").getAsBoolean());
		admin.next("end");
		assertEquals(List.of("running", "stopped"), states(admin));
		admin.next("players");
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
