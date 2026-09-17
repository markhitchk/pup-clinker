package com.harleytg.puppyclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PuppyWidgetSnapshotTest {
    @Test
    fun widgetSnapshotRoundTrips() {
        val snapshot = PuppyWidgetSnapshot(
            puppyName = "Buddy",
            puppyStyle = "classic",
            treats = 1234L,
            careScore = 88,
            bond = 73,
            updatedAtMs = 99L
        )
        assertEquals(snapshot, PuppyWidgetSnapshotStore.decode(PuppyWidgetSnapshotStore.encode(snapshot)))
    }

    @Test
    fun malformedSnapshotIsRejected() {
        assertNull(PuppyWidgetSnapshotStore.decode("not-json"))
        assertNull(PuppyWidgetSnapshotStore.decode("{\"puppyName\":\"\",\"puppyStyle\":\"classic\"}"))
    }

    @Test
    fun numericValuesAreNormalized() {
        val decoded = PuppyWidgetSnapshotStore.decode(
            "{\"puppyName\":\"Buddy\",\"puppyStyle\":\"classic\",\"treats\":-1,\"careScore\":500,\"bond\":-20,\"updatedAtMs\":7}"
        )!!
        assertEquals(0L, decoded.treats)
        assertEquals(100, decoded.careScore)
        assertEquals(0, decoded.bond)
        assertEquals(7L, decoded.updatedAtMs)
    }
}
