package inside;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import inside.geometry.Rectangle;
import inside.geometry.Vertice;

class GeometryTest implements IConfig {
	private static final float EPS = 1e-4f;

	@Test
	void rotationDUnQuartDeTourUtiliseLesCoordonneesDOrigine() {
		Vertice v = new Vertice(1, 0);
		v.rotate((float)(Math.PI / 2));
		assertEquals(0, v.getX(), EPS);
		assertEquals(1, v.getY(), EPS);

		Vertice w = new Vertice(3, 4);
		w.rotate((float)Math.PI);
		assertEquals(-3, w.getX(), EPS);
		assertEquals(-4, w.getY(), EPS);
		assertEquals(5, w.length(), EPS);
	}

	@Test
	void rectanglesIntersectionInclusionInterpolation() {
		Rectangle a = new Rectangle(0, 0, 100, 50);
		Rectangle b = new Rectangle(90, 40, 120, 60);
		Rectangle c = new Rectangle(100, 0, 120, 50);

		assertTrue(a.intersect(b));
		assertFalse(a.intersect(c), "des rectangles qui se touchent ne se chevauchent pas");
		assertTrue(a.contain(new Rectangle(10, 10, 20, 20)));
		assertFalse(a.contain(b));
		assertTrue(a.contains(100, 50));

		Rectangle half = a.lerp(new Rectangle(50, 0, 100, 50), 0.5f);
		assertEquals(new Rectangle(25, 0, 100, 50), half);
		assertEquals(Rectangle.centered(10, 10, 5, 2), new Rectangle(5, 8, 15, 12));
	}

	@Test
	void directionsNormaliseesDansLeSensTrigonometriqueAvecYVersLeBas() {
		for (int d = 0; d < DIRECTIONS_NUMBER; d++) {
			float dx = Direction.dx(d), dy = Direction.dy(d);
			assertEquals(1, Math.sqrt(dx * dx + dy * dy), EPS, "direction " + d + " non normalisée");
			assertEquals(d, Direction.fromVector(dx, dy));
		}
		assertEquals(1, Direction.dx(EAST), EPS);
		assertEquals(-1, Direction.dy(NORTH), EPS);
		assertEquals(1, Direction.dy(SOUTH), EPS);
		assertEquals(NORTH_EAST, Direction.fromVector(1, -1));
		assertEquals(SOUTH_WEST, Direction.fromVector(-1, 1));
		assertEquals(-1, Direction.fromVector(0, 0));
		assertEquals(WEST, Direction.opposite(EAST));
		assertFalse(Direction.isValid(8));
		assertFalse(Direction.isValid(-1));
	}

	@Test
	void grilleBorneeAuxBordsDeLaCarte() {
		assertEquals(0, GameMap.columnOf(-5));
		assertEquals(AREAS_WIDTH - 1, GameMap.columnOf(MAP_WIDTH));
		assertEquals(AREAS_WIDTH - 1, GameMap.columnOf(MAP_WIDTH + 100));
		assertEquals(0, GameMap.rowOf(-0.1f));
		assertEquals(AREAS_HEIGHT - 1, GameMap.rowOf(MAP_HEIGHT));

		GameMap map = new GameMap(java.util.List.of());
		assertNotNull(map.getAreaAt(MAP_WIDTH, MAP_HEIGHT));
		assertNotNull(map.getAreaAt(0, 0));
		assertFalse(map.areasOverlapping(new Rectangle(-50, -50, MAP_WIDTH + 50, MAP_HEIGHT + 50)).isEmpty());
	}
}
