package plottwin.worldstate

private val COORDINATE_PAIR = Regex("""[-+]?\d+(\.\d+)?\s*,\s*[-+]?\d+(\.\d+)?""")

class CoordinateInOpRejected(slot: OpSlot, slotText: String) :
    IllegalArgumentException("op slot $slot carries a coordinate pair (\"$slotText\"); ops speak names and prose, never geometry")

fun requireCoordinateFreeSlots(op: OpRow) {
    op.slots.forEach { (slot, slotText) ->
        if (COORDINATE_PAIR.containsMatchIn(slotText)) throw CoordinateInOpRejected(slot, slotText)
    }
}
