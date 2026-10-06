package communication;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OriginCheckTest {
	@Test
	void acceptsAMissingOrigin() {
		assertTrue(OriginCheck.allows(null, "localhost:8000", "http"));
		assertTrue(OriginCheck.allows(null, null, "http"));
	}

	@Test
	void acceptsTheHostAndPortOfTheRequest() {
		assertTrue(OriginCheck.allows("http://localhost:38232", "localhost:38232", "http"));
		assertTrue(OriginCheck.allows("http://LocalHost:38232", "localhost:38232", "http"));
		assertTrue(OriginCheck.allows("http://192.168.1.12:38232", "192.168.1.12:38232", "http"));
		assertTrue(OriginCheck.allows("http://[::1]:38232", "[::1]:38232", "http"));
		assertTrue(OriginCheck.allows("http://example.org", "example.org", "http"));
		assertTrue(OriginCheck.allows("http://example.org:80", "example.org", "http"));
		assertTrue(OriginCheck.allows("https://example.org", "example.org", "https"));
	}

	@Test
	void refusesAnyOtherOrigin() {
		assertFalse(OriginCheck.allows("http://evil.example:38232", "localhost:38232", "http"));
		assertFalse(OriginCheck.allows("http://localhost:38233", "localhost:38232", "http"));
		assertFalse(OriginCheck.allows("http://localhost", "localhost:38232", "http"));
		assertFalse(OriginCheck.allows("https://example.org", "example.org", "http"));
		assertFalse(OriginCheck.allows("null", "localhost:38232", "http"));
		assertFalse(OriginCheck.allows("file:///tmp/index.html", "localhost:38232", "http"));
		assertFalse(OriginCheck.allows("http://user@localhost:38232", "localhost:38232", "http"));
		assertFalse(OriginCheck.allows("pas une origine", "localhost:38232", "http"));
		assertFalse(OriginCheck.allows("http://localhost:38232", null, "http"));
		assertFalse(OriginCheck.allows("http://localhost:38232", "", "http"));
		assertFalse(OriginCheck.allows("http://localhost:38232", "localhost:abc", "http"));
	}

	@Test
	void refusesAnOriginWhenTheRequestIsUnknown() {
		assertTrue(OriginCheck.allowsCurrent(null));
		assertFalse(OriginCheck.allowsCurrent("http://localhost:38232"));
	}
}
