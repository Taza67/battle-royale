package outside.communication;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import inside.Board;
import inside.Board.PlayerSpec;
import inside.Command;

/**
 * Serveur TCP du jeu : attend la connexion du serveur web, lit la poignée de main,
 * puis répond à chaque bloc d'actions par l'état du plateau (voir docs/PROTOCOLE.md).
 * <p>
 * Ce fil ne modifie jamais le plateau : il dépose des commandes dans sa file et lit
 * la dernière image immuable publiée par la simulation.
 * @author mourtaza
 */
public class GameServer implements Runnable, AutoCloseable {
	/**
	 * Journal du serveur
	 */
	private static final Logger LOGGER = Logger.getLogger(GameServer.class.getName());
	/**
	 * Adresse d'écoute par défaut : seul le serveur web lancé sur la même machine peut se connecter
	 */
	public static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";
	/**
	 * Délai accordé au serveur web pour terminer la poignée de main, en millisecondes
	 */
	public static final int HANDSHAKE_TIMEOUT_MS = 5000;
	/**
	 * Délai d'attente maximal d'un message du serveur web pendant une partie, en millisecondes :
	 * une connexion restée muette est considérée perdue
	 */
	public static final int READ_TIMEOUT_MS = 15_000;

	/**
	 * Écouteur des événements du serveur (appelé depuis le fil réseau)
	 */
	public interface Listener {
		/**
		 * Le serveur web demande le lancement d'une partie
		 * @param players Joueurs annoncés (déjà validés)
		 * @return Plateau créé, ou null pour refuser la partie
		 */
		Board onGameRequested(List<PlayerSpec> players);

		/**
		 * Changement d'état de la connexion, à afficher dans le jeu
		 * @param message Message en français
		 * @param error true s'il s'agit d'une erreur
		 */
		void onStatus(String message, boolean error);

		/**
		 * La connexion avec le serveur web a été perdue pendant une partie
		 * @param board Plateau de la partie concernée
		 */
		void onConnectionLost(Board board);
	}

	/**
	 * Adresse d'écoute
	 */
	private final String bindAddress;
	/**
	 * Port d'écoute
	 */
	private final int port;
	/**
	 * Écouteur
	 */
	private final Listener listener;
	/**
	 * Fil du serveur
	 */
	private final Thread thread;
	/**
	 * Socket d'écoute
	 */
	private volatile ServerSocket serverSocket;
	/**
	 * Connexion en cours
	 */
	private volatile Socket client;
	/**
	 * Indique si le serveur doit s'arrêter
	 */
	private volatile boolean closed;
	/**
	 * Indique si l'arrêt de la partie en cours a été demandé (fil réseau uniquement)
	 */
	private boolean stopRequested;


	/**
	 * Construit le serveur (sans le démarrer)
	 * @param bindAddress Adresse d'écoute (par exemple {@value #DEFAULT_BIND_ADDRESS} ou 0.0.0.0)
	 * @param port Port d'écoute
	 * @param listener Écouteur
	 */
	public GameServer(String bindAddress, int port, Listener listener) {
		this.bindAddress = bindAddress;
		this.port = port;
		this.listener = listener;
		thread = new Thread(this, "serveur-tcp-" + port);
		thread.setDaemon(true);
	}

	/**
	 * Démarre le fil du serveur
	 */
	public void start() {
		thread.start();
	}

	/**
	 * Retourne le port d'écoute
	 * @return Port
	 */
	public int getPort() { return port; }

	/**
	 * Retourne l'adresse d'écoute
	 * @return Adresse
	 */
	public String getBindAddress() { return bindAddress; }

	@Override
	public void run() {
		try (ServerSocket server = new ServerSocket()) {
			server.setReuseAddress(true);
			// File d'attente réduite à 1 : une seconde connexion pendant une partie est refusée
			server.bind(new InetSocketAddress(InetAddress.getByName(bindAddress), port), 1);
			serverSocket = server;
			listener.onStatus("En attente du serveur web (" + bindAddress + ":" + port + ")", false);

			while (!closed) {
				try (Socket socket = server.accept()) {
					client = socket;
					if (closed) break;
					socket.setTcpNoDelay(true);
					listener.onStatus("Serveur web connecté : " + socket.getRemoteSocketAddress(), false);
					handle(socket);
				} catch (IOException e) {
					if (closed) break;
					LOGGER.log(Level.WARNING, "Erreur de connexion avec le serveur web", e);
					listener.onStatus("Erreur de connexion : " + e.getMessage(), true);
				} finally {
					client = null;
				}
			}
		} catch (IOException e) {
			if (!closed) {
				LOGGER.log(Level.SEVERE, "Impossible d'écouter sur " + bindAddress + ":" + port, e);
				listener.onStatus("Impossible d'écouter sur " + bindAddress + ":" + port + " : " + e.getMessage(), true);
			}
		}
	}

	/**
	 * Gère une connexion du serveur web : poignée de main puis boucle d'échange
	 * @param socket Connexion
	 * @throws IOException Erreur d'entrée/sortie
	 */
	private void handle(Socket socket) throws IOException {
		DeadlineInputStream raw = new DeadlineInputStream(socket, System.nanoTime() + HANDSHAKE_TIMEOUT_MS * 1_000_000L);
		DataInputStream in = new DataInputStream(new BufferedInputStream(raw));
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

		List<PlayerSpec> players;
		try {
			players = Protocol.readHandshake(in);
		} catch (SocketTimeoutException e) {
			listener.onStatus("Poignée de main non terminée en " + HANDSHAKE_TIMEOUT_MS / 1000 + " s, connexion fermée", true);
			return;
		}
		// Après la poignée de main, les lectures restent bornées : un client muet ne bloque pas le serveur
		raw.clearDeadline();
		socket.setSoTimeout(READ_TIMEOUT_MS);

		String refusal = Protocol.validatePlayers(players);
		Board board = refusal == null ? listener.onGameRequested(players) : null;

		out.writeBoolean(board != null);
		out.flush();
		if (board == null) {
			listener.onStatus("Partie refusée : " + (refusal != null ? refusal : "le jeu n'est pas prêt"), true);
			return;
		}
		listener.onStatus("Partie lancée avec " + players.size() + " joueur(s)", false);

		stopRequested = false;
		try {
			loop(in, out, board);
		} catch (IOException e) {
			if (closed) return;
			if (stopRequested) {
				LOGGER.log(Level.FINE, "Connexion fermée après l'arrêt de la partie", e);
				listener.onStatus("Partie arrêtée par l'administrateur", false);
			} else if (board.getSnapshot().isOver()) {
				LOGGER.log(Level.FINE, "Connexion fermée après la fin de la partie", e);
				listener.onStatus("Partie terminée, serveur web déconnecté", false);
			} else {
				boolean closedByPeer = e instanceof EOFException || e instanceof SocketException || e instanceof SocketTimeoutException;
				LOGGER.log(closedByPeer ? Level.FINE : Level.WARNING, "Échange interrompu avec le serveur web", e);
				listener.onConnectionLost(board);
				listener.onStatus("Connexion avec le serveur web perdue", true);
			}
		}
	}

	/**
	 * Boucle d'échange : lit un code puis applique les actions ou le contrôle demandé.
	 * Après un arrêt, le jeu continue de répondre par l'état jusqu'à ce que le serveur web ferme la connexion.
	 * @param in Flux d'entrée
	 * @param out Flux de sortie
	 * @param board Plateau de la partie
	 * @throws IOException Erreur d'entrée/sortie
	 */
	private void loop(DataInputStream in, DataOutputStream out, Board board) throws IOException {
		while (!closed) {
			int code = in.readInt();

			if (code >= 0) {
				if (code > Protocol.MAX_ACTIONS_SIZE)
					throw new Protocol.ProtocolException("Bloc d'actions trop grand : " + code);
				byte[] actions = new byte[code];
				in.readFully(actions);
				for (Command c : Protocol.decodeActions(actions))
					board.enqueue(c);
				Protocol.writeStateResponse(out, board.getSnapshot());
			} else if (code == Protocol.PAUSE) {
				board.enqueue(new Command.Control(Command.ControlType.PAUSE));
			} else if (code == Protocol.RESUME) {
				board.enqueue(new Command.Control(Command.ControlType.RESUME));
			} else if (code == Protocol.STOP) {
				stopRequested = true;
				board.enqueue(new Command.Control(Command.ControlType.STOP));
			} else {
				throw new Protocol.ProtocolException("Code inconnu : " + code);
			}
		}
	}

	/**
	 * Arrête le serveur et ferme les connexions
	 */
	@Override
	public void close() {
		closed = true;
		closeQuietly(client);
		ServerSocket s = serverSocket;
		if (s != null) {
			try {
				s.close();
			} catch (IOException e) {
				LOGGER.log(Level.FINE, "Erreur à la fermeture", e);
			}
		}
		try {
			thread.join(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Flux d'entrée d'une connexion soumis à une échéance globale : avant chaque lecture, le délai
	 * d'attente du socket est réduit au temps restant, si bien qu'un envoi au compte-gouttes ne
	 * prolonge pas la poignée de main
	 */
	private static final class DeadlineInputStream extends FilterInputStream {
		/**
		 * Connexion lue
		 */
		private final Socket socket;
		/**
		 * Échéance (System.nanoTime), 0 si aucune
		 */
		private long deadline;

		DeadlineInputStream(Socket socket, long deadline) throws IOException {
			super(socket.getInputStream());
			this.socket = socket;
			this.deadline = deadline;
		}

		/**
		 * Supprime l'échéance et rend les lectures bloquantes sans limite
		 * @throws SocketException Erreur du socket
		 */
		void clearDeadline() throws SocketException {
			deadline = 0;
			socket.setSoTimeout(0);
		}

		private void arm() throws IOException {
			if (deadline == 0) return;
			long left = (deadline - System.nanoTime()) / 1_000_000L;
			if (left <= 0) throw new SocketTimeoutException("Échéance de la poignée de main dépassée");
			socket.setSoTimeout((int)left);
		}

		@Override
		public int read() throws IOException {
			arm();
			return super.read();
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			arm();
			return super.read(b, off, len);
		}
	}

	/**
	 * Ferme une connexion sans propager d'erreur
	 * @param s Connexion (peut être null)
	 */
	private static void closeQuietly(Socket s) {
		if (s == null) return;
		try {
			s.close();
		} catch (IOException e) {
			LOGGER.log(Level.FINE, "Erreur à la fermeture", e);
		}
	}
}
