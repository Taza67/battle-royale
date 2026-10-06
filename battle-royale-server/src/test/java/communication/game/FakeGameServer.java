package communication.game;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Faux jeu TCP respectant docs/PROTOCOLE.md, qui enregistre tout ce qu'il reçoit
 */
public final class FakeGameServer implements AutoCloseable {
	/** Événement reçu par le faux jeu */
	public sealed interface Event {}
	/** Poignée de main reçue */
	public record Handshake(List<Participant> participants) implements Event {}
	/** Échange reçu avec ses actions */
	public record Actions(byte[] payload) implements Event {}
	/** Code de contrôle reçu (-1, -2 ou -3) */
	public record Control(int code) implements Event {}
	/** Connexion fermée par le serveur web */
	public record Closed() implements Event {}

	public static final int PORT = 18000;

	private final ServerSocket server;
	private final Thread thread;
	private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();
	private volatile boolean accept = true;
	private volatile byte[] state = SnapshotBytes.battle().build();
	private volatile boolean finishNext;
	private volatile byte[] finalState;
	private volatile boolean dropNext;
	private volatile boolean silentHandshake;
	private volatile Socket current;

	public FakeGameServer() throws IOException {
		this(PORT);
	}

	public FakeGameServer(int port) throws IOException {
		server = new ServerSocket();
		server.setReuseAddress(true);
		server.bind(new InetSocketAddress("localhost", port));
		thread = new Thread(this::serve, "fake-game");
		thread.setDaemon(true);
		thread.start();
	}

	public int port() { return server.getLocalPort(); }

	/** Réponse à la poignée de main */
	public FakeGameServer accept(boolean value) { accept = value; return this; }
	/** Ne jamais répondre à la poignée de main */
	public FakeGameServer silentHandshake(boolean value) { silentHandshake = value; return this; }
	/** État renvoyé à chaque échange */
	public FakeGameServer state(byte[] value) { state = value; return this; }
	/** Termine la partie au prochain échange avec l'état donné */
	public FakeGameServer finish(byte[] value) { finalState = value; finishNext = true; return this; }
	/** Coupe brutalement la connexion au prochain échange */
	public FakeGameServer dropNext() { dropNext = true; return this; }

	public BlockingQueue<Event> events() { return events; }

	/** Attend le prochain événement du type donné en ignorant les autres */
	public <T extends Event> T await(Class<T> type, long timeoutMillis) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
		while (true) {
			long left = deadline - System.nanoTime();
			Event e = left > 0 ? events.poll(left, TimeUnit.NANOSECONDS) : null;
			if (e == null)
				throw new AssertionError("Aucun événement " + type.getSimpleName() + " reçu à temps");
			if (type.isInstance(e))
				return type.cast(e);
		}
	}

	/** Attend un échange dont les actions ne sont pas vides */
	public byte[] awaitActions(long timeoutMillis) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
		while (true) {
			Actions a = await(Actions.class, Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
			if (a.payload().length > 0)
				return a.payload();
		}
	}

	/** Attend un événement de contrôle et vérifie son code */
	public Control awaitControl(long timeoutMillis) throws InterruptedException {
		return await(Control.class, timeoutMillis);
	}

	private void serve() {
		while (!server.isClosed()) {
			try (Socket socket = server.accept()) {
				current = socket;
				handle(socket);
			} catch (IOException e) {
				if (!server.isClosed())
					events.add(new Closed());
			}
		}
	}

	private void handle(Socket socket) throws IOException {
		DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

		int start = in.readInt();
		if (start != GameLink.CODE_START)
			throw new IOException("Code de démarrage inattendu : " + start);
		int n = in.readInt();
		List<Participant> participants = new ArrayList<>();
		for (int i = 0; i < n; i++)
			participants.add(new Participant(in.readByte(), in.readUTF()));
		events.add(new Handshake(participants));

		if (silentHandshake) {
			while (in.read() >= 0) {
				// le faux jeu ne répond jamais
			}
			events.add(new Closed());
			return;
		}
		out.writeBoolean(accept);
		out.flush();
		if (!accept) {
			events.add(new Closed());
			return;
		}

		while (true) {
			int code;
			try {
				code = in.readInt();
			} catch (EOFException e) {
				events.add(new Closed());
				return;
			}
			if (code < 0) {
				events.add(new Control(code));
				if (code == GameLink.CODE_STOP) {
					in.read();
					events.add(new Closed());
					return;
				}
				continue;
			}
			byte[] payload = new byte[code];
			in.readFully(payload);
			events.add(new Actions(payload));

			if (dropNext) {
				dropNext = false;
				socket.close();
				return;
			}
			boolean finishing = finishNext;
			byte[] reply = finishing ? finalState : state;
			out.writeInt(reply.length);
			out.write(reply);
			out.writeBoolean(!finishing);
			out.flush();
			if (finishing) {
				finishNext = false;
				in.read();
				events.add(new Closed());
				return;
			}
		}
	}

	@Override
	public void close() throws IOException {
		server.close();
		Socket s = current;
		if (s != null)
			s.close();
		try {
			thread.join(2000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
