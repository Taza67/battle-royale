package outside;

import static inside.IConfig.*;
import static org.lwjgl.glfw.GLFW.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import inside.Board;
import inside.Board.PlayerSpec;
import inside.BoardSnapshot;
import inside.BotController;
import inside.Command;
import inside.IConfig;
import outside.audio.AudioUtilities;
import outside.communication.GameServer;
import outside.graphic.Effects;
import outside.graphic.Fonts;
import outside.graphic.GraphicUtilities;
import outside.graphic.HudRenderer;
import outside.graphic.HudRenderer.HudInfo;
import outside.graphic.TextureManager;
import outside.graphic.WorldRenderer;

/**
 * Point d'entrée du jeu : fenêtre GLFW, clavier et affichage.
 * <p>
 * La simulation avance par pas fixes de 1/60 s sur son propre fil ({@link SimulationLoop}).
 * Le fil de la fenêtre se contente de déposer les commandes du clavier et de redessiner
 * chaque image (synchronisation verticale) en interpolant entre les deux dernières images
 * publiées par la simulation.
 * @author mourtaza
 */
public class Game {
	private static final Logger LOGGER = Logger.getLogger(Game.class.getName());
	/**
	 * Durée maximale d'une image prise en compte pour l'animation des effets
	 */
	private static final double MAX_FRAME_TIME = 0.25;

	private final LaunchOptions OPTIONS;
	private final String GAMEPAD_URL;
	private final KeyboardInput INPUT = new KeyboardInput();
	private final Effects EFFECTS = new Effects();
	private final SimulationLoop SIMULATION = new SimulationLoop();
	/**
	 * Nombre de parties créées (fait varier la graine d'une partie à l'autre)
	 */
	private final AtomicInteger GAMES_CREATED = new AtomicInteger();

	private long window;
	private TextureManager textures;
	private Fonts fonts;
	private WorldRenderer world;
	private HudRenderer hud;
	private AudioUtilities audio;
	private GameServer server;

	/**
	 * Partie affichée (fil de la fenêtre)
	 */
	private SimulationLoop.Session shown;
	private int localId = -1;
	private double endTime = -1;

	/**
	 * Position et taille de la fenêtre avant le passage en plein écran
	 */
	private int windowedX = 80, windowedY = 80, windowedW = 0, windowedH = 0;

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
		GAMEPAD_URL = options.effectiveGamepadUrl();
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
			SIMULATION.start();
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

		setWindowIcon();

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
	 * Boucle de la fenêtre : clavier, effets de la dernière image publiée, puis affichage
	 */
	private void loop() {
		double last = glfwGetTime();

		while (!glfwWindowShouldClose(window)) {
			RuntimeException failure = SIMULATION.getFailure();
			if (failure != null) throw new IllegalStateException("Simulation arrêtée", failure);

			glfwPollEvents();

			double now = glfwGetTime();
			float elapsed = (float)Math.min(now - last, MAX_FRAME_TIME);
			last = now;

			SimulationLoop.Frame frame = SIMULATION.getFrame();
			if (frame != null && frame.session() != shown) show(frame.session());
			if (shown != null) {
				if (localId >= 0) INPUT.apply(shown.board(), localId, readKeys());
				consumeEvents(now);
				if (frame != null && frame.session() == shown)
					EFFECTS.ambient(elapsed, frame.current().zone());
			}

			EFFECTS.update(now, elapsed);
			render(now, frame);
			glfwSwapBuffers(window);
		}
	}

	/**
	 * Transmet aux effets et au son les événements produits par la simulation depuis l'image précédente
	 * @param now Instant courant
	 */
	private void consumeEvents(double now) {
		for (SimulationLoop.Events batch : SIMULATION.drainEvents()) {
			if (batch.session() != shown) continue;
			EFFECTS.consume(batch.events(), batch.snapshot(), localId);
			audio.play(batch.events(), localId);
		}
		if (endTime < 0 && shown.board().getSnapshot().isOver()) endTime = now;
	}

	/**
	 * Dessine l'image courante
	 * @param now Instant courant
	 * @param frame Dernière image publiée par la simulation (null avant la première partie)
	 */
	private void render(double now, SimulationLoop.Frame frame) {
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

		if (frame == null || frame.session() != shown) {
			if (OPTIONS.multi()) hud.renderLobby(info, now);
			return;
		}

		BoardSnapshot current = frame.current();

		// Tremblement de caméra : le monde est translaté, l'interface reste fixe
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPushMatrix();
		GL11.glTranslatef(EFFECTS.shakeX(), EFFECTS.shakeY(), 0);
		world.render(shown.board().getMap(), frame.previous(), current, frame.alpha(System.nanoTime()), now, localId,
			EFFECTS, fonts.MEDIUM);
		GL11.glPopMatrix();
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
			down(GLFW_KEY_K) || down(GLFW_KEY_ENTER) || down(GLFW_KEY_KP_ENTER),
			down(GLFW_KEY_SPACE) || down(GLFW_KEY_J));
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
			BoardSnapshot s = shown != null ? shown.board().getSnapshot() : null;
			if (!OPTIONS.multi() && s != null && !s.isOver())
				shown.board().enqueue(new Command.Control(s.paused() ? Command.ControlType.RESUME : Command.ControlType.PAUSE));
		}
		case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> {
			if (!OPTIONS.multi() && shown != null && endTime >= 0 && glfwGetTime() - endTime > 1) newSoloGame();
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
			glfwSetWindowMonitor(window, 0, windowedX, windowedY,
				windowedW > 0 ? windowedW : OPTIONS.windowWidth(),
				windowedH > 0 ? windowedH : OPTIONS.windowHeight(), GLFW_DONT_CARE);
		} else {
			// Mémorise la position et la taille fenêtrée pour les restaurer à la sortie
			try (MemoryStack stack = MemoryStack.stackPush()) {
				var x = stack.mallocInt(1);
				var y = stack.mallocInt(1);
				var w = stack.mallocInt(1);
				var h = stack.mallocInt(1);
				glfwGetWindowPos(window, x, y);
				glfwGetWindowSize(window, w, h);
				windowedX = x.get(0); windowedY = y.get(0);
				windowedW = w.get(0); windowedH = h.get(0);
			}
			long primary = glfwGetPrimaryMonitor();
			GLFWVidMode mode = glfwGetVideoMode(primary);
			if (mode != null) glfwSetWindowMonitor(window, primary, 0, 0, mode.width(), mode.height(), mode.refreshRate());
		}
	}

	/**
	 * Applique l'emblème du jeu comme icône de fenêtre (silencieux si l'image manque)
	 */
	private void setWindowIcon() {
		try {
			outside.graphic.Texture.Image icon = outside.graphic.Texture.readResource("textures/emblem.png");
			try (MemoryStack stack = MemoryStack.stackPush()) {
				GLFWImage.Buffer icons = GLFWImage.malloc(1, stack);
				icons.width(icon.width()).height(icon.height()).pixels(icon.pixels());
				glfwSetWindowIcon(window, icons);
			}
		} catch (RuntimeException e) {
			LOGGER.fine("Icône de fenêtre non appliquée : " + e.getMessage());
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
			specs.add(new PlayerSpec(id++, BotController.botPseudo(i), true));

		long seed = OPTIONS.seed() + GAMES_CREATED.getAndIncrement();
		Board b = new Board(OPTIONS.settings().withSeed(seed), specs);
		SIMULATION.play(SimulationLoop.Session.of(b, OPTIONS.spectate() ? -1 : 0));
	}

	/**
	 * Affiche une nouvelle partie prise en charge par la simulation
	 * @param s Partie
	 */
	private void show(SimulationLoop.Session s) {
		shown = s;
		localId = s.localId();
		endTime = -1;
		EFFECTS.clear();
		INPUT.reset();
	}

	/**
	 * Démarre le serveur TCP du mode multijoueur
	 */
	private void startServer() {
		server = new GameServer(OPTIONS.bind(), OPTIONS.port(), new GameServer.Listener() {
			@Override
			public Board onGameRequested(List<PlayerSpec> players) {
				Board b = createMultiBoard(players);
				connectionLost = false;
				SIMULATION.play(SimulationLoop.Session.of(b, -1));
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
			specs.add(new PlayerSpec(next++, BotController.botPseudo(i), true));
		return new Board(OPTIONS.settings().withSeed(OPTIONS.seed() + GAMES_CREATED.getAndIncrement()), specs);
	}

	/**
	 * Libère toutes les ressources
	 */
	private void cleanup() {
		if (server != null) server.close();
		SIMULATION.close();
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
