package plottwin.worldstate

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class GeometryWriteRejected(row: WorldRow, writer: WriterRole, requiredWriter: WriterRole) :
    IllegalArgumentException("${row::class.simpleName} is $requiredWriter-only; $writer tried to append it")

data class LoggedRow(
    val seq: Long,
    val writer: WriterRole,
    val row: WorldRow,
    val refs: List<RowRef> = emptyList(),
)

class WorldLog private constructor(private val connection: Connection) : AutoCloseable {

    private val rowCodec = Json
    private val refsCodec = ListSerializer(RowRef.serializer())
    private val ruleTriggers = mutableListOf<(RuleRow) -> Unit>()
    private val opTriggers = mutableListOf<(Long, OpRow) -> Unit>()

    companion object {
        fun open(dbPath: Path): WorldLog = openConnection("jdbc:sqlite:$dbPath")

        fun openInMemory(): WorldLog = openConnection("jdbc:sqlite::memory:")

        private fun openConnection(jdbcUrl: String): WorldLog {
            val log = WorldLog(DriverManager.getConnection(jdbcUrl))
            log.createLogTable()
            return log
        }
    }

    fun append(row: WorldRow, writer: WriterRole, refs: List<RowRef> = emptyList()): Long {
        requireGeometryWriter(row, writer)
        val seq = insertRow(row, writer, refs)
        notifyRuleTriggers(row)
        notifyOpTriggers(seq, row)
        return seq
    }

    fun readAll(): List<LoggedRow> {
        connection.createStatement().use { statement ->
            val logRows = statement.executeQuery("SELECT seq, writer_role, payload, refs FROM world_log ORDER BY seq")
            return buildList {
                while (logRows.next()) {
                    add(
                        LoggedRow(
                            seq = logRows.getLong("seq"),
                            writer = WriterRole.valueOf(logRows.getString("writer_role")),
                            row = rowCodec.decodeFromString(WorldRow.serializer(), logRows.getString("payload")),
                            refs = rowCodec.decodeFromString(refsCodec, logRows.getString("refs")),
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

    fun onOpAppended(trigger: (Long, OpRow) -> Unit) {
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
                    payload TEXT NOT NULL,
                    refs TEXT NOT NULL DEFAULT '[]'
                )
                """.trimIndent()
            )
        }
    }

    private fun requireGeometryWriter(row: WorldRow, writer: WriterRole) {
        val requiredWriter = geometryWriterFor(row) ?: return
        if (writer != requiredWriter) throw GeometryWriteRejected(row, writer, requiredWriter)
    }

    private fun insertRow(row: WorldRow, writer: WriterRole, refs: List<RowRef>): Long {
        connection.prepareStatement("INSERT INTO world_log (writer_role, payload, refs) VALUES (?, ?, ?)").use { insert ->
            insert.setString(1, writer.name)
            insert.setString(2, rowCodec.encodeToString(WorldRow.serializer(), row))
            insert.setString(3, rowCodec.encodeToString(refsCodec, refs))
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

    private fun notifyOpTriggers(seq: Long, row: WorldRow) {
        if (row !is OpRow) return
        opTriggers.forEach { trigger -> trigger(seq, row) }
    }
}
