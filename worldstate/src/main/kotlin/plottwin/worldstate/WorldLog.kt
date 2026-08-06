package plottwin.worldstate

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.serialization.json.Json

class GeometryWriteRejected(row: WorldRow, writer: WriterRole) :
    IllegalArgumentException("geometry rows are optimizer-only; $writer tried to append ${row::class.simpleName}")

data class LoggedRow(
    val seq: Long,
    val writer: WriterRole,
    val row: WorldRow,
)

class WorldLog private constructor(private val connection: Connection) : AutoCloseable {

    private val rowCodec = Json
    private val ruleTriggers = mutableListOf<(RuleRow) -> Unit>()
    private val opTriggers = mutableListOf<(OpRow) -> Unit>()

    companion object {
        fun open(dbPath: Path): WorldLog = openConnection("jdbc:sqlite:$dbPath")

        fun openInMemory(): WorldLog = openConnection("jdbc:sqlite::memory:")

        private fun openConnection(jdbcUrl: String): WorldLog {
            val log = WorldLog(DriverManager.getConnection(jdbcUrl))
            log.createLogTable()
            return log
        }
    }

    fun append(row: WorldRow, writer: WriterRole): Long {
        requireGeometryWriter(row, writer)
        val seq = insertRow(row, writer)
        notifyRuleTriggers(row)
        notifyOpTriggers(row)
        return seq
    }

    fun readAll(): List<LoggedRow> {
        connection.createStatement().use { statement ->
            val logRows = statement.executeQuery("SELECT seq, writer_role, payload FROM world_log ORDER BY seq")
            return buildList {
                while (logRows.next()) {
                    add(
                        LoggedRow(
                            seq = logRows.getLong("seq"),
                            writer = WriterRole.valueOf(logRows.getString("writer_role")),
                            row = rowCodec.decodeFromString(WorldRow.serializer(), logRows.getString("payload")),
                        )
                    )
                }
            }
        }
    }

    fun currentState(): CurrentState = projectCurrentState(readAll())

    fun onRuleAppended(trigger: (RuleRow) -> Unit) {
        ruleTriggers.add(trigger)
    }

    fun onOpAppended(trigger: (OpRow) -> Unit) {
        opTriggers.add(trigger)
    }

    override fun close() {
        connection.close()
    }

    private fun createLogTable() {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS world_log (
                    seq INTEGER PRIMARY KEY AUTOINCREMENT,
                    writer_role TEXT NOT NULL,
                    payload TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private fun requireGeometryWriter(row: WorldRow, writer: WriterRole) {
        if (carriesGeometry(row) && writer != WriterRole.OPTIMIZER) throw GeometryWriteRejected(row, writer)
    }

    private fun insertRow(row: WorldRow, writer: WriterRole): Long {
        connection.prepareStatement("INSERT INTO world_log (writer_role, payload) VALUES (?, ?)").use { insert ->
            insert.setString(1, writer.name)
            insert.setString(2, rowCodec.encodeToString(WorldRow.serializer(), row))
            insert.executeUpdate()
        }
        return lastInsertedSeq()
    }

    private fun lastInsertedSeq(): Long {
        connection.createStatement().use { statement ->
            val lastRowId = statement.executeQuery("SELECT last_insert_rowid()")
            lastRowId.next()
            return lastRowId.getLong(1)
        }
    }

    private fun notifyRuleTriggers(row: WorldRow) {
        if (row !is RuleRow) return
        ruleTriggers.forEach { trigger -> trigger(row) }
    }

    private fun notifyOpTriggers(row: WorldRow) {
        if (row !is OpRow) return
        opTriggers.forEach { trigger -> trigger(row) }
    }
}
