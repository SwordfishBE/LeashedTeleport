package net.leashedteleport.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModrinthUpdateCheckerTest {
    @Test void incompatibleReleaseIsNotAdvertised() {
        assertTrue(ModrinthUpdateChecker.extractLatestVersion("""
                [{"version_type":"release","version_number":"9.0","date_published":"2026-10-19T00:00:00Z",
                  "loaders":["fabric"],"game_versions":["26.4"]}]
                """, "26.3").isEmpty());
    }

    @Test void compatibleReleaseWinsOverNewerWrongLoader() {
        assertEquals("1.1.10", ModrinthUpdateChecker.extractLatestVersion("""
                [{"version_type":"release","version_number":"1.1.10","date_published":"2026-10-18T00:00:00Z",
                  "loaders":["fabric"],"game_versions":["26.3"]},
                 {"version_type":"release","version_number":"9.0","date_published":"2026-10-19T00:00:00Z",
                  "loaders":["neoforge"],"game_versions":["26.3"]}]
                """, "26.3").orElseThrow());
    }

    @Test void malformedFieldsAreSkipped() {
        assertTrue(ModrinthUpdateChecker.extractLatestVersion("""
                [{"version_type":{},"version_number":[],"date_published":"not a date"}]
                """, "26.3").isEmpty());
    }
}
