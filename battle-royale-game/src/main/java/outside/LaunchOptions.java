package outside;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

import inside.GameSettings;
import inside.IConfig;
import outside.communication.GameServer;
import outside.communication.NetworkUtilities;

/**
 * Options de lancement du jeu, lues sur la ligne de commande
 * @param multi true pour le mode multijoueur (serveur web et manettes)
 * @param bots Nombre de robots
 * @param port Port TCP du jeu (mode multijoueur)
 * @param bind Adresse d'écoute du serveur TCP du jeu (mode multijoueur)
 * @param warmupSeconds Durée de l'échauffement en secondes
 * @param seed Graine aléatoire
 * @param pseudo Pseudo du joueur local (mode solo)
 * @param spectate true pour regarder une partie entre robots (mode solo)
 * @param sound false pour désactiver le son
 * @param windowWidth Largeur initiale de la fenêtre
 * @param windowHeight Hauteur initiale de la fenêtre
 * @param gamepadUrl Adresse de la manette affichée dans la salle d'attente (null pour la déduire de l'adresse locale)
 * @author mourtaza
 */
public record LaunchOptions(boolean multi, int bots, int port, String bind, float warmupSeconds, long seed, String pseudo,
	boolean spectate, boolean sound, int windowWidth, int windowHeight, String gamepadUrl) implements IConfig {

	/**
	 * Port TCP par défaut
	 */
	public static final int DEFAULT_PORT = 8000;
	/**
	 * Nombre de robots par défaut en solo
	 */
	public static final int DEFAULT_SOLO_BOTS = 9;

	/**
	 * Texte d'aide
	 */
	public static final String USAGE = String.join("\n",
		"Utilisation : battle-royale-game [options]",
		"  --mode solo|multi   mode de jeu (solo par défaut)",
		"  --bots N            nombre de robots (" + DEFAULT_SOLO_BOTS + " en solo, 0 en multijoueur)",
		"  --port P            port TCP du jeu en multijoueur (" + DEFAULT_PORT + " par défaut)",
		"  --bind ADRESSE      adresse d'écoute du jeu (" + GameServer.DEFAULT_BIND_ADDRESS + " par défaut,",
		"                      0.0.0.0 si le serveur web tourne sur une autre machine)",
		"  --warmup S          durée de l'échauffement en secondes (" + GameSettings.DEFAULT_WARMUP_SECONDS + " par défaut)",
		"  --seed N            graine aléatoire (carte, zones, robots)",
		"  --pseudo NOM        pseudo du joueur en solo",
		"  --spectate          solo : regarder une partie entre robots",
		"  --no-sound          désactiver le son",
		"  --window LxH        taille initiale de la fenêtre (1280x720 par défaut)",
		"  --gamepad-url URL   adresse de la manette affichée dans la salle d'attente",
		"                      (par défaut http://<adresse locale>:8080/battle-royale-server/gamepad/)",
		"  --help              afficher cette aide");

	/**
	 * Lit les options de la ligne de commande
	 * @param args Arguments
	 * @return Options
	 * @throws IllegalArgumentException Option inconnue ou valeur invalide
	 */
	public static LaunchOptions parse(String... args) {
		Boolean multi = null;
		Integer bots = null;
		int port = DEFAULT_PORT, width = 1280, height = 720;
		float warmup = GameSettings.DEFAULT_WARMUP_SECONDS;
		long seed = System.nanoTime();
		String pseudo = "Joueur", bind = GameServer.DEFAULT_BIND_ADDRESS, gamepadUrl = null;
		boolean spectate = false, sound = true;

		for (int i = 0; i < args.length; i++) {
			String a = args[i];
			switch (a) {
			case "--mode" -> {
				String m = value(args, ++i, a);
				if (m.equals("solo")) multi = false;
				else if (m.equals("multi")) multi = true;
				else throw new IllegalArgumentException("Mode inconnu : " + m + " (solo ou multi)");
			}
			case "--bots" -> bots = intValue(args, ++i, a, 0, MAX_PLAYERS - 1);
			case "--port" -> port = intValue(args, ++i, a, 1, 65535);
			case "--bind" -> {
				bind = value(args, ++i, a).strip();
				if (bind.isEmpty() || bind.chars().anyMatch(Character::isWhitespace))
					throw new IllegalArgumentException("Adresse invalide pour --bind : " + args[i]);
			}
			case "--warmup" -> {
				String v = value(args, ++i, a);
				try {
					warmup = Float.parseFloat(v);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Valeur invalide pour --warmup : " + v);
				}
				if (!(warmup >= 0 && warmup <= 600)) throw new IllegalArgumentException("--warmup doit être entre 0 et 600");
			}
			case "--seed" -> {
				String v = value(args, ++i, a);
				try {
					seed = Long.parseLong(v);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Valeur invalide pour --seed : " + v);
				}
			}
			case "--pseudo" -> {
				pseudo = value(args, ++i, a).strip();
				if (pseudo.isEmpty() || pseudo.length() > 16) throw new IllegalArgumentException("Le pseudo doit faire de 1 à 16 caractères");
			}
			case "--spectate" -> spectate = true;
			case "--no-sound" -> sound = false;
			case "--window" -> {
				String v = value(args, ++i, a);
				String[] parts = v.toLowerCase().split("x");
				try {
					width = Integer.parseInt(parts[0]);
					height = Integer.parseInt(parts[1]);
				} catch (RuntimeException e) {
					throw new IllegalArgumentException("Taille de fenêtre invalide : " + v + " (ex. 1600x900)");
				}
				if (width < 320 || height < 180) throw new IllegalArgumentException("Fenêtre trop petite : " + v);
			}
			case "--gamepad-url" -> {
				gamepadUrl = value(args, ++i, a).strip();
				if (!gamepadUrl.matches("https?://\\S+"))
					throw new IllegalArgumentException("Adresse invalide pour --gamepad-url : " + args[i] + " (ex. http://192.168.1.20:8080/battle-royale-server/gamepad/)");
			}
			default -> throw new IllegalArgumentException("Option inconnue : " + a);
			}
		}

		boolean m = multi != null && multi;
		int b = bots != null ? bots : (m ? 0 : DEFAULT_SOLO_BOTS);
		if (!m && spectate && b < 2) throw new IllegalArgumentException("Il faut au moins 2 robots pour --spectate");
		if (m && spectate) throw new IllegalArgumentException("--spectate n'est disponible qu'en solo");
		return new LaunchOptions(m, b, port, bind, warmup, seed, pseudo, spectate, sound, width, height, gamepadUrl);
	}

	/**
	 * Demande le mode de jeu dans la console (utilisé seulement sans argument)
	 * @param in Entrée de la console
	 * @param out Sortie de la console
	 * @return Options
	 */
	public static LaunchOptions ask(BufferedReader in, PrintStream out) {
		try {
			out.print("- Partie solo contre des robots ? (o/n, o par défaut) : ");
			out.flush();
			String line = in.readLine();
			boolean multi = line != null && line.strip().toLowerCase().startsWith("n");
			if (multi) return parse("--mode", "multi");

			out.print("- Nombre de robots (" + DEFAULT_SOLO_BOTS + " par défaut) : ");
			out.flush();
			line = in.readLine();
			if (line == null || line.isBlank()) return parse("--mode", "solo");
			return parse("--mode", "solo", "--bots", line.strip());
		} catch (IOException | IllegalArgumentException e) {
			out.println("- Entrée invalide, lancement en solo par défaut");
			return parse("--mode", "solo");
		}
	}

	/**
	 * Retourne l'adresse de la manette à afficher : celle de --gamepad-url, sinon celle du serveur web
	 * supposé lancé sur cette machine
	 * @return Adresse
	 */
	public String effectiveGamepadUrl() {
		return gamepadUrl != null ? gamepadUrl : NetworkUtilities.gamepadUrl(NetworkUtilities.lanIPv4());
	}

	/**
	 * Construit les réglages de partie correspondants
	 * @return Réglages
	 */
	public GameSettings settings() {
		return GameSettings.defaults(seed).withWarmup(warmupSeconds);
	}

	private static String value(String[] args, int i, String option) {
		if (i >= args.length) throw new IllegalArgumentException("Valeur manquante pour " + option);
		return args[i];
	}

	private static int intValue(String[] args, int i, String option, int min, int max) {
		String v = value(args, i, option);
		int n;
		try {
			n = Integer.parseInt(v);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Valeur invalide pour " + option + " : " + v);
		}
		if (n < min || n > max) throw new IllegalArgumentException(option + " doit être entre " + min + " et " + max);
		return n;
	}
}
