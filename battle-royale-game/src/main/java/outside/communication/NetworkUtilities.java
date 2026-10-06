package outside.communication;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;

/**
 * Outils réseau de la vue
 * @author mourtaza
 */
public final class NetworkUtilities {
	private NetworkUtilities() {}

	/**
	 * Cherche l'adresse IPv4 de la machine sur le réseau local (privée de préférence)
	 * @return Adresse, ou "localhost" si aucune n'est trouvée
	 */
	public static String lanIPv4() {
		String fallback = null;
		try {
			for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
				String name = ni.getName().toLowerCase();
				boolean virtual = name.startsWith("docker") || name.startsWith("br-") || name.startsWith("veth") || name.startsWith("virbr");
				for (InetAddress a : Collections.list(ni.getInetAddresses())) {
					if (!(a instanceof Inet4Address) || a.isLoopbackAddress() || a.isLinkLocalAddress()) continue;
					if (virtual) continue;
					if (a.isSiteLocalAddress()) return a.getHostAddress();
					if (fallback == null) fallback = a.getHostAddress();
				}
			}
		} catch (SocketException e) {
			return "localhost";
		}
		return fallback != null ? fallback : "localhost";
	}

	/**
	 * Construit l'adresse de la manette web
	 * @param host Hôte du serveur web
	 * @return Adresse
	 */
	public static String gamepadUrl(String host) {
		return "http://" + host + ":8080/battle-royale-server/gamepad/";
	}
}
