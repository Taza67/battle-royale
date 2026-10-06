package outside.graphic;

import static outside.graphic.GraphicUtilities.*;

import java.text.Normalizer;
import java.util.List;

import inside.BoardSnapshot;
import inside.BoardSnapshot.KillFeedEntry;
import inside.BoardSnapshot.PlayerState;
import inside.DamageCause;
import inside.IConfig;
import inside.Phase;
import inside.SafeZone;
import outside.graphic.Font.Align;

/**
 * Dessin de l'interface : HUD, bandeaux, pause, écran de fin et salle d'attente
 * @author mourtaza
 */
public class HudRenderer implements IConfig {
	/**
	 * Informations de la vue nécessaires au HUD
	 * @param localId Identifiant du joueur local (-1 si aucun)
	 * @param multi true en mode multijoueur
	 * @param gamepadUrl Adresse de la manette web
	 * @param port Port TCP du jeu
	 * @param status Dernier message de connexion
	 * @param statusError true si ce message est une erreur
	 * @param statusTime Instant du message (secondes)
	 * @param connectionLost true si la connexion avec le serveur web a été perdue en partie
	 */
	public record HudInfo(int localId, boolean multi, String gamepadUrl, int port, String status, boolean statusError,
		double statusTime, boolean connectionLost) {}

	/**
	 * Durée d'affichage d'une élimination dans le fil, en secondes
	 */
	private static final float KILL_FEED_SECONDS = 8;

	private final Fonts FONTS;
	private final TextureManager TEXTURES;


	/**
	 * Construit le dessinateur de l'interface
	 * @param fonts Polices
	 * @param textures Textures
	 */
	public HudRenderer(Fonts fonts, TextureManager textures) {
		FONTS = fonts;
		TEXTURES = textures;
	}

	/**
	 * Dessine le HUD d'une partie en cours ou terminée
	 * @param s Image du plateau
	 * @param info Informations de la vue
	 * @param effects Effets (bandeau courant)
	 * @param time Instant courant (secondes)
	 * @param endTime Instant de la fin de partie (secondes, négatif si la partie continue)
	 */
	public void render(BoardSnapshot s, HudInfo info, Effects effects, double time, double endTime) {
		renderStatusPanel(s);
		renderKillFeed(s);

		PlayerState local = info.localId() >= 0 ? s.player(info.localId()) : null;
		if (local != null && !s.isOver()) renderLocalPlayer(local);

		if (s.phase() == Phase.WARMUP) renderWarmup(s, info);
		if (local != null && !local.alive() && !s.isOver())
			FONTS.MEDIUM.drawShadowed("Vous êtes éliminé - mode spectateur", MAP_WIDTH / 2, MAP_HEIGHT - 58, Color.LIGHT_GREY, Align.CENTER);

		Effects.Banner banner = effects.getBanner();
		if (banner != null && !s.isOver() && !s.paused()) renderBanner(banner, time);

		if (info.connectionLost() && !s.isOver()) {
			panel(MAP_WIDTH / 2 - 260, 92, MAP_WIDTH / 2 + 260, 128, 8, new Color(0.45f, 0.05f, 0.05f, 0.85f));
			FONTS.SMALL.draw("Connexion avec le serveur web perdue", MAP_WIDTH / 2, 101, Color.WHITE, Align.CENTER);
		}

		if (s.paused() && !s.isOver()) renderPause(info, time);
		if (s.isOver()) renderEnd(s, info, time, endTime);

		renderStatusToast(info, time);
	}

	/**
	 * Panneau en haut à gauche : phase, compte à rebours, joueurs vivants, vague
	 * @param s Image du plateau
	 */
	private void renderStatusPanel(BoardSnapshot s) {
		String timer;
		Color timerColor = Color.WHITE;
		switch (s.phase()) {
		case WARMUP:
			timer = "Échauffement : combat dans " + s.secondsLeft() + " s";
			timerColor = Color.CYAN;
			break;
		case BATTLE:
			if (s.zoneStage() == SafeZone.Stage.WAITING) timer = "Prochaine zone dans " + s.secondsLeft() + " s";
			else if (s.zoneStage() == SafeZone.Stage.SHRINKING) {
				timer = "La zone se resserre : " + s.secondsLeft() + " s";
				timerColor = Color.ORANGE;
			} else timer = "Zone finale";
			break;
		default:
			timer = s.stopped() ? "Partie arrêtée" : "Partie terminée";
		}

		float w = Math.max(250, FONTS.SMALL.width(timer) + 28);
		panel(12, 12, 12 + w, s.phase() == Phase.BATTLE ? 94 : 72, 8, new Color(0.05f, 0.07f, 0.1f, 0.72f));
		FONTS.SMALL.draw(timer, 26, 22, timerColor, Align.LEFT);
		FONTS.SMALL.draw("Vivants : " + s.alive() + " / " + s.total(), 26, 46, Color.WHITE, Align.LEFT);
		if (s.phase() == Phase.BATTLE) {
			String wave = s.zoneStage() == SafeZone.Stage.CLOSED ? "Toutes les vagues sont passées"
				: "Vague " + Math.min(s.wave(), s.waveCount()) + " / " + s.waveCount();
			FONTS.LABEL.draw(wave + "   -   lave : " + s.lavaDamagePerSecond() + " PV/s", 26, 72, Color.LIGHT_GREY, Align.LEFT);
		}
	}

	/**
	 * Fil des éliminations en haut à droite
	 * @param s Image du plateau
	 */
	private void renderKillFeed(BoardSnapshot s) {
		List<KillFeedEntry> feed = s.killFeed();
		float y = 14;
		for (int i = feed.size() - 1; i >= 0 && i >= feed.size() - 5; i--) {
			KillFeedEntry e = feed.get(i);
			float age = (s.tick() - e.tick()) / (float)TICKS_PER_SECOND;
			if (age > KILL_FEED_SECONDS && !s.isOver()) continue;
			float a = s.isOver() ? 1 : Math.min(1, (KILL_FEED_SECONDS - age) / 1.5f);

			String victim = s.pseudoOf(e.victimId());
			String text = e.killerId() < 0 ? victim + " a fondu dans la lave"
				: s.pseudoOf(e.killerId()) + " » " + victim + (e.cause() == DamageCause.MELEE ? "  (épée)" : "  (tir)");
			float w = FONTS.SMALL.width(text) + 22;
			panel(MAP_WIDTH - 12 - w, y, MAP_WIDTH - 12, y + 26, 6, new Color(0.05f, 0.07f, 0.1f, 0.65f * a));
			FONTS.SMALL.draw(text, MAP_WIDTH - 23, y + 4, (e.killerId() < 0 ? Color.ORANGE : Color.WHITE).withAlpha(a), Align.RIGHT);
			y += 31;
		}
	}

	/**
	 * Panneau du joueur local en bas à gauche : vie, éliminations, recharges
	 * @param p Joueur local
	 */
	private void renderLocalPlayer(PlayerState p) {
		float x = 12, y = MAP_HEIGHT - 86;
		panel(x, y, x + 300, MAP_HEIGHT - 12, 8, new Color(0.05f, 0.07f, 0.1f, 0.75f));
		texture(TEXTURES.getPlayerTexture(p.id(), !p.alive()), x + 10, y + 10, x + 62, y + 62, Color.WHITE);

		float ratio = p.life() / (float)MAX_LIFE_POINTS;
		fillRect(x + 74, y + 12, x + 288, y + 30, new Color(0, 0, 0, 0.6f));
		fillRect(x + 74, y + 12, x + 74 + 214 * ratio, y + 30, Color.life(ratio));
		FONTS.SMALL.drawShadowed(p.life() + " PV", x + 181, y + 12, Color.WHITE, Align.CENTER);

		FONTS.LABEL.draw(p.pseudo() + "   -   éliminations : " + p.kills(), x + 74, y + 36, Color.WHITE, Align.LEFT);
		cooldown("Épée", x + 74, y + 56, 1 - p.meleeCooldown() / (float)MELEE_COOLDOWN_TICKS);
		cooldown("Tir", x + 184, y + 56, 1 - p.shootCooldown() / (float)SHOOT_COOLDOWN_TICKS);
	}

	/**
	 * Jauge de recharge
	 * @param label Nom de l'attaque
	 * @param x Abscisse
	 * @param y Ordonnée
	 * @param ready Proportion rechargée
	 */
	private void cooldown(String label, float x, float y, float ready) {
		ready = Math.max(0, Math.min(1, ready));
		FONTS.LABEL.draw(label, x, y, ready >= 1 ? Color.WHITE : Color.LIGHT_GREY, Align.LEFT);
		fillRect(x + 36, y + 3, x + 104, y + 10, new Color(0, 0, 0, 0.6f));
		fillRect(x + 36, y + 3, x + 36 + 68 * ready, y + 10, ready >= 1 ? Color.CYAN : Color.CYAN.withAlpha(0.45f));
	}

	/**
	 * Aide et compte à rebours de l'échauffement
	 * @param s Image du plateau
	 * @param info Informations de la vue
	 */
	private void renderWarmup(BoardSnapshot s, HudInfo info) {
		String help = info.multi() ? "Échauffement : aucun dégât, prenez vos marques avec la manette"
			: "Flèches / ZQSD / WASD : se déplacer   -   Espace / J : tir   -   K / Entrée : épée   -   P : pause";
		float w = FONTS.SMALL.width(help) + 30;
		panel(MAP_WIDTH / 2 - w / 2, MAP_HEIGHT - 44, MAP_WIDTH / 2 + w / 2, MAP_HEIGHT - 12, 8, new Color(0.05f, 0.07f, 0.1f, 0.7f));
		FONTS.SMALL.draw(help, MAP_WIDTH / 2, MAP_HEIGHT - 38, Color.WHITE, Align.CENTER);

		if (s.secondsLeft() <= 3 && s.secondsLeft() > 0 && !s.paused())
			FONTS.TITLE.drawShadowed(Integer.toString(s.secondsLeft()), MAP_WIDTH / 2, MAP_HEIGHT / 2 - 120, Color.GOLD, Align.CENTER);
	}

	/**
	 * Bandeau central
	 * @param b Bandeau
	 * @param time Instant courant
	 */
	private void renderBanner(Effects.Banner b, double time) {
		float a = b.alpha(time);
		if (a <= 0) return;
		float y = 150, w = Math.max(FONTS.LARGE.width(b.title()), FONTS.MEDIUM.width(b.subtitle())) + 80;
		gradientRect(MAP_WIDTH / 2 - w / 2, y - 12, MAP_WIDTH / 2 + w / 2, y + 90, new Color(0, 0, 0, 0.55f * a), new Color(0, 0, 0, 0.15f * a));
		FONTS.LARGE.drawShadowed(b.title(), MAP_WIDTH / 2, y, b.color().withAlpha(a), Align.CENTER);
		if (!b.subtitle().isEmpty())
			FONTS.MEDIUM.drawShadowed(b.subtitle(), MAP_WIDTH / 2, y + 52, Color.WHITE.withAlpha(a), Align.CENTER);
	}

	/**
	 * Écran de pause
	 * @param info Informations de la vue
	 * @param time Instant courant
	 */
	private void renderPause(HudInfo info, double time) {
		fillRect(0, 0, MAP_WIDTH, MAP_HEIGHT, new Color(0, 0, 0, 0.55f));
		float pulse = 0.75f + 0.25f * (float)Math.sin(time * 3);
		FONTS.TITLE.drawShadowed("PAUSE", MAP_WIDTH / 2, MAP_HEIGHT / 2 - 50, Color.WHITE.withAlpha(pulse), Align.CENTER);
		FONTS.MEDIUM.drawShadowed(info.multi() ? "La partie a été suspendue par l'administrateur" : "Appuyez sur P pour reprendre",
			MAP_WIDTH / 2, MAP_HEIGHT / 2 + 20, Color.LIGHT_GREY, Align.CENTER);
	}

	/**
	 * Écran de fin : vainqueur et classement
	 * @param s Image finale
	 * @param info Informations de la vue
	 * @param time Instant courant
	 * @param endTime Instant de la fin
	 */
	private void renderEnd(BoardSnapshot s, HudInfo info, double time, double endTime) {
		float a = (float)Math.min(1, Math.max(0, (time - Math.max(0, endTime)) / 0.8));
		fillRect(0, 0, MAP_WIDTH, MAP_HEIGHT, new Color(0.02f, 0.03f, 0.06f, 0.78f * a));

		// Emblème du jeu en filigrane derrière l'écran de fin
		Texture emblem = TEXTURES.getEmblem();
		if (emblem != null) {
			float es = 430 + 6 * (float)Math.sin(time * 1.2);
			texture(SubTexture.whole(emblem), MAP_WIDTH / 2 - es / 2, MAP_HEIGHT / 2 - es / 2,
				MAP_WIDTH / 2 + es / 2, MAP_HEIGHT / 2 + es / 2, Color.WHITE.withAlpha(0.10f * a));
		}

		PlayerState winner = s.winnerId() >= 0 ? s.player(s.winnerId()) : null;
		String title, subtitle;
		Color color = Color.GOLD;
		if (winner != null) {
			title = winner.id() == info.localId() ? "Victoire !" : "Victoire " + of(winner.pseudo());
			subtitle = winner.kills() + " élimination" + (winner.kills() > 1 ? "s" : "") + "   -   " + winner.life() + " PV restants";
		} else if (s.stopped()) {
			title = "Partie arrêtée";
			subtitle = s.alive() + " survivant" + (s.alive() > 1 ? "s" : "") + " à égalité";
			color = Color.WHITE;
		} else {
			title = "Égalité";
			subtitle = "Les derniers joueurs sont tombés en même temps";
			color = Color.ORANGE;
		}

		float top = 58;
		if (winner != null) {
			float bob = (float)Math.sin(time * 2.5) * 4;
			glow(TEXTURES.getGlow(), MAP_WIDTH / 2, top + 44 + bob, 70, 70, Color.GOLD.withAlpha(0.45f * a));
			texture(TEXTURES.getPlayerTexture(winner.id(), false), MAP_WIDTH / 2 - 36, top + 8 + bob, MAP_WIDTH / 2 + 36, top + 80 + bob, Color.WHITE.withAlpha(a));
			top += 92;
		}
		FONTS.LARGE.drawShadowed(title, MAP_WIDTH / 2, top, color.withAlpha(a), Align.CENTER);
		FONTS.MEDIUM.drawShadowed(subtitle, MAP_WIDTH / 2, top + 50, Color.LIGHT_GREY.withAlpha(a), Align.CENTER);

		// Classement
		List<PlayerState> ranking = s.ranking();
		float x1 = MAP_WIDTH / 2 - 270, x2 = MAP_WIDTH / 2 + 270, y = top + 92;
		int rows = Math.min(ranking.size(), winner != null ? 12 : 14);
		panel(x1, y - 8, x2, y + 30 + rows * 28 + 8, 10, new Color(0.08f, 0.1f, 0.15f, 0.85f * a));
		FONTS.SMALL.draw("Rang", x1 + 20, y, Color.LIGHT_GREY.withAlpha(a), Align.LEFT);
		FONTS.SMALL.draw("Joueur", x1 + 110, y, Color.LIGHT_GREY.withAlpha(a), Align.LEFT);
		FONTS.SMALL.draw("Éliminations", x2 - 20, y, Color.LIGHT_GREY.withAlpha(a), Align.RIGHT);
		y += 30;

		boolean localShown = false;
		for (int i = 0; i < rows; i++) {
			PlayerState p = ranking.get(i);
			localShown |= p.id() == info.localId();
			rankingRow(p, i, x1, x2, y, a, p.id() == info.localId());
			y += 28;
		}
		if (!localShown && info.localId() >= 0) {
			PlayerState local = s.player(info.localId());
			if (local != null) {
				panel(x1, y + 4, x2, y + 34, 8, new Color(0.08f, 0.1f, 0.15f, 0.85f * a));
				rankingRow(local, rows, x1, x2, y + 6, a, true);
			}
		}

		String hint = info.multi() ? "En attente d'une nouvelle partie du serveur web" : "Entrée : rejouer   -   Échap : quitter";
		FONTS.SMALL.drawShadowed(hint, MAP_WIDTH / 2, MAP_HEIGHT - 40, Color.WHITE.withAlpha(a * (0.7f + 0.3f * (float)Math.sin(time * 3))), Align.CENTER);
	}

	/**
	 * Construit le complément « de pseudo », avec élision devant une voyelle
	 * @param name Pseudo
	 * @return « d'Inès » ou « de Hugo »
	 */
	static String of(String name) {
		if (name.isEmpty()) return "de " + name;
		String first = Normalizer.normalize(new String(Character.toChars(name.codePointAt(0))), Normalizer.Form.NFD);
		char c = Character.toLowerCase(first.charAt(0));
		return ("aeiouy".indexOf(c) >= 0 ? "d'" : "de ") + name;
	}

	/**
	 * Ligne du classement
	 * @param p Joueur
	 * @param index Position dans la liste
	 * @param x1 Gauche du tableau
	 * @param x2 Droite du tableau
	 * @param y Ordonnée de la ligne
	 * @param a Opacité
	 * @param local true pour le joueur local
	 */
	private void rankingRow(PlayerState p, int index, float x1, float x2, float y, float a, boolean local) {
		if (local) fillRect(x1 + 6, y - 3, x2 - 6, y + 24, Color.GOLD.withAlpha(0.16f * a));
		int rank = p.rank() > 0 ? p.rank() : 1;
		Color c = rank == 1 ? Color.GOLD : rank <= 3 ? Color.WHITE : Color.LIGHT_GREY;
		FONTS.SMALL.draw(Effects.rankText(rank), x1 + 20, y + 1, c.withAlpha(a), Align.LEFT);
		texture(TEXTURES.getPlayerTexture(p.id(), !p.alive()), x1 + 76, y - 1, x1 + 100, y + 23, Color.WHITE.withAlpha(a));
		FONTS.SMALL.draw(p.pseudo(), x1 + 110, y + 1, (local ? Color.GOLD : Color.WHITE).withAlpha(a), Align.LEFT);
		FONTS.SMALL.draw(Integer.toString(p.kills()), x2 - 20, y + 1, Color.WHITE.withAlpha(a), Align.RIGHT);
	}

	/**
	 * Salle d'attente du mode multijoueur
	 * @param info Informations de la vue
	 * @param time Instant courant
	 */
	public void renderLobby(HudInfo info, double time) {
		tiled(TEXTURES.getGround(), 0, 0, MAP_WIDTH, MAP_HEIGHT, 150, (float)(time * 0.01), 0, new Color(0.45f, 0.5f, 0.45f, 1));
		gradientRect(0, 0, MAP_WIDTH, MAP_HEIGHT, new Color(0.02f, 0.03f, 0.08f, 0.55f), new Color(0.02f, 0.03f, 0.08f, 0.9f));

		// Emojis qui défilent
		int n = TEXTURES.getPlayersTexturesNumber();
		for (int i = 0; i < 14; i++) {
			float x = (float)((i * 97 + time * 30) % (MAP_WIDTH + 80)) - 40;
			float y = 120 + (float)Math.sin(time * 1.3 + i) * 10;
			texture(TEXTURES.getPlayerTexture(i * 7 % n, false), x - 18, y - 18, x + 18, y + 18, Color.WHITE.withAlpha(0.5f));
		}

		// Emblème du jeu au-dessus du titre
		Texture emblem = TEXTURES.getEmblem();
		if (emblem != null) {
			float bob = (float)Math.sin(time * 1.8) * 5;
			glow(TEXTURES.getGlow(), MAP_WIDTH / 2, 118 + bob, 120, 120, Color.CYAN.withAlpha(0.22f));
			texture(SubTexture.whole(emblem), MAP_WIDTH / 2 - 72, 46 + bob, MAP_WIDTH / 2 + 72, 190 + bob, Color.WHITE);
		}

		FONTS.TITLE.drawShadowed("BATTLE ROYALE", MAP_WIDTH / 2, 218, Color.GOLD, Align.CENTER);
		float pulse = 0.65f + 0.35f * (float)Math.sin(time * 3);
		FONTS.LARGE.drawShadowed("En attente du serveur web", MAP_WIDTH / 2, 300, Color.WHITE.withAlpha(pulse), Align.CENTER);

		panel(MAP_WIDTH / 2 - 380, 370, MAP_WIDTH / 2 + 380, 520, 12, new Color(0.08f, 0.1f, 0.15f, 0.85f));
		FONTS.MEDIUM.draw("Rejoignez la partie depuis votre téléphone :", MAP_WIDTH / 2, 390, Color.LIGHT_GREY, Align.CENTER);
		FONTS.MEDIUM.drawShadowed(info.gamepadUrl(), MAP_WIDTH / 2, 430, Color.CYAN, Align.CENTER);
		FONTS.SMALL.draw("L'administrateur lance la partie depuis le panneau d'administration", MAP_WIDTH / 2, 478, Color.LIGHT_GREY, Align.CENTER);

		FONTS.SMALL.draw("Port du jeu : " + info.port(), MAP_WIDTH / 2, MAP_HEIGHT - 70, Color.LIGHT_GREY, Align.CENTER);
		if (info.status() != null)
			FONTS.SMALL.draw(info.status(), MAP_WIDTH / 2, MAP_HEIGHT - 45, info.statusError() ? Color.RED : Color.LIGHT_GREY, Align.CENTER);
	}

	/**
	 * Message de connexion récent, en bas à droite (en partie)
	 * @param info Informations de la vue
	 * @param time Instant courant
	 */
	private void renderStatusToast(HudInfo info, double time) {
		if (!info.multi() || info.status() == null || time - info.statusTime() > 5) return;
		float a = (float)Math.min(1, 5 - (time - info.statusTime()));
		float w = FONTS.SMALL.width(info.status()) + 24;
		panel(MAP_WIDTH - 12 - w, MAP_HEIGHT - 44, MAP_WIDTH - 12, MAP_HEIGHT - 12, 6, new Color(0.05f, 0.07f, 0.1f, 0.75f * a));
		FONTS.SMALL.draw(info.status(), MAP_WIDTH - 24, MAP_HEIGHT - 38, (info.statusError() ? Color.RED : Color.WHITE).withAlpha(a), Align.RIGHT);
	}
}
