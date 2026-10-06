package outside;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import inside.Board;
import inside.Command;
import inside.IConfig;
import outside.communication.NetworkUtilities;
import outside.graphic.Viewport;

class LaunchOptionsTest implements IConfig {

	@Test
	void valeursParDefaut() {
		LaunchOptions solo = LaunchOptions.parse();
		assertFalse(solo.multi());
		assertEquals(LaunchOptions.DEFAULT_SOLO_BOTS, solo.bots());
		assertEquals(8000, solo.port());
		assertEquals("127.0.0.1", solo.bind());
		assertTrue(solo.sound());

		LaunchOptions multi = LaunchOptions.parse("--mode", "multi");
		assertTrue(multi.multi());
		assertEquals(0, multi.bots());
	}

	@Test
	void toutesLesOptions() {
		LaunchOptions o = LaunchOptions.parse("--mode", "multi", "--bots", "5", "--port", "38042", "--warmup", "2.5",
			"--seed", "77", "--no-sound", "--window", "1600x900");
		assertTrue(o.multi());
		assertEquals(5, o.bots());
		assertEquals(38042, o.port());
		assertEquals("0.0.0.0", LaunchOptions.parse("--mode", "multi", "--bind", "0.0.0.0").bind());
		assertTrue(LaunchOptions.USAGE.contains("--bind ADRESSE"));
		assertEquals(2.5f, o.warmupSeconds());
		assertEquals(77, o.seed());
		assertFalse(o.sound());
		assertEquals(1600, o.windowWidth());
		assertEquals(900, o.windowHeight());
		assertEquals(150, o.settings().getWarmupTicks());
		assertEquals(77, o.settings().getSeed());

		LaunchOptions s = LaunchOptions.parse("--spectate", "--bots", "3", "--pseudo", "  Zoé ");
		assertTrue(s.spectate());
		assertEquals("Zoé", s.pseudo());
	}

	@Test
	void optionsInvalides() {
		for (String[] args : List.of(
				new String[] { "--mode", "duo" },
				new String[] { "--bots", "-1" },
				new String[] { "--bots", "100" },
				new String[] { "--port", "0" },
				new String[] { "--port", "abc" },
				new String[] { "--bind" },
				new String[] { "--bind", " " },
				new String[] { "--bind", "1.2.3.4 5" },
				new String[] { "--warmup", "-3" },
				new String[] { "--seed" },
				new String[] { "--window", "10x10" },
				new String[] { "--window", "1280x720x9" },
				new String[] { "--window", "100000x100000" },
				new String[] { "--pseudo", "un pseudo beaucoup trop long" },
				new String[] { "--pseudo", "émoji\uD83D\uDE00" },
				new String[] { "--spectate", "--bots", "1" },
				new String[] { "--mode", "multi", "--spectate" },
				new String[] { "--inconnue" }))
			assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse(args), String.join(" ", args));
	}

	@Test
	void modeConsoleSansArgument() {
		ByteArrayOutputStream sink = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(sink, true, StandardCharsets.UTF_8);
		assertTrue(LaunchOptions.ask(new BufferedReader(new StringReader("n\n")), out).multi());
		LaunchOptions solo = LaunchOptions.ask(new BufferedReader(new StringReader("o\n4\n")), out);
		assertFalse(solo.multi());
		assertEquals(4, solo.bots());
		assertEquals(LaunchOptions.DEFAULT_SOLO_BOTS, LaunchOptions.ask(new BufferedReader(new StringReader("o\nabc\n")), out).bots());
		assertEquals(LaunchOptions.DEFAULT_SOLO_BOTS, LaunchOptions.ask(new BufferedReader(new StringReader("")), out).bots());
	}

	@Test
	void directionsDuClavier() {
		assertEquals(-1, KeyboardInput.direction(false, false, false, false));
		assertEquals(-1, KeyboardInput.direction(true, true, true, true), "touches opposées annulées");
		assertEquals(EAST, KeyboardInput.direction(false, false, false, true));
		assertEquals(NORTH, KeyboardInput.direction(true, false, false, false));
		assertEquals(NORTH_WEST, KeyboardInput.direction(true, false, true, false));
		assertEquals(SOUTH_EAST, KeyboardInput.direction(false, true, false, true));
		assertEquals(SOUTH, KeyboardInput.direction(false, true, true, true));
	}

	@Test
	void clavierEnvoieUnArretAuRelachement() {
		List<Command> sent = new ArrayList<>();
		Board board = new Board(inside.GameSettings.defaults(1), List.of(new Board.PlayerSpec(0, "A", false))) {
			@Override
			public void enqueue(Command c) {
				sent.add(c);
			}
		};
		KeyboardInput input = new KeyboardInput();
		input.apply(board, 0, new KeyboardInput.Keys(false, false, false, true, false, false, false));
		input.apply(board, 0, new KeyboardInput.Keys(false, false, false, false, false, true, false));
		input.apply(board, 0, new KeyboardInput.Keys(false, false, false, false, false, false, false));
		input.apply(board, 0, new KeyboardInput.Keys(true, false, false, false, true, false, true));

		assertEquals(List.of(
			new Command.Move(0, EAST, KeyboardInput.RUN_SPEED),
			new Command.Move(0, 0, 0),
			new Command.Attack(0, ATTACK_MELEE),
			new Command.Move(0, NORTH, KeyboardInput.WALK_SPEED),
			new Command.Attack(0, ATTACK_SHOOT)), sent);
	}

	@Test
	void affichageAuFormat16Sur9() {
		assertEquals(new Viewport(0, 0, 1280, 720), Viewport.letterbox(1280, 720));
		assertEquals(new Viewport(0, 60, 1280, 720), Viewport.letterbox(1280, 840), "bandes horizontales");
		assertEquals(new Viewport(160, 0, 1600, 900), Viewport.letterbox(1920, 900), "bandes verticales");
		assertEquals(2f, Viewport.letterbox(2560, 1440).scale());
		assertEquals(0, Viewport.letterbox(0, 0).width(), "fenêtre réduite");
	}

	@Test
	void adresseDeLaManette() {
		assertEquals("http://192.168.1.20:8080/battle-royale-server/gamepad/", NetworkUtilities.gamepadUrl("192.168.1.20"));
		String ip = NetworkUtilities.lanIPv4();
		assertTrue(ip.equals("localhost") || ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"), ip);
		assertFalse(ip.startsWith("127."));
	}

	@Test
	void adresseDeLaManetteImposee() {
		String url = "https://jeu.example.org/battle-royale-server/gamepad/";
		LaunchOptions o = LaunchOptions.parse("--mode", "multi", "--gamepad-url", url);
		assertEquals(url, o.gamepadUrl());
		assertEquals(url, o.effectiveGamepadUrl());

		LaunchOptions d = LaunchOptions.parse("--mode", "multi");
		assertNull(d.gamepadUrl());
		assertEquals(NetworkUtilities.gamepadUrl(NetworkUtilities.lanIPv4()), d.effectiveGamepadUrl(), "adresse déduite par défaut");
		assertTrue(LaunchOptions.USAGE.contains("--gamepad-url URL"));

		for (String bad : new String[] { "", "ftp://x/", "http://", "jeu.example.org", "http://a b/" })
			assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--gamepad-url", bad), bad);
		assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--gamepad-url"));
	}
}
