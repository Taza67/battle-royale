package outside.communication;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
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
	private final String BIND_ADDRESS;
	/**
	 * Port d'écoute
	 */
	private final int PORT;
	/**
	 * Écouteur
	 */
	private final Listener LISTENER;
	/**
	 * Fil du serveur
	 */
	private final Thread THREAD;
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
	 * Construit le serveur (sans le démarrer)
	 * @param bindAddress Adresse d'écoute (par exemple {@value #DEFAULT_BIND_ADDRESS} ou 0.0.0.0)
	 * @param port Port d'écoute
	 * @param listener Écouteur
	 */
	public GameServer(String bindAddress, int port, Listener listener) {
		BIND_ADDRESS = bindAddress;
		PORT = port;
		LISTENER = listener;
		THREAD = new Thread(this, "serveur-tcp-" + port);
		THREAD.setDaemon(true);
	}

	/**
	 * Démarre le fil du serveur
	 */
	public void start() {
		THREAD.start();
	}

	/**
	 * Retourne le port d'écoute
	 * @return Port
	 */
	public int getPort() { return PORT; }

	/**
	 * Retourne l'adresse d'écoute
	 * @return Adresse
	 */
	public String getBindAddress() { return BIND_ADDRESS; }

	@Override
	public void run() {
		try (ServerSocket server = new ServerSocket()) {
			server.setReuseAddress(true);
			server.bind(new InetSocketAddress(InetAddress.getByName(BIND_ADDRESS), PORT));
			serverSocket = server;
			LISTENER.onStatus("En attente du serveur web (" + BIND_ADDRESS + ":" + PORT + ")", false);

			while (!closed) {
				try (Socket socket = server.accept()) {
					client = socket;
					socket.setTcpNoDelay(true);
					LISTENER.onStatus("Serveur web connecté : " + socket.getRemoteSocketAddress(), false);
					handle(socket);
				} catch (IOException e) {
					if (closed) break;
					LOGGER.log(Level.WARNING, "Erreur de connexion avec le serveur web", e);
					LISTENER.onStatus("Erreur de connexion : " + e.getMessage(), true);
				} finally {
					client = null;
				}
			}
		} catch (IOException e) {
			if (!closed) {
				LOGGER.log(Level.SEVERE, "Impossible d'écouter sur " + BIND_ADDRESS + ":" + PORT, e);
				LISTENER.onStatus("Impossible d'écouter sur " + BIND_ADDRESS + ":" + PORT + " : " + e.getMessage(), true);
			}
		}
	}

	/**
	 * Gère une connexion du serveur web : poignée de main puis boucle d'échange
	 * @param socket Connexion
	 * @throws IOException Erreur d'entrée/sortie
	 */
	private void handle(Socket socket) throws IOException {
		DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

		List<PlayerSpec> players = Protocol.readHandshake(in);
		String refusal = Protocol.validatePlayers(players);
		Board board = refusal == null ? LISTENER.onGameRequested(players) : null;

		out.writeBoolean(board != null);
		out.flush();
		if (board == null) {
			LISTENER.onStatus("Partie refusée : " + (refusal != null ? refusal : "le jeu n'est pas prêt"), true);
			return;
		}
		LISTENER.onStatus("Partie lancée avec " + players.size() + " joueur(s)", false);

		try {
			loop(in, out, board);
		} catch (EOFException | SocketException e) {
			if (closed) return;
			LOGGER.log(Level.FINE, "Connexion fermée par le serveur web", e);
			if (!board.getSnapshot().isOver()) {
				LISTENER.onConnectionLost(board);
				LISTENER.onStatus("Connexion avec le serveur web perdue", true);
			} else {
				LISTENER.onStatus("Partie terminée, serveur web déconnecté", false);
			}
		}
	}

	/**
	 * Boucle d'échange : lit un code puis applique les actions ou le contrôle demandé
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
			THREAD.join(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
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
