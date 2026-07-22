package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import java.math.BigDecimal
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcOperations

/**
 * Regression coverage for changesets 116 (rewrote the historia triggers to parse ISO-8601
 * startTime/endTime instead of epoch-second numbers) and 117 (backfills any row still holding the
 * legacy epoch format). Simulates a row left in the legacy epoch format - as changeset 117's
 * backfill would have found before it ran - and proves both that the backfill conversion is correct
 * and that the changeset-116 trigger accepts the result afterwards.
 */
class HistoriaTriggerITest(
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val jdbcOperations: JdbcOperations,
) : IntegrationTest() {

    private val triggers =
        listOf(
            "after_johtoselvitys_changes",
            "after_kaivuilmoitus_changes",
            "after_kaivuilmoitusalueet_changes",
        )

    @Test
    fun `legacy epoch-format dates are backfilled to ISO-8601 and the historia trigger accepts the result`() {
        val hakemus = hakemusFactory.builder(ApplicationType.CABLE_REPORT).saveEntity()
        val epochStart = 1_700_000_000L
        val epochEnd = 1_700_003_600L

        // Simulate data written before the Jackson 2->3 migration: applicationdata's
        // startTime/endTime as raw epoch-second numbers. Triggers must be disabled first, exactly
        // like changeset 117 does, since writing epoch-format data would otherwise make the
        // changeset-116 trigger fail trying to cast it to timestamptz.
        triggers.forEach { jdbcOperations.execute("ALTER TABLE applications DISABLE TRIGGER $it") }
        jdbcOperations.update(
            """
            UPDATE applications
            SET applicationdata = applicationdata
                || jsonb_build_object('startTime', $epochStart, 'endTime', $epochEnd)
            WHERE id = ?
            """
                .trimIndent(),
            hakemus.id,
        )

        // Reproduce changeset 117's exact backfill expression against just this row.
        jdbcOperations.update(
            """
            UPDATE applications
            SET applicationdata = applicationdata || jsonb_build_object(
                'startTime', CASE WHEN applicationdata ->> 'startTime' ~ '^\d+(\.\d+)?${'$'}'
                    THEN to_jsonb(to_timestamp((applicationdata ->> 'startTime')::decimal))
                    ELSE applicationdata -> 'startTime' END,
                'endTime', CASE WHEN applicationdata ->> 'endTime' ~ '^\d+(\.\d+)?${'$'}'
                    THEN to_jsonb(to_timestamp((applicationdata ->> 'endTime')::decimal))
                    ELSE applicationdata -> 'endTime' END
            )
            WHERE id = ?
            """
                .trimIndent(),
            hakemus.id,
        )
        triggers.forEach { jdbcOperations.execute("ALTER TABLE applications ENABLE TRIGGER $it") }

        // The backfilled value must no longer look like an epoch number, and must cast cleanly to
        // the originally-intended instant.
        val stillLooksLikeEpoch =
            jdbcOperations.queryForObject(
                "SELECT (applicationdata ->> 'startTime') ~ '^\\d+(\\.\\d+)?\$' FROM applications " +
                    "WHERE id = ?",
                Boolean::class.java,
                hakemus.id,
            )
        assertThat(stillLooksLikeEpoch!!).isFalse()
        val backfilledEpoch =
            jdbcOperations.queryForObject(
                "SELECT extract(epoch from (applicationdata ->> 'startTime')::timestamptz) " +
                    "FROM applications WHERE id = ?",
                BigDecimal::class.java,
                hakemus.id,
            )
        assertThat(backfilledEpoch!!.toLong()).isEqualTo(epochStart)

        // Fire the trigger for real against the now-backfilled row - exercising changeset 116's
        // `(NEW.applicationdata ->> 'startTime')::timestamptz` cast on exactly the kind of data a
        // genuinely-backfilled legacy row would have.
        jdbcOperations.update(
            "UPDATE applications SET applicationidentifier = ? WHERE id = ?",
            "CHANGED-IDENTIFIER",
            hakemus.id,
        )

        // arvioitu_alkupaiva is a TIMESTAMP (no time zone) column, so the timestamptz -> timestamp
        // assignment cast below shifts the value by the DB session's time zone. Compare against a
        // value produced by the exact same cast rather than assuming UTC storage.
        val matchesExpectedInstant =
            jdbcOperations.queryForObject(
                """
                SELECT arvioitu_alkupaiva = to_timestamp($epochStart)::timestamp
                FROM johtoselvitys_historia
                WHERE hakemuksen_id = ? AND dml_type = 'UPDATE'
                """
                    .trimIndent(),
                Boolean::class.java,
                hakemus.id,
            )
        assertThat(matchesExpectedInstant!!).isTrue()
    }
}
