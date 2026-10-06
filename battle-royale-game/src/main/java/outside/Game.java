package outside;

import static org.lwjgl.glfw.GLFW.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import inside.Board;
import inside.Board.PlayerSpec;
import inside.BoardSnapshot;
import inside.BotController;
import inside.Command;
import inside.GameEvent;
import inside.IConfig;
import outside.audio.AudioUtilities;
import outside.communication.GameServer;
import outside.communication.NetworkUtilities;
import outside.graphic.Effects;
import outside.graphic.Fonts;
import outside.graphic.GraphicUtilities;
import outside.graphic.HudRenderer;
import outside.graphic.HudRenderer.HudInfo;
import outside.graphic.TextureManager;
import outside.graphic.WorldRenderer;

/**
 * Point d'entrée du jeu : fenêtre GLFW, boucle principale à pas fixe et affichage.
 * <p>
 * La simulation avance par pas fixes de 1/60 s grâce à un accumulateur ; l'affichage est
 * redessiné à chaque image (synchronisation verticale) en interpolant entre les deux
 * dernières images de la simulation.
 * @author mourtaza
 */
public class Game implements IConfig {
	private static final Logger LOGGER = Logger.getLogger(Game.class.getName());
	/**
	 * Durée maximale d'une image prise en compte (évite la spirale de rattrapage)
	 */
	private static final double MAX_FRAME_TIME = 0.25;

	private final LaunchOptions OPTIONS;
	private final String GAMEPAD_URL;
	private final KeyboardInput INPUT = new KeyboardInput();
	private final Effects EFFECTS = new Effects();
	/**
	 * Plateau créé par le fil réseau, en attente de prise en charge par la boucle principale
	 */
	private final AtomicReference<Board> PENDING_BOARD = new AtomicReference<>();

	private long window;
	private TextureManager textures;
	private Fonts fonts;
	private WorldRenderer world;
	private HudRenderer hud;
	private AudioUtilities audio;
	private GameServer server;

	private Board board;
	private BotController bots;
	private BoardSnapshot previous;
	private int localId = -1;
	private int gamesPlayed;
	private double endTime = -1;

	private volatile String status;
	private volatile boolean statusError;
	private volatile double statusTime = -100;
	private volatile boolean connectionLost;


	/**
	 * Construit le jeu
	 * @param options Options de lancement
	 */
	public Game(LaunchOptions options) {
		OPTIONS = options;
		GAMEPAD_URL = NetworkUtilities.gamepadUrl(NetworkUtilities.lanIPv4());
	}

	/**
	 * Lance le jeu
	 * @param args Options (voir {@link LaunchOptions#USAGE}) ; sans argument, le mode est demandé dans la console
	 */
	public static void main(String[] args) {
		LaunchOptions options;
		if (args.length == 0) {
			options = LaunchOptions.ask(new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset())), System.out);
		} else if (java.util.Arrays.stream(args).anyMatch(a -> a.equals("--help") || a.equals("-h"))) {
			System.out.println(LaunchOptions.USAGE);
			return;
		} else {
			try {
				options = LaunchOptions.parse(args);
			} catch (IllegalArgumentException e) {
				System.err.println("- " + e.getMessage());
				System.err.println(LaunchOptions.USAGE);
				return;
			}
		}

		new Game(options).run();
	}

	/**
	 * Ouvre la fenêtre et exécute la boucle principale jusqu'à sa fermeture
	 */
	public void run() {
		try {
			if (!init()) return;
			if (OPTIONS.multi()) startServer();
			else newSoloGame();
			loop();
		} catch (RuntimeException e) {
			LOGGER.log(Level.SEVERE, "Erreur fatale du jeu", e);
		} finally {
			cleanup();
		}
	}

	/**
	 * Initialise GLFW, la fenêtre, OpenGL et les ressources
	 * @return false si l'initialisation a échoué
	 */
	private boolean init() {
		GLFWErrorCallback.createPrint(System.err).set();
		if (!glfwInit()) {
			System.err.println("- Échec de l'initialisation de GLFW (aucun affichage disponible ?)");
			return false;
		}

		glfwDefaultWindowHints();
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
		glfwWindowHint(GLFW_SCALE_TO_MONITOR, GLFW_TRUE);
		glfwWindowHint(GLFW_SAMPLES, 4);

		window = glfwCreateWindow(OPTIONS.windowWidth(), OPTIONS.windowHeight(), "Battle Royale", 0, 0);
		if (window == 0) {
			glfwWindowHint(GLFW_SAMPLES, 0);
			window = glfwCreateWindow(OPTIONS.windowWidth(), OPTIONS.windowHeight(), "Battle Royale", 0, 0);
		}
		if (window == 0) {
			System.err.println("- Échec de la création de la fenêtre");
			return false;
		}

		GLFWVidMode mode = glfwGetVideoMode(glfwGetPrimaryMonitor());
		if (mode != null) {
			try (MemoryStack stack = MemoryStack.stackPush()) {
				var w = stack.mallocInt(1);
				var h = stack.mallocInt(1);
				glfwGetWindowSize(window, w, h);
				glfwSetWindowPos(window, Math.max(0, (mode.width() - w.get(0)) / 2), Math.max(0, (mode.height() - h.get(0)) / 2));
			}
		}

		glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
			if (action == GLFW_PRESS) onKeyPressed(key);
		});

		glfwMakeContextCurrent(window);
		glfwSwapInterval(1);
		GL.createCapabilities();

		textures = new TextureManager();
		fonts = new Fonts();
		world = new WorldRenderer(textures, fonts.LABEL);
		hud = new HudRenderer(fonts, textures);
		audio = new AudioUtilities(OPTIONS.sound());

		glfwShowWindow(window);
		return true;
	}

	/**
	 * Boucle principale : accumulateur à pas fixe, puis affichage à chaque image
	 */
	private void loop() {
		double last = glfwGetTime(), accumulator = 0;

		while (!glfwWindowShouldClose(window)) {
			glfwPollEvents();

			double now = glfwGetTime();
			double frame = Math.min(now - last, MAX_FRAME_TIME);
			last = now;

			Board pending = PENDING_BOARD.getAndSet(null);
			if (pending != null) setBoard(pending, -1);

			if (board != null) {
				accumulator += frame;
				while (accumulator >= TICK_DURATION) {
					step(now);
					accumulator -= TICK_DURATION;
				}
			}

			EFFECTS.update(now, (float)frame);
			render(now, (float)(accumulator / TICK_DURATION));
			glfwSwapBuffers(window);
		}
	}

	/**
	 * Effectue un pas de simulation : entrées, robots, simulation, effets
	 * @param now Instant courant
	 */
	private void step(double now) {
		previous = board.getSnapshot();
		if (localId >= 0) INPUT.apply(board, localId, readKeys());
		if (bots != null) bots.update();
		board.tick();

		List<GameEvent> events = board.drainEvents();
		BoardSnapshot s = board.getSnapshot();
		EFFECTS.consume(events, s, localId);
		audio.play(events, localId);
		if (s.isOver() && endTime < 0) endTime = now;
	}

	/**
	 * Dessine l'image courante
	 * @param now Instant courant
	 * @param alpha Avancement de l'interpolation
	 */
	private void render(double now, float alpha) {
		int fbWidth, fbHeight;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			var w = stack.mallocInt(1);
			var h = stack.mallocInt(1);
			glfwGetFramebufferSize(window, w, h);
			fbWidth = w.get(0);
			fbHeight = h.get(0);
		}

		GraphicUtilities.beginFrame(fbWidth, fbHeight);
		HudInfo info = new HudInfo(localId, OPTIONS.multi(), GAMEPAD_URL, OPTIONS.port(), status, statusError, statusTime, connectionLost);

		if (board == null) {
			hud.renderLobby(info, now);
			return;
		}

		BoardSnapshot current = board.getSnapshot();
		world.render(board.getMap(), previous, current, Math.max(0, Math.min(1, alpha)), now, localId, EFFECTS, fonts.MEDIUM);
		hud.render(current, info, EFFECTS, now, endTime);
	}

	/**
	 * Lit l'état des touches du joueur local
	 * @return État des touches
	 */
	private KeyboardInput.Keys readKeys() {
		return new KeyboardInput.Keys(
			down(GLFW_KEY_UP) || down(GLFW_KEY_W),
			down(GLFW_KEY_DOWN) || down(GLFW_KEY_S),
			down(GLFW_KEY_LEFT) || down(GLFW_KEY_A),
			down(GLFW_KEY_RIGHT) || down(GLFW_KEY_D),
			down(GLFW_KEY_LEFT_SHIFT) || down(GLFW_KEY_RIGHT_SHIFT),
			down(GLFW_KEY_SPACE) || down(GLFW_KEY_J),
			down(GLFW_KEY_K) || down(GLFW_KEY_ENTER) || down(GLFW_KEY_KP_ENTER));
	}

	private boolean down(int key) {
		return glfwGetKey(window, key) == GLFW_PRESS;
	}

	/**
	 * Gère les touches de contrôle (appelé depuis la boucle principale par glfwPollEvents)
	 * @param key Touche
	 */
	private void onKeyPressed(int key) {
		switch (key) {
		case GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(window, true);
		case GLFW_KEY_P -> {
			if (!OPTIONS.multi() && board != null && !board.isOver())
				board.enqueue(new Command.Control(board.isPaused() ? Command.ControlType.RESUME : Command.ControlType.PAUSE));
		}
		case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> {
			if (!OPTIONS.multi() && board != null && board.isOver() && glfwGetTime() - endTime > 1) newSoloGame();
		}
		case GLFW_KEY_F11 -> toggleFullscreen();
		default -> {}
		}
	}

	/**
	 * Bascule entre plein écran et fenêtre
	 */
	private void toggleFullscreen() {
		long monitor = glfwGetWindowMonitor(window);
		if (monitor != 0) {
			glfwSetWindowMonitor(window, 0, 80, 80, OPTIONS.windowWidth(), OPTIONS.windowHeight(), GLFW_DONT_CARE);
		} else {
			long primary = glfwGetPrimaryMonitor();
			GLFWVidMode mode = glfwGetVideoMode(primary);
			if (mode != null) glfwSetWindowMonitor(window, primary, 0, 0, mode.width(), mode.height(), mode.refreshRate());
		}
	}

	/**
	 * Lance une nouvelle partie solo
	 */
	private void newSoloGame() {
		List<PlayerSpec> specs = new ArrayList<>();
		int id = 0;
		if (!OPTIONS.spectate()) specs.add(new PlayerSpec(id++, OPTIONS.pseudo(), false));
		for (int i = 0; i < OPTIONS.bots(); i++)
			specs.add(new PlayerSpec(id++, BotController.botName(i), true));

		long seed = OPTIONS.seed() + gamesPlayed;
		setBoard(new Board(OPTIONS.settings().withSeed(seed), specs), OPTIONS.spectate() ? -1 : 0);
	}

	/**
	 * Remplace la partie affichée
	 * @param b Nouveau plateau
	 * @param local Identifiant du joueur local (-1 si aucun)
	 */
	private void setBoard(Board b, int local) {
		board = b;
		localId = local;
		previous = null;
		endTime = -1;
		connectionLost = false;
		gamesPlayed++;
		EFFECTS.clear();
		INPUT.reset();
		boolean hasBots = b.getPlayers().stream().anyMatch(p -> p.isBot());
		bots = hasBots ? new BotController(b, b.getSettings().getSeed()) : null;
	}

	/**
	 * Démarre le serveur TCP du mode multijoueur
	 */
	private void startServer() {
		server = new GameServer(OPTIONS.bind(), OPTIONS.port(), new GameServer.Listener() {
			@Override
			public Board onGameRequested(List<PlayerSpec> players) {
				Board b = createMultiBoard(players);
				PENDING_BOARD.set(b);
				return b;
			}

			@Override
			public void onStatus(String message, boolean error) {
				status = message;
				statusError = error;
				statusTime = glfwGetTime();
				if (error) LOGGER.warning(message);
				else LOGGER.info(message);
			}

			@Override
			public void onConnectionLost(Board b) {
				b.enqueue(new Command.Control(Command.ControlType.PAUSE));
				connectionLost = true;
			}
		});
		server.start();
	}

	/**
	 * Crée le plateau d'une partie multijoueur, en ajoutant les robots demandés
	 * @param players Joueurs annoncés par le serveur web
	 * @return Plateau
	 */
	Board createMultiBoard(List<PlayerSpec> players) {
		List<PlayerSpec> specs = new ArrayList<>(players);
		int next = players.stream().mapToInt(PlayerSpec::id).max().orElse(-1) + 1;
		for (int i = 0; i < OPTIONS.bots() && next < MAX_PLAYERS; i++)
			specs.add(new PlayerSpec(next++, BotController.botName(i), true));
		return new Board(OPTIONS.settings().withSeed(OPTIONS.seed() + gamesPlayed), specs);
	}

	/**
	 * Libère toutes les ressources
	 */
	private void cleanup() {
		if (server != null) server.close();
		if (audio != null) audio.close();
		if (fonts != null) fonts.close();
		if (textures != null) textures.delete();
		if (window != 0) {
			glfwDestroyWindow(window);
			window = 0;
		}
		glfwTerminate();
		GLFWErrorCallback cb = glfwSetErrorCallback(null);
		if (cb != null) cb.free();
	}
}
