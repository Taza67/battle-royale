package communication;

import java.io.File;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;

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
	 * Démarre le serveur
	 * @param args Arguments (inutilisés)
	 * @throws LifecycleException si Tomcat ne peut pas démarrer
	 */
	public static void main(String[] args) throws LifecycleException {
		int port = Integer.parseInt(System.getProperty("battle-royale.port", "8080"));
		File webapp = resolveWebappDirectory();

		Tomcat tomcat = new Tomcat();
		tomcat.setBaseDir(new File(System.getProperty("java.io.tmpdir"), "battle-royale-tomcat").getAbsolutePath());
		tomcat.setPort(port);
		tomcat.getConnector();

		Context context = tomcat.addWebapp(CONTEXT_PATH, webapp.getAbsolutePath());
		context.setAddWebinfClassesResources(true);

		tomcat.start();
		System.err.println("- Serveur web prêt : http://localhost:" + port + CONTEXT_PATH + "/gamepad/");
		tomcat.getServer().await();
	}

	/**
	 * Retourne le répertoire contenant les fichiers statiques
	 * @return Répertoire de l'application web
	 */
	private static File resolveWebappDirectory() {
		String configured = System.getProperty("battle-royale.webapp");
		if (configured != null)
			return new File(configured);

		for (String candidate : new String[] { "src/main/webapp", "webapp", "../webapp" }) {
			File dir = new File(candidate);
			if (new File(dir, "gamepad").isDirectory())
				return dir;
		}

		throw new IllegalStateException("- Répertoire webapp introuvable (option -Dbattle-royale.webapp)");
	}
}
