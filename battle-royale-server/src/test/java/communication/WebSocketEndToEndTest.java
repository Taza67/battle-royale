package communication;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import communication.game.FakeGameServer;
import communication.game.GameLink;
import communication.game.GameLinkSettings;
import communication.game.Participant;
import communication.game.SnapshotBytes;
import communication.message.Json;
import communication.session.GameSession;
import communication.session.GameState;

/**
 * Tests de bout en bout : Tomcat embarqué, vrais clients WebSocket et faux jeu TCP
 */
class WebSocketEndToEndTest {
	private static final int HTTP_PORT = 38232;
	private static final int UNUSED_GAME_PORT = FakeGameServer.UNUSED_PORT;
	private static final String BASE = "localhost:" + HTTP_PORT + ServerLauncher.CONTEXT_PATH;
	private static final String TEST_PASSWORD = "mot-de-passe-de-test";

	private final HttpClient http = HttpClient.newHttpClient();
	private final List<Client> clients = new ArrayList<>();
	private GameSession session;
	private Tomcat tomcat;
	private FakeGameServer game;

	private void startServer(int gamePort, String password) throws Exception {
		session = new GameSession(GameLinkSettings.of("localhost", gamePort), password);
		tomcat = ServerLauncher.start(HTTP_PORT, new File("src/main/webapp"), session);
	}

	@AfterEach
	void tearDown() throws Exception {
		for (Client c : clients)
			c.abort();
		if (tomcat != null)
			ServerLauncher.stop(tomcat, session);
		if (game != null)
			game.close();
	}

	/** Client WebSocket qui mémorise les messages reçus */
	private final class Client implements WebSocket.Listener {
		private final BlockingQueue<JsonObject> received = new LinkedBlockingQueue<>();
		private final CompletableFuture<Integer> closed = new CompletableFuture<>();
		private final StringBuilder partial = new StringBuilder();
		private final WebSocket socket;

		Client() throws Exception {
			socket = http.newWebSocketBuilder()
				.buildAsync(URI.create("ws://" + BASE + WebSocketServer.PATH), this)
				.get(5, TimeUnit.SECONDS);
			clients.add(this);
		}

		@Override
		public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
			partial.append(data);
			if (last) {
				received.add(Json.GSON.fromJson(partial.toString(), JsonObject.class));
				partial.setLength(0);
			}
			ws.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
			closed.complete(statusCode);
			return null;
		}

		@Override
		public void onError(WebSocket ws, Throwable error) {
			closed.complete(-1);
		}

		Client send(String json) throws Exception {
			socket.sendText(json, true).get(5, TimeUnit.SECONDS);
			return this;
		}

		/** Attend le prochain message du type donné en ignorant les autres */
		JsonObject next(String type) throws InterruptedException {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
			while (true) {
				JsonObject m = received.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
				if (m == null)
					throw new AssertionError("aucun message " + type + " reçu");
				if (type.equals(m.get("type").getAsString()))
					return m;
			}
		}

		/** Vérifie qu'aucun message du type donné n'arrive pendant le délai */
		boolean receivesNo(String type, long millis) throws InterruptedException {
			long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
			JsonObject m;
			while ((m = received.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)) != null)
				if (type.equals(m.get("type").getAsString()))
					return false;
			return true;
		}

		void close() throws Exception {
			socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(5, TimeUnit.SECONDS);
			closed.get(5, TimeUnit.SECONDS);
		}

		void abort() {
			socket.abort();
		}
	}

	private static String join(String pseudo) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "join");
		o.addProperty("pseudo", pseudo);
		return o.toString();
	}

	private static String adminJoin(String password) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "admin-join");
		o.addProperty("password", password);
		return o.toString();
	}

	private static String command(String name) {
		return "{\"type\":\"admin-command\",\"command\":\"" + name + "\"}";
	}

	@Test
	void servesTheWebClients() throws Exception {
		startServer(UNUSED_GAME_PORT, null);
		for (String path : new String[] { "/gamepad/", "/adminPanel/" }) {
			HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("http://" + BASE + path)).build(),
				HttpResponse.BodyHandlers.ofString());
			assertEquals(200, r.statusCode(), path);
			assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("text/html"), path);
		}
	}

	@Test
	void registersPlayersAndRejectsDuplicateOrInvalidPseudos() throws Exception {
		startServer(UNUSED_GAME_PORT, null);
		Client taza = new Client();
		taza.send("pas du json").send("{\"type\":\"danse\"}").send("{\"type\":\"move\",\"direction\":9,\"speed\":1}");
		taza.send(join("  Taza  "));
		JsonObject welcome = taza.next("welcome");
		assertEquals(0, welcome.get("id").getAsInt());
		assertEquals("Taza", welcome.get("pseudo").getAsString());
		assertEquals("lobby", welcome.get("state").getAsString());

		Client twin = new Client().send(join("TAZA"));
		assertEquals(GameSession.PSEUDO_TAKEN, twin.next("rejected").get("reason").getAsString());

		Client tooLong = new Client().send(join("a".repeat(17)));
		assertTrue(tooLong.next("rejected").get("reason").getAsString().startsWith(WebSocketServer.INVALID_PSEUDO));
		tooLong.send(join("Émile\u00e9\u00e9"));
		assertEquals(1, tooLong.next("welcome").get("id").getAsInt());

		Client hostile = new Client().send(join("<script>\"x\""));
		assertEquals("<script>\"x\"", hostile.next("welcome").get("pseudo").getAsString());

		taza.close();
		Client back = new Client().send(join("taza"));
		assertEquals(0, back.next("welcome").get("id").getAsInt());
	}

	@Test
	void authenticatesTheAdminAndLetsItReconnect() throws Exception {
		startServer(UNUSED_GAME_PORT, TEST_PASSWORD);
		Client guesser = new Client().send(adminJoin("admin"));
		assertEquals(GameSession.WRONG_PASSWORD, guesser.next("rejected").get("reason").getAsString());

		Client admin = new Client().send(adminJoin(TEST_PASSWORD));
		assertEquals("lobby", admin.next("admin-welcome").get("state").getAsString());
		assertEquals(0, admin.next("players").getAsJsonArray("players").size());

		new Client().send(join("Taza")).next("welcome");
		assertEquals("Taza", admin.next("players").getAsJsonArray("players").get(0).getAsJsonObject().get("pseudo").getAsString());

		Client reconnected = new Client().send(adminJoin(TEST_PASSWORD));
		reconnected.next("admin-welcome");
		assertEquals(1, reconnected.next("players").getAsJsonArray("players").size());
		assertEquals(GameSession.ADMIN_REPLACED, admin.next("rejected").get("reason").getAsString());
		assertEquals(1000, admin.closed.get(3, TimeUnit.SECONDS));
	}

	@Test
	void givesTheAdminSeatToTheFirstClaimantWithoutPassword() throws Exception {
		startServer(UNUSED_GAME_PORT, null);
		Client admin = new Client().send("{\"type\":\"admin-join\"}");
		admin.next("admin-welcome");
		Client other = new Client().send(adminJoin(""));
		assertEquals(GameSession.ADMIN_TAKEN, other.next("rejected").get("reason").getAsString());

		admin.close();
		Client again = new Client().send(adminJoin(""));
		again.next("admin-welcome");
	}

	@Test
	void ignoresAdminCommandsFromPlayers() throws Exception {
		game = new FakeGameServer();
		startServer(FakeGameServer.PORT, null);
		Client admin = new Client().send(adminJoin(""));
		admin.next("admin-welcome");
		Client taza = new Client().send(join("Taza"));
		taza.next("welcome");

		taza.send(command("start")).send(adminJoin(""));
		Client stranger = new Client().send(command("start"));
		assertTrue(taza.receivesNo("ack", 400));
		assertTrue(stranger.receivesNo("ack", 50));
		assertTrue(admin.receivesNo("ack", 50));
		assertTrue(game.events().isEmpty(), "le jeu ne doit pas être contacté");
		assertEquals(GameState.LOBBY, session.state());
	}

	@Test
	void acknowledgesAnUnreachableGame() throws Exception {
		startServer(UNUSED_GAME_PORT, null);
		Client admin = new Client().send(adminJoin(""));
		admin.next("admin-welcome");
		admin.send(command("start"));
		JsonObject noPlayer = admin.next("ack");
		assertFalse(noPlayer.get("ok").getAsBoolean());
		assertEquals(GameSession.NO_PLAYER, noPlayer.get("error").getAsString());

		new Client().send(join("Taza")).next("welcome");
		admin.send(command("start"));
		JsonObject ack = admin.next("ack");
		assertEquals("start", ack.get("command").getAsString());
		assertFalse(ack.get("ok").getAsBoolean());
		assertEquals(GameLink.UNREACHABLE, ack.get("error").getAsString());

		admin.send(command("pause"));
		assertEquals(GameSession.NOT_RUNNING, admin.next("ack").get("error").getAsString());
	}

	@Test
	void reconnectsPlayersAfterTheGameStarted() throws Exception {
		game = new FakeGameServer();
		game.state(SnapshotBytes.battle().counts(1, 1).secondsLeft(42).player(0, 1, 77, 300, 400, 0, 0).build());
		startServer(FakeGameServer.PORT, null);
		Client admin = new Client().send(adminJoin(""));
		admin.next("admin-welcome");
		Client taza = new Client().send(join("Taza"));
		taza.next("welcome");

		admin.send(command("start"));
		JsonObject ack = admin.next("ack");
		assertTrue(ack.get("ok").getAsBoolean(), ack.toString());
		assertTrue(ack.get("error").isJsonNull());
		assertEquals(List.of(new Participant(0, "Taza")), game.await(FakeGameServer.Handshake.class, 2000).participants());
		assertEquals("running", taza.next("game").get("state").getAsString());
		assertEquals(77, taza.next("state").get("life").getAsInt());

		taza.close();
		Client late = new Client().send(join("Nouveau"));
		assertEquals(GameSession.REGISTRATION_CLOSED, late.next("rejected").get("reason").getAsString());

		Client phone = new Client().send(join("Taza"));
		JsonObject welcome = phone.next("welcome");
		assertEquals("running", welcome.get("state").getAsString());
		assertEquals(0, welcome.get("id").getAsInt());
		JsonObject state = phone.next("state");
		assertEquals(77, state.get("life").getAsInt());
		assertEquals(42, state.get("secondsLeft").getAsInt());
		assertEquals(1280, state.getAsJsonObject("map").get("width").getAsInt());

		phone.send("{\"type\":\"move\",\"direction\":3,\"speed\":2}");
		assertArrayEquals(new byte[] { 0, 0, 3, 2 }, game.awaitActions(2000));

		admin.send(command("stop"));
		assertTrue(admin.next("ack").get("ok").getAsBoolean());
		assertEquals(GameLink.CODE_STOP, game.awaitControl(2000).code());
		assertEquals("stopped", phone.next("game").get("state").getAsString());
	}
}
