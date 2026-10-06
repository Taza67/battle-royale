package outside;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.system.MemoryStack.stackPush;

import java.nio.IntBuffer;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import inside.Board;
import inside.IConfig;
import outside.communication.TCommunicationHandler;
import outside.graphic.Color;
import outside.graphic.GraphicUtilities;
import outside.graphic.TextureManager;

import java.util.Scanner;


public class Game implements IConfig {
	public static Scanner keyboard;
	
	private long window;
	private final Board BOARD;
	private final TCommunicationHandler COMMUNICATION_HANDLER;
	public static TextureManager TEXTURE_MANAGER;

	// Constructeurs
	public Game() {
		BOARD = new Board();
		COMMUNICATION_HANDLER = new TCommunicationHandler(BOARD);
	}


	// Méthodes
	public void run() {
		init();
		TEXTURE_MANAGER = new TextureManager();
		loop();

		COMMUNICATION_HANDLER.interrupt();
		BOARD.stopThreads();
		GLFW.glfwDestroyWindow(window);
		GLFW.glfwTerminate();
		GLFW.glfwSetErrorCallback(null).free();
		ScoreScreen fenetreScore = new ScoreScreen(BOARD);
		fenetreScore.afficheScore();
	}

	// Initialise la carte
	public void init() {
		// Initialisation de GFLW
		GLFWErrorCallback.createPrint(System.err).set();
		if (!GLFW.glfwInit())
			throw new IllegalStateException("- Échec de l'initialisation de GLFW !");

		GLFW.glfwDefaultWindowHints();
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);

		// Création de la fenêtre
		window = GLFW.glfwCreateWindow(WINDOW_WIDTH, WINDOW_HEIGHT, "Battle Royale", 0, 0);
		if (window == 0)
			throw new IllegalStateException("- Échec de la création de la fenêtre !");

		// Gestion des événements
		// // Clavier
		GLFW.glfwSetKeyCallback(window, new GLFWKeyCallbackI() {
			@Override
			public void invoke(long window, int key, int scancode, int action, int mods) {
				if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
					switch (key) {
					case GLFW.GLFW_KEY_ESCAPE:
						GLFW.glfwSetWindowShouldClose(window, true);
						BOARD.setIsWindowDirty(true);
						break;
					case GLFW.GLFW_KEY_LEFT:
						BOARD.movePlayer(0, WEST, 1);
						BOARD.setIsWindowDirty(true);
						break;
					case GLFW.GLFW_KEY_RIGHT:
						BOARD.movePlayer(0, EAST, 1);
						BOARD.setIsWindowDirty(true);
						break;
					case GLFW.GLFW_KEY_UP:
						BOARD.movePlayer(0, NORTH, 1);
						BOARD.setIsWindowDirty(true);
						break;
					case GLFW.GLFW_KEY_DOWN:
						BOARD.movePlayer(0, SOUTH, 1);
						BOARD.setIsWindowDirty(true);
						break;
					case GLFW.GLFW_KEY_0:
						BOARD.drawPlayerWeapon(0);
						BOARD.setIsWindowDirty(true);
						break;
					case GLFW.GLFW_KEY_1:
						BOARD.shootBulletPlayer(0);
						BOARD.setIsWindowDirty(true);
					}
				}

			}
		});


		try (MemoryStack stack = stackPush()) {
			// Dimensions de la fenêtre
			IntBuffer pWidth = stack.mallocInt(1);
			IntBuffer pHeight = stack.mallocInt(1);

			// Positionnement de la fenêtre
			GLFW.glfwGetWindowSize(window, pWidth, pHeight);
			GLFWVidMode vidmode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
			GLFW.glfwSetWindowPos(window,
					(vidmode.width() - pWidth.get(0)) / 2,
					(vidmode.height() - pHeight.get(0)) / 2);

			GLFW.glfwMakeContextCurrent(window);				// Contexte
			GLFW.glfwSwapInterval(1);							// v-sync
			GLFW.glfwShowWindow(window);						// Affichage de la fenêtre

			GL.createCapabilities();							// Initialisation d'OpenGL

			// Textures
	        GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
	        GL11.glEnable(GL_TEXTURE_2D);
	        GL11.glEnable(GL_BLEND);
	        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
			
	        // Définir la projection orthographique
	        glMatrixMode(GL_PROJECTION);
	        glLoadIdentity();
	        glOrtho(0, WINDOW_WIDTH, WINDOW_HEIGHT, 0, 1, -1);
	        glMatrixMode(GL_MODELVIEW);
		}
	}

	// Réalise l'affichage
	private void display() {
		// Nettoyage
		GraphicUtilities.cleanScreen(new Color(0, 0, 0));

		// Dessin
		BOARD.draw();

		// Échange des buffers
		GLFW.glfwSwapBuffers(window);

		// Après un affichage, la fenêtre n'est plus sale
		BOARD.setIsWindowDirty(false);
	}

	// Exécute la boucle de jeu
	private void loop() {
		keyboard = new Scanner(System.in);
		
		// Choix du mode
		System.out.println("- Vous voulez tester ? (Oui : 'o', Non : 'n') : ");
		confirmationWait : while(true) {
			switch(keyboard.nextLine()) {
			case "O":
			case "o":
				System.out.println("- Mode test !");
				// Création d'un joueur
				BOARD.addPlayer(0);
				BOARD.startGame();
				break confirmationWait;
			case "N":
			case "n":
				System.out.println("- Mode multijoueur !");
				// Début de la communication avec le jeu
				COMMUNICATION_HANDLER.start();
				break confirmationWait;
			default:
				System.out.println("- Mauvaise réponse ! Réessayez... (Oui : 'o', Non : 'n') : ");
			}
		}
		
		while (!GLFW.glfwWindowShouldClose(window)) {
			
			
			// On réaffice que quand y a besoin
			if (BOARD.getIsWindowDirty())
				display();
			
			if (BOARD.getIsDone())
				break;

			// Maj état de la fenêtre - évenéments
			GLFW.glfwPollEvents();
		}
		
		keyboard.close();
		
		COMMUNICATION_HANDLER.interrupt();
	}


	public static void main(String[] args) {
		new Game().run();
	}
}
