package communication;

import java.io.File;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.DeploymentException;
import javax.websocket.server.ServerContainer;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Wrapper;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.apache.tomcat.websocket.BackgroundProcess;

import communication.session.GameSession;

/**
 * Point d'entrée du serveur web : démarre un Tomcat embarqué qui sert
 * la manette, le panneau d'administration et le point d'accès WebSocket
 * @author mourtaza
 *
 */
public class ServerLauncher {
	/**
	 * Chemin de contexte attendu par les clients web
	 */
	public static final String CONTEXT_PATH = "/battle-royale-server";
	/**
	 * Propriété fixant le niveau de journalisation du serveur (INFO par défaut)
	 */
	public static final String LOG_LEVEL = "battle-royale.log-level";

	private static final String LOG_FORMAT = "java.util.logging.SimpleFormatter.format";

	static {
		if (System.getProperty(LOG_FORMAT) == null)
			System.setProperty(LOG_FORMAT, "%1$tF %1$tT %4$-7s [%3$s] %5$s%6$s%n");
	}

	private static final Logger LOG = Logger.getLogger(ServerLauncher.class.getName());
	/**
	 * Journal parent de toutes les classes du serveur, conservé pour garder son niveau
	 */
	private static final Logger ROOT = Logger.getLogger("communication");

	/**
	 * Démarre le serveur
	 * @param args Arguments (inutilisés)
	 * @throws LifecycleException si Tomcat ne peut pas démarrer
	 */
	public static void main(String[] args) throws LifecycleException {
		configureLogging(System.getProperty(LOG_LEVEL, "INFO"));
		ServerConfig config = ServerConfig.fromSystemProperties();
		LOG.config(config::toString);
		if (config.adminPassword() == null)
			LOG.warning(() -> "Aucun mot de passe administrateur (propriété " + ServerConfig.ADMIN_PASSWORD + ") : "
				+ "la place d'administrateur revient à la première session qui la réclame");

		GameSession game = new GameSession(config.game(), config.adminPassword());
		Tomcat tomcat = start(config.port(), resolveWebappDirectory(config.webapp()), game);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> stop(tomcat, game), "battle-royale-shutdown"));

		LOG.info(() -> "Serveur web prêt : http://localhost:" + config.port() + CONTEXT_PATH + "/gamepad/ (jeu attendu sur "
			+ config.game().host() + ":" + config.game().port() + ", mot de passe administrateur "
			+ (config.adminPassword() == null ? "non requis" : "requis") + ")");
		tomcat.getServer().await();
	}

	/**
	 * Démarre Tomcat et enregistre le point d'accès WebSocket lié à la session de jeu
	 * @param port Port HTTP
	 * @param webapp Répertoire des fichiers statiques
	 * @param game Session de jeu partagée par les connexions
	 * @return Instance de Tomcat démarrée
	 * @throws LifecycleException si Tomcat ne peut pas démarrer
	 * @throws IllegalStateException si le port est indisponible ou si le point d'accès ne peut pas être enregistré
	 */
	@SuppressWarnings("deprecation")
	public static Tomcat start(int port, File webapp, GameSession game) throws LifecycleException {
		Tomcat tomcat = new Tomcat();
		tomcat.setBaseDir(new File(System.getProperty("java.io.tmpdir"), "battle-royale-tomcat-" + port).getAbsolutePath());
		tomcat.setPort(port);
		tomcat.getConnector();
		tomcat.setAddDefaultWebXmlToWebapp(false);

		Context context = tomcat.addWebapp(CONTEXT_PATH, webapp.getAbsolutePath());
		context.setAddWebinfClassesResources(true);
		Wrapper files = Tomcat.addServlet(context, "default", DefaultServlet.class.getName());
		files.addInitParameter("listings", "false");
		files.setLoadOnStartup(1);
		context.addServletMappingDecoded("/", "default", false);
		context.addWelcomeFile("index.html");
		FilterDef handshake = new FilterDef();
		handshake.setFilterName("websocket-handshake");
		handshake.setFilter(new OriginCheck.HandshakeFilter());
		handshake.setAsyncSupported("true");
		context.addFilterDef(handshake);
		FilterMap handshakeMap = new FilterMap();
		handshakeMap.setFilterName(handshake.getFilterName());
		handshakeMap.addURLPatternDecoded(WebSocketServer.PATH);
		context.addFilterMap(handshakeMap);
		Tomcat.addDefaultMimeTypeMappings(context);
		if (context.getJarScanner() instanceof StandardJarScanner scanner)
			scanner.setScanClassPath(false);

		tomcat.start();
		try {
			if (tomcat.getConnector().getState() != LifecycleState.STARTED)
				throw new IllegalStateException("Port " + port + " indisponible");

			Object container = context.getServletContext().getAttribute(ServerContainer.class.getName());
			if (!(container instanceof ServerContainer))
				throw new IllegalStateException("Conteneur WebSocket indisponible");
			if (container instanceof BackgroundProcess process)
				process.setProcessPeriod(1);
			((ServerContainer) container).addEndpoint(WebSocketServer.config(game));
		} catch (DeploymentException | RuntimeException e) {
			destroyQuietly(tomcat);
			if (e instanceof IllegalStateException ise)
				throw ise;
			throw new IllegalStateException("Impossible d'enregistrer le point d'accès WebSocket", e);
		}
		return tomcat;
	}

	/**
	 * Arrête la partie puis Tomcat
	 * @param tomcat Instance de Tomcat
	 * @param game Session de jeu
	 */
	public static void stop(Tomcat tomcat, GameSession game) {
		game.close();
		destroyQuietly(tomcat);
	}

	/**
	 * Fixe le niveau de journalisation des classes du serveur
	 * @param level Nom du niveau (SEVERE, WARNING, INFO, FINE, ...)
	 */
	static void configureLogging(String level) {
		Level parsed;
		try {
			parsed = Level.parse(level.strip().toUpperCase());
		} catch (IllegalArgumentException e) {
			parsed = Level.INFO;
		}
		ROOT.setLevel(parsed);
		for (Handler handler : Logger.getLogger("").getHandlers())
			if (handler.getLevel().intValue() > parsed.intValue())
				handler.setLevel(parsed);
	}

	/**
	 * Arrête et détruit Tomcat sans propager d'erreur
	 * @param tomcat Instance de Tomcat
	 */
	private static void destroyQuietly(Tomcat tomcat) {
		try {
			if (tomcat.getServer().getState().isAvailable())
				tomcat.stop();
			tomcat.destroy();
		} catch (LifecycleException e) {
			LOG.log(Level.WARNING, "Arrêt de Tomcat incomplet", e);
		}
	}

	/**
	 * Retourne le répertoire contenant les fichiers statiques
	 * @param configured Répertoire configuré, ou null pour le rechercher
	 * @return Répertoire de l'application web
	 */
	private static File resolveWebappDirectory(String configured) {
		if (configured != null)
			return new File(configured);

		for (String candidate : new String[] { "src/main/webapp", "webapp", "../webapp" }) {
			File dir = new File(candidate);
			if (new File(dir, "gamepad").isDirectory())
				return dir;
		}

		throw new IllegalStateException("Répertoire webapp introuvable (option -D" + ServerConfig.WEBAPP + ")");
	}
}
