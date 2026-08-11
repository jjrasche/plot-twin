package plottwin.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import plottwin.worldstate.ROAD_ENTITY_NAME
import plottwin.worldstate.WorldLog
import plottwin.worldstate.WriterRole
import plottwin.worldstate.isTreeEntity
import plottwin.worldstate.isWaterEntity

class FeatureIngestionGateTest {

    private fun ingestedState() = WorldLog.openInMemory().use { log ->
        appendRealParcel(log, RealParcelFixture.parcel())
        appendParcelFeatures(log, RealParcelFixture.features())
        log.currentState()
    }

    @Test
    fun tree_count_lands_within_thirty_percent_of_the_chm_crown_maxima() {
        val receipts = RealParcelFixture.features().receipts
        val treeCount = ingestedState().entities.keys.count(::isTreeEntity)
        println("[features] $treeCount tree rows vs ${receipts.crownMaximaCount} CHM crown maxima")
        assertTrue(
            treeCount >= receipts.crownMaximaCount * 0.7 && treeCount <= receipts.crownMaximaCount * 1.3,
            "$treeCount trees outside +-30% of ${receipts.crownMaximaCount} crown maxima",
        )
        assertEquals(receipts.treeCount, treeCount, "log tree rows drifted from the extraction receipt")
    }

    @Test
    fun tree_heights_read_as_a_michigan_woodlot_not_shrubs_or_towers() {
        val heights = ingestedState().entities
            .filterKeys(::isTreeEntity)
            .values.map { it.height.value }
        assertTrue(heights.isNotEmpty(), "no tree entities landed")
        assertTrue(heights.min() >= 3.0, "a tree under the 3m extraction floor landed: ${heights.min()}")
        assertTrue(heights.max() in 15.0..35.0, "tallest tree ${heights.max()}m is not a believable mature canopy")
    }

    @Test
    fun class_six_absence_on_this_woodlot_means_no_structure_rows() {
        val features = RealParcelFixture.features()
        assertEquals(0, features.structures.size, "extraction found structures the class histogram says are absent")
        assertTrue(features.receipts.classHistogram.keys.none { it == "6" }, "class 6 present but no structures extracted")
        assertTrue(
            ingestedState().entities.keys.none { it.startsWith("structure-") },
            "structure rows landed without class-6 evidence",
        )
    }

    @Test
    fun water_rows_exist_exactly_when_the_extraction_found_water() {
        val features = RealParcelFixture.features()
        val waterEntities = ingestedState().entities.keys.filter(::isWaterEntity)
        assertEquals(features.water.size, waterEntities.size, "log water rows drifted from extraction")
    }

    @Test
    fun the_road_corridor_lands_as_a_capture_entity_row() {
        val state = ingestedState()
        val road = state.entities[ROAD_ENTITY_NAME]
        assertTrue(road != null && road.footprint.size >= 3, "no road corridor entity landed")
    }

    @Test
    fun every_feature_row_enters_through_the_log_with_the_capture_writer() {
        WorldLog.openInMemory().use { log ->
            appendRealParcel(log, RealParcelFixture.parcel())
            val seqs = appendParcelFeatures(log, RealParcelFixture.features())
            val bySeq = log.readAll().associateBy { it.seq }
            assertTrue(seqs.isNotEmpty(), "ingestion appended nothing")
            assertTrue(
                seqs.all { bySeq.getValue(it).writer == WriterRole.CAPTURE },
                "a lidar-measured feature row carries a non-CAPTURE writer",
            )
        }
    }
}
