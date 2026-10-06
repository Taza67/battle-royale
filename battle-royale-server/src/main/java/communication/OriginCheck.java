package communication;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * Vérification de l'en-tête `Origin` des poignées de main WebSocket.
 * Le conteneur appelle {@link javax.websocket.server.ServerEndpointConfig.Configurator#checkOrigin}
 * avec la seule valeur d'`Origin` : le filtre {@link HandshakeFilter} expose donc la requête HTTP
 * en cours (en-tête `Host` et schéma) au configurateur, sur le même fil d'exécution.
 * @author mourtaza
 *
 */
public final class OriginCheck {
	private static final Logger LOG = Logger.getLogger(OriginCheck.class.getName());
	private static final ThreadLocal<HttpServletRequest> CURRENT = new ThreadLocal<>();

	private OriginCheck() {}

	/**
	 * Filtre qui mémorise la requête HTTP le temps de son traitement, pour que
	 * {@link #allowsCurrent(String)} connaisse l'hôte de la poignée de main
	 */
	public static final class HandshakeFilter implements Filter {
		@Override
		public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
			if (!(request instanceof HttpServletRequest http)) {
				chain.doFilter(request, response);
				return;
			}
			HttpServletRequest previous = CURRENT.get();
			CURRENT.set(http);
			try {
				chain.doFilter(request, response);
			} finally {
				if (previous == null)
					CURRENT.remove();
				else
					CURRENT.set(previous);
			}
		}
	}

	/**
	 * Indique si l'origine de la poignée de main en cours est acceptée
	 * @param origin Valeur de l'en-tête `Origin`, null s'il est absent
	 * @return true si l'origine est absente ou désigne l'hôte de la requête
	 */
	static boolean allowsCurrent(String origin) {
		if (origin == null)
			return true;
		HttpServletRequest request = CURRENT.get();
		if (request == null) {
			LOG.warning(() -> "Origine " + origin + " refusée : requête de poignée de main inconnue");
			return false;
		}
		boolean allowed = allows(origin, request.getHeader("Host"), effectiveScheme(request));
		if (!allowed)
			LOG.warning(() -> "Connexion WebSocket refusée : origine " + origin + " différente de l'hôte "
				+ request.getHeader("Host"));
		return allowed;
	}

	/**
	 * Indique si une origine correspond à l'hôte d'une requête
	 * @param origin Valeur de l'en-tête `Origin`, null s'il est absent
	 * @param host Valeur de l'en-tête `Host` de la requête
	 * @param requestScheme Schéma de la requête vu par le client (http ou https),
	 *                      pour le port par défaut de `Host`
	 * @return true si l'origine est absente, ou si son hôte et son port sont ceux de `Host`
	 */
	static boolean allows(String origin, String host, String requestScheme) {
		if (origin == null)
			return true;
		if (host == null || host.isBlank())
			return false;
		Authority expected = parseHost(host.strip(), defaultPort(requestScheme));
		Authority actual = parseOrigin(origin.strip());
		return expected != null && expected.equals(actual);
	}

	/**
	 * Hôte (en minuscules) et port d'une adresse
	 * @param host Nom ou adresse de l'hôte, crochets compris pour IPv6
	 * @param port Port explicite ou par défaut
	 */
	private record Authority(String host, int port) {}

	/**
	 * Retourne le schéma vu par le client : `Forwarded` puis `X-Forwarded-Proto`
	 * quand un mandataire termine TLS, sinon le schéma de la requête
	 * @param request Requête de poignée de main
	 * @return Schéma effectif (http ou https)
	 */
	private static String effectiveScheme(HttpServletRequest request) {
		String forwarded = request.getHeader("Forwarded");
		if (forwarded != null)
			for (String part : forwarded.split(";")) {
				String[] pair = part.trim().split("=", 2);
				if (pair.length == 2 && pair[0].equalsIgnoreCase("proto"))
					return pair[1].replace("\"", "").strip().toLowerCase(Locale.ROOT);
			}
		String proto = request.getHeader("X-Forwarded-Proto");
		if (proto != null && !proto.isBlank())
			return proto.split(",")[0].strip().toLowerCase(Locale.ROOT);
		return request.getScheme();
	}

	private static Authority parseOrigin(String origin) {
		try {
			URI uri = new URI(origin);
			String scheme = uri.getScheme();
			if (scheme == null || uri.getHost() == null || uri.getRawUserInfo() != null)
				return null;
			int port = uri.getPort() >= 0 ? uri.getPort() : defaultPort(scheme);
			if (port < 0)
				return null;
			return new Authority(uri.getHost().toLowerCase(Locale.ROOT), port);
		} catch (URISyntaxException e) {
			return null;
		}
	}

	private static Authority parseHost(String host, int defaultPort) {
		String name;
		String port;
		if (host.startsWith("[")) {
			int end = host.indexOf(']');
			if (end < 0)
				return null;
			name = host.substring(0, end + 1);
			String rest = host.substring(end + 1);
			if (!rest.isEmpty() && !rest.startsWith(":"))
				return null;
			port = rest.isEmpty() ? null : rest.substring(1);
		} else {
			int colon = host.indexOf(':');
			if (colon != host.lastIndexOf(':'))
				return null;
			name = colon < 0 ? host : host.substring(0, colon);
			port = colon < 0 ? null : host.substring(colon + 1);
		}
		if (name.isEmpty())
			return null;
		int parsedPort = defaultPort;
		if (port != null) {
			try {
				parsedPort = Integer.parseInt(port);
			} catch (NumberFormatException e) {
				return null;
			}
		}
		if (parsedPort < 0 || parsedPort > 65535)
			return null;
		return new Authority(name.toLowerCase(Locale.ROOT), parsedPort);
	}

	private static int defaultPort(String scheme) {
		if (scheme == null)
			return -1;
		return switch (scheme.toLowerCase(Locale.ROOT)) {
			case "http", "ws" -> 80;
			case "https", "wss" -> 443;
			default -> -1;
		};
	}
}
