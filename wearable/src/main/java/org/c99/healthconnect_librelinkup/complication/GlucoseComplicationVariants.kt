package org.c99.healthconnect_librelinkup.complication

class GlucoseTrendComplicationService : GlucoseComplicationService() {
    override val includeTrendInText: Boolean = true
    override val useTrendIconInText: Boolean = false
}

class BigGlucoseComplicationService : GlucoseComplicationService()

class BigGlucoseTrendComplicationService : GlucoseComplicationService() {
    override val includeTrendInImage: Boolean = true
}
