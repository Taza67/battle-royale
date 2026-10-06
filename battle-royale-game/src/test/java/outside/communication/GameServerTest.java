package outside.communication;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import inside.Board;
import inside.Board.PlayerSpec;
import inside.GameSettings;
import inside.IConfig;
import inside.Phase;

/**
 * Faux serveur web qui se connecte au serveur TCP du jeu (ports 38200 à 38219 uniquement)
 */
@Timeout(20)
class GameServerTest implements IConfig {
	private GameServer server;
	private final AtomicReference<Board> board = new AtomicReference<>();
	private final List<String> statuses = new CopyOnWriteArrayList<>();
	private final CountDownLatch lost = new CountDownLatch(1);

	private GameServer start(int port) {
		return start(GameServer.DEFAULT_BIND_ADDRESS, port);
	}

	private GameServer start(String bind, int port) {
		server = new GameServer(bind, port, new GameServer.Listener() {
			@Override
			public Board onGameRequested(List<PlayerSpec> players) {
				Board b = new Board(GameSettings.defaults(1).withWarmup(0), players);
				board.set(b);
				return b;
			}

			@Override
			public void onStatus(String message, boolean error) {
				statuses.add(message);
			}

			@Override
			public void onConnectionLost(Board b) {
				lost.countDown();
			}
		});
		server.start();
		return server;
	}

	@AfterEach
	void close() {
		if (server != null) server.close();
	}

	private static Socket connect(int port) throws IOException, InterruptedException {
		return connect("127.0.0.1", port);
	}

	private static Socket connect(String host, int port) throws IOException, InterruptedException {
		for (int i = 0; i < 100; i++) {
			try {
				return new Socket(host, port);
			} catch (ConnectException e) {
				Thread.sleep(20);
			}
		}
		throw new ConnectException("serveur du jeu injoignable sur " + port);
	}

	private static void handshake(DataOutputStream out, Object... players) throws IOException {
		out.writeInt(0);
		out.writeInt(players.length / 2);
		for (int i = 0; i < players.length; i += 2) {
			out.writeByte((Integer)players[i]);
			out.writeUTF((String)players[i + 1]);
		}
		out.flush();
	}

	private static byte[] exchange(DataOutputStream out, DataInputStream in, byte[] actions, boolean[] running) throws IOException {
		out.writeInt(actions.length);
		out.write(actions);
		out.flush();
		byte[] state = new byte[in.readInt()];
		in.readFully(state);
		running[0] = in.readBoolean();
		return state;
	}

	/**
	 * Fait avancer la simulation (rôle de la boucle principale) jusqu'à ce que la condition soit vraie
	 */
	private static void tickUntil(Board b, BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 5000;
		while (!condition.getAsBoolean()) {
			assertTrue(System.currentTimeMillis() < deadline, "délai dépassé");
			b.tick();
			Thread.sleep(2);
		}
	}

	@Test
	void partieCompleteAvecPauseRepriseEtArret() throws Exception {
		start(38200);
		try (Socket s = connect(38200)) {
			DataOutputStream out = new DataOutputStream(s.getOutputStream());
			DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));

			handshake(out, 3, "Alice", 7, "Bob");
			assertTrue(in.readBoolean(), "partie acceptée");
			Board b = board.get();
			assertNotNull(b);
			assertEquals(2, b.getPlayers().size());
			assertEquals("Alice", b.getPlayer(3).getPseudo());

			boolean[] running = new boolean[1];
			byte[] state = exchange(out, in, new byte[0], running);
			assertTrue(running[0]);
			assertEquals(Protocol.HEADER_SIZE + 2 * Protocol.PLAYER_SIZE, state.length);
			assertEquals(3, state[Protocol.HEADER_SIZE]);
			assertEquals(7, state[Protocol.HEADER_SIZE + Protocol.PLAYER_SIZE]);

			// Déplacement de Alice vers l'est
			float x = b.getPlayer(3).getX();
			exchange(out, in, Protocol.encodeMove(3, EAST, 4), running);
			b.tick();
			b.tick();
			assertTrue(b.getPlayer(3).getX() > x || b.getPlayer(3).getX() >= MAP_WIDTH - PLAYER_RADIUS_X - 1);
			assertEquals(Phase.BATTLE, b.getPhase());

			// Pause puis reprise : aucune réponse attendue
			out.writeInt(Protocol.PAUSE);
			out.flush();
			tickUntil(b, b::isPaused);
			long frozen = b.getTick();
			for (int i = 0; i < 10; i++) b.tick();
			assertEquals(frozen, b.getTick());
			assertTrue(b.getSnapshot().paused());

			out.writeInt(Protocol.RESUME);
			out.flush();
			tickUntil(b, () -> !b.isPaused());

			// Les échanges continuent après la reprise
			state = exchange(out, in, Protocol.encodeAttack(7, ATTACK_SHOOT), running);
			assertTrue(running[0]);
			assertEquals(1, state[0]);

			// Arrêt demandé par l'administrateur
			out.writeInt(Protocol.STOP);
			out.flush();
			tickUntil(b, b::isOver);
			state = exchange(out, in, new byte[0], running);
			assertFalse(running[0], "enCours vaut false après l'arrêt");
			assertEquals(2, state[0], "phase terminée");
		}
		assertTrue(statuses.stream().anyMatch(m -> m.startsWith("Partie lancée")));
	}

	@Test
	void poigneeDeMainRefuseeAvecIdentifiantsEnDouble() throws Exception {
		start(38201);
		try (Socket s = connect(38201)) {
			DataOutputStream out = new DataOutputStream(s.getOutputStream());
			DataInputStream in = new DataInputStream(s.getInputStream());
			handshake(out, 1, "A", 1, "B");
			assertFalse(in.readBoolean());
			assertThrows(EOFException.class, in::readInt, "connexion fermée après le refus");
		}
		assertNull(board.get());

		// Le serveur accepte une nouvelle connexion ensuite
		try (Socket s = connect(38201)) {
			DataOutputStream out = new DataOutputStream(s.getOutputStream());
			DataInputStream in = new DataInputStream(s.getInputStream());
			handshake(out, 1, "A", 2, "B");
			assertTrue(in.readBoolean());
		}
	}

	@Test
	void perteDeConnexionSignalee() throws Exception {
		start(38202);
		try (Socket s = connect(38202)) {
			DataOutputStream out = new DataOutputStream(s.getOutputStream());
			DataInputStream in = new DataInputStream(s.getInputStream());
			handshake(out, 0, "A", 1, "B");
			assertTrue(in.readBoolean());
		}
		assertTrue(lost.await(5, TimeUnit.SECONDS), "perte de connexion signalée");
		assertTrue(statuses.contains("Connexion avec le serveur web perdue"));
	}

	@Test
	void fermetureDuServeurLibereLePort() throws Exception {
		start(38203);
		connect(38203).close();
		server.close();
		server = null;

		start(38203);
		try (Socket s = connect(38203)) {
			assertTrue(s.isConnected());
		}
	}

	@Test
	void ecouteSeulementEnLocalParDefaut() throws Exception {
		String lan = NetworkUtilities.lanIPv4();
		assumeFalse(lan.equals("localhost"), "aucune adresse réseau locale");

		start(38204);
		connect(38204).close();
		assertThrows(ConnectException.class, () -> new Socket(lan, 38204).close(), "injoignable depuis " + lan);
		server.close();
		server = null;

		start("0.0.0.0", 38204);
		try (Socket s = connect(lan, 38204)) {
			assertTrue(s.isConnected());
		}
	}

	@Test
	void poigneeDeMainAuCompteGouttesFermeeApres5s() throws Exception {
		start(38205);
		try (Socket s = connect(38205)) {
			DataOutputStream out = new DataOutputStream(s.getOutputStream());
			long begin = System.nanoTime();
			s.setSoTimeout(10_000);
			Thread sender = new Thread(() -> {
				try {
					out.writeInt(0);
					out.writeInt(1);
					out.writeByte(1);
					out.flush();
					for (byte octet : new byte[] { 0, 5, 'A', 'l', 'i', 'c', 'e' }) {
						Thread.sleep(1000);
						out.writeByte(octet);
						out.flush();
					}
				} catch (IOException | InterruptedException e) {
					// Connexion fermée par le jeu
				}
			});
			sender.setDaemon(true);
			sender.start();

			assertEquals(-1, s.getInputStream().read(), "connexion fermée par le jeu");
			long elapsed = (System.nanoTime() - begin) / 1_000_000;
			assertTrue(elapsed >= GameServer.HANDSHAKE_TIMEOUT_MS - 200 && elapsed < GameServer.HANDSHAKE_TIMEOUT_MS + 1500,
				"fermée après " + elapsed + " ms");
			sender.join(3000);
		}
		assertNull(board.get());
		assertTrue(statuses.stream().anyMatch(m -> m.startsWith("Poignée de main non terminée")), statuses.toString());

		// Une poignée de main normale reste possible ensuite et la boucle n'est plus limitée dans le temps
		try (Socket s = connect(38205)) {
			DataOutputStream out = new DataOutputStream(s.getOutputStream());
			DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
			handshake(out, 1, "A", 2, "B");
			assertTrue(in.readBoolean());
			Thread.sleep(GameServer.HANDSHAKE_TIMEOUT_MS + 500);
			boolean[] running = new boolean[1];
			exchange(out, in, new byte[0], running);
			assertTrue(running[0]);
		}
	}
}
