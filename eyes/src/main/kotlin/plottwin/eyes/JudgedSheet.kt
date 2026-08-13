package plottwin.eyes

import java.io.File
import java.security.MessageDigest
import plottwin.capture.repoRoot

// A receipt a later run can overwrite is not a receipt. Run 6's visual acceptance is unrecoverable
// because every sheet an eye scored went to one build path, and for two days the single tracked
// image showed a parcel 15.6 km from the one the verdict above it judged. So: a sheet a human is
// about to score lands under its own run and cycle, and that address refuses a second write.
// A run that names no cycle is not judging anything, and its sheets stay in build/ as throwaway.
const val RECEIPT_RUN_PROPERTY = "plottwin.receipt.run"
const val RECEIPT_CYCLE_PROPERTY = "plottwin.receipt.cycle"

fun judgedSheetFile(name: String): File {
    val run = System.getProperty(RECEIPT_RUN_PROPERTY) ?: return File(System.getProperty("user.dir"), "build/$name.png")
    val cycle = requireNotNull(System.getProperty(RECEIPT_CYCLE_PROPERTY)) {
        "-D$RECEIPT_RUN_PROPERTY=$run names a run but no -D$RECEIPT_CYCLE_PROPERTY: a sheet scored " +
            "without a cycle is the address collision this exists to prevent"
    }
    val stamped = repoRoot().resolve("capture").resolve("receipts").resolve("run-$run").resolve("cycle-$cycle").resolve("$name.png").toFile()
    check(!stamped.exists()) {
        "receipt already written: $stamped\n" +
            "  one run-and-cycle address holds exactly one image. Bump -D$RECEIPT_CYCLE_PROPERTY " +
            "rather than overwriting the sheet an earlier verdict was written against."
    }
    return stamped
}

// Printed for every sheet, stamped or throwaway, so a D-019 ledger line can name the exact image
// it scored even when the image itself was never committed.
fun announceSheet(sheet: File): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(sheet.readBytes()).joinToString("") { "%02x".format(it) }
    println("[receipt] ${sheet.absolutePath} ${sheet.length()} bytes sha256 $digest")
    return digest
}
