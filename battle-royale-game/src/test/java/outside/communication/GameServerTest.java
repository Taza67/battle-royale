package outside.communication;

import static org.junit.jupiter.api.Assertions.*;

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
 * Faux serveur web qui se connecte au serveur TCP du jeu (ports 38000 à 38099 uniquement)
 */
@Timeout(20)
class GameServerTest implements IConfig {
	private GameServer server;
	private final AtomicReference<Board> board = new AtomicReference<>();
	private final List<String> statuses = new CopyOnWriteArrayList<>();
	private final CountDownLatch lost = new CountDownLatch(1);

	private GameServer start(int port) {
		server = new GameServer(port, new GameServer.Listener() {
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
		for (int i = 0; i < 100; i++) {
			try {
				return new Socket("127.0.0.1", port);
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
		start(38000);
		try (Socket s = connect(38000)) {
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
		start(38001);
		try (Socket s = connect(38001)) {
			DataOutputStream out = new DataOutputStream(s.getOutputStream());
			DataInputStream in = new DataInputStream(s.getInputStream());
			handshake(out, 1, "A", 1, "B");
			assertFalse(in.readBoolean());
			assertThrows(EOFException.class, in::readInt, "connexion fermée après le refus");
		}
		assertNull(board.get());

		// Le serveur accepte une nouvelle connexion ensuite
		try (Socket s = connect(38001)) {
			DataOutputStream out = new DataOutputStream(s.getOutputStream());
			DataInputStream in = new DataInputStream(s.getInputStream());
			handshake(out, 1, "A", 2, "B");
			assertTrue(in.readBoolean());
		}
	}

	@Test
	void perteDeConnexionSignalee() throws Exception {
		start(38002);
		try (Socket s = connect(38002)) {
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
		start(38003);
		connect(38003).close();
		server.close();
		server = null;

		start(38003);
		try (Socket s = connect(38003)) {
			assertTrue(s.isConnected());
		}
	}
}
