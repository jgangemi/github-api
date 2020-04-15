package org.kohsuke.github;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.TimeZone;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.core.Is.is;

/**
 * Unit test for {@link GitHub} static helpers.
 *
 * @author Liam Newman
 */
public class GitHubStaticTest extends AbstractGitHubWireMockTest {

    @Test
    public void timeRoundTrip() throws Exception {
        Instant instantNow = Instant.now();

        Date instantSeconds = Date.from(instantNow.truncatedTo(ChronoUnit.SECONDS));
        Date instantMillis = Date.from(instantNow.truncatedTo(ChronoUnit.MILLIS));

        // if we happen to land exactly on zero milliseconds, add 1 milli
        if (instantSeconds.equals(instantMillis)) {
            instantMillis = Date.from(instantNow.plusMillis(1).truncatedTo(ChronoUnit.MILLIS));
        }

        // TODO: other formats
        String instantFormatSlash = formatDate(instantMillis, "yyyy/MM/dd HH:mm:ss ZZZZ");
        String instantFormatDash = formatDate(instantMillis, "yyyy-MM-dd'T'HH:mm:ss'Z'");
        String instantFormatMillis = formatDate(instantMillis, "yyyy-MM-dd'T'HH:mm:ss.S'Z'");
        String instantSecondsFormatMillis = formatDate(instantSeconds, "yyyy-MM-dd'T'HH:mm:ss.S'Z'");
        String instantBadFormat = formatDate(instantMillis, "yy-MM-dd'T'HH:mm'Z'");

        assertThat(GitHubClient.parseDate(GitHubClient.printDate(instantSeconds)),
                equalTo(GitHubClient.parseDate(GitHubClient.printDate(instantMillis))));

        assertThat(instantSeconds, equalTo(GitHubClient.parseDate(GitHubClient.printDate(instantSeconds))));

        // printDate will truncate to the nearest second, so it should not be equal
        assertThat(instantMillis, not(equalTo(GitHubClient.parseDate(GitHubClient.printDate(instantMillis)))));

        assertThat(instantSeconds, equalTo(GitHubClient.parseDate(instantFormatSlash)));

        assertThat(instantSeconds, equalTo(GitHubClient.parseDate(instantFormatDash)));

        // This parser does not truncate to the nearest second, so it will be equal
        assertThat(instantMillis, equalTo(GitHubClient.parseDate(instantFormatMillis)));

        assertThat(instantSeconds, equalTo(GitHubClient.parseDate(instantSecondsFormatMillis)));

        try {
            GitHubClient.parseDate(instantBadFormat);
            fail("Bad time format should throw.");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo("Unable to parse the timestamp: " + instantBadFormat));
        }
    }

    @Test
    public void testGitHubRateLimitShouldReplaceRateLimit() throws Exception {

        GHRateLimit.Record unknown0 = GHRateLimit.Unknown().getCore();
        GHRateLimit.Record unknown1 = GHRateLimit.Unknown().getCore();

        GHRateLimit.Record record0 = new GHRateLimit.Record(10, 10, 10L);
        GHRateLimit.Record record1 = new GHRateLimit.Record(10, 9, 10L);
        GHRateLimit.Record record2 = new GHRateLimit.Record(10, 2, 10L);
        GHRateLimit.Record record3 = new GHRateLimit.Record(10, 10, 20L);
        GHRateLimit.Record record4 = new GHRateLimit.Record(10, 5, 20L);

        Thread.sleep(2000);

        GHRateLimit.Record recordWorst = new GHRateLimit.Record(Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE);
        GHRateLimit.Record record00 = new GHRateLimit.Record(10, 10, 10L);
        GHRateLimit.Record unknown2 = GHRateLimit.Unknown().getCore();

        // Rate-limit records maybe created and returned in different orders.
        // We should update to the regular records over unknowns.
        // After that, we should update to the candidate if its limit is lower or its reset is later.

        assertThat("Equivalent unknown should not replace", GitHubClient.shouldReplace(unknown0, unknown1), is(false));
        assertThat("Equivalent unknown should not replace", GitHubClient.shouldReplace(unknown1, unknown0), is(false));

        assertThat("Later unknown should replace earlier", GitHubClient.shouldReplace(unknown2, unknown0), is(true));
        assertThat("Earlier unknown should not replace later",
                GitHubClient.shouldReplace(unknown0, unknown2),
                is(false));

        assertThat("Worst record should replace later unknown",
                GitHubClient.shouldReplace(recordWorst, unknown1),
                is(true));
        assertThat("Unknown should not replace worst record",
                GitHubClient.shouldReplace(unknown1, recordWorst),
                is(false));

        assertThat("Earlier record should replace later worst",
                GitHubClient.shouldReplace(record0, recordWorst),
                is(true));
        assertThat("Later worst record should not replace earlier",
                GitHubClient.shouldReplace(recordWorst, record0),
                is(false));

        assertThat("Equivalent record should not replace", GitHubClient.shouldReplace(record0, record00), is(false));
        assertThat("Equivalent record should not replace", GitHubClient.shouldReplace(record00, record0), is(false));

        assertThat("Lower limit record should replace higher", GitHubClient.shouldReplace(record1, record0), is(true));
        assertThat("Lower limit record should replace higher", GitHubClient.shouldReplace(record2, record1), is(true));

        assertThat("Higher limit record should not replace lower",
                GitHubClient.shouldReplace(record1, record2),
                is(false));

        assertThat("Higher limit record with later reset should  replace lower",
                GitHubClient.shouldReplace(record3, record2),
                is(true));

        assertThat("Lower limit record with later reset should replace higher",
                GitHubClient.shouldReplace(record4, record1),
                is(true));

        assertThat("Lower limit record with earlier reset should not replace higher",
                GitHubClient.shouldReplace(record2, record4),
                is(false));

    }

    @Test
    public void testMappingReaderWriter() throws Exception {

        // This test ensures that data objects can be written and read in a raw form from string.
        // This behavior is completely unsupported and should not be used but given that some
        // clients, such as Jenkins Blue Ocean, have already implemented their own Jackson
        // Reader and Writer that bind this library's data objects from outside this library
        // this makes sure they don't break.

        GHRepository repo = getTempRepository();
        assertThat(repo.root, not(nullValue()));

        String repoString = GitHub.getMappingObjectWriter().writeValueAsString(repo);
        assertThat(repoString, not(nullValue()));
        assertThat(repoString, containsString("testMappingReaderWriter"));

        GHRepository readRepo = GitHub.getMappingObjectReader().forType(GHRepository.class).readValue(repoString);

        // This should never happen if these methods aren't used
        assertThat(readRepo.root, nullValue());

        String readRepoString = GitHub.getMappingObjectWriter().writeValueAsString(readRepo);
        assertThat(readRepoString, equalTo(repoString));

    }

    static String formatDate(Date dt, String format) {
        SimpleDateFormat df = new SimpleDateFormat(format);
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        return df.format(dt);
    }

}
