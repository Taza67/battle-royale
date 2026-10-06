package communication;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import com.google.gson.Gson;

@ServerEndpoint("/websocketserver")
public class WebSocketServer {
	private volatile static Admin admin;
	private volatile static boolean adminIsInitialized = false;
	private Player player;
	private boolean isInitialized = false;
	private final Gson JSON = new Gson();
	
	@OnOpen
	public void onOpen(Session session) {
		System.err.println ("- Nouveau client : " + session.getId());
	}

	@OnMessage
	public void onMessage(String message, Session session) {
		System.out.println("- Message : " + message);
		
		// Message de création
		if (!isInitialized && message.contains("informations")) {
			String response = "ko";
			
			// Administrateur ?
			if (!adminIsInitialized && message.contains("admin")) {
				// Création de l'administrateur
				admin = new Admin(session);
				adminIsInitialized = true;
				
				// Message de confirmation
				response = "ok";
			} else if (adminIsInitialized) {	// Joueur && admin existant ?
				// Récupération du joueur
				PlayerInformations infos = JSON.fromJson(message, PlayerInformations.class);
				
				// Le joueur existe déjà ?
				if (admin.checkClientExistence(infos.getPseudo())) {
					Player tmp = admin.getClient(infos.getPseudo());
					
					if (tmp.getSession() != null) {
						response = "ko";
					} else {
						// Réassociation du joueur + récupération
						player = admin.reassociateClient(infos.getPseudo(), session);
						
						// Message de confirmation
						response = Integer.toString(player.getID());
					}
					
					// Envoi du joueur à l'admin
					admin.getSession().getAsyncRemote().sendText(player.getJSon());
					
					isInitialized = true;
				} else if (Admin.getPlayerInstances() < Admin.MAX_PLAYERS) {
					// S'il reste de la place
					// Création du joueur
					player = admin.addPlayer(infos.getPseudo(), session);
					
					// Message de confirmation
					response = Integer.toString(player.getID());
					
					// Envoi du joueur à l'admin
					admin.getSession().getAsyncRemote().sendText(player.getJSon());
					
					isInitialized = true;
				}
			}
			
			// Confirmation de connexion
			session.getAsyncRemote().sendText(response);
		} else if (adminIsInitialized && message.contains("admin")) {
			// Message venant de l'administrateur
			boolean response = false;
			
			// Requête de l'administrateur
			if (message.contains("debut")) {
				response = admin.startGame();
			} else if (message.contains("pause")) {
				response = admin.pauseGame();
			} else if (message.contains("stop")) {
				response = admin.stopGame();
			} else if (message.contains("continue")) {
				response = admin.continueGame();
			}
			
			// Confirmation de réalisation de requête
			String confMessage = "ko";
			if (response) confMessage = "ok";
			session.getAsyncRemote().sendText(confMessage);
		} else if (Admin.getGameIsRunning()) {
			if (message.contains("movement")) {
				// Récupération des infos sur le déplacement
				MoveInformations moveInfos = JSON.fromJson(message, MoveInformations.class);
				
				// Entrée du déplacement dans l'ensemble de données
				player.putMoveAction((byte)moveInfos.getDirection(), (byte)moveInfos.getSpeed());
			} else if (message.contains("attack")) {
				// Récupération des infos sur l'attaque
				AttackInformations attackInfos = JSON.fromJson(message, AttackInformations.class);
				
				// Entrée de l'attaque dans l'ensemble de données
				player.putAttackAction((byte)attackInfos.getForm());
			}
		}
	}

	@OnClose
	public void onClose(Session session, CloseReason closeReason) {
		System.out.println("- Fermeture de la Session : " + session.getId() +
						   "\n  pour la raison : " + closeReason.getReasonPhrase());
		player.destroySession();
	}

	@OnError
	public void onError(Session session, Throwable t) {
		System.out.println ("- Erreur : " + session.getId());
		player.destroySession();
	}
}

