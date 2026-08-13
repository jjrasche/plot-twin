package plottwin.capture

import plottwin.worldstate.WorldLog
import plottwin.worldstate.WriterRole

// The property line is measured ground, so it enters the log as a CAPTURE row (D-013) and the
// renderer reads it from the projection like everything else - never from the boundary file.
fun appendParcelBoundary(log: WorldLog, boundary: CapturedBoundary): Long =
    log.append(parcelBoundaryRowOf(boundary), WriterRole.CAPTURE)
