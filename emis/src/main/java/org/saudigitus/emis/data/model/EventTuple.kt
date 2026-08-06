package org.saudigitus.emis.data.model

import org.dhis2.form.model.RowAction

data class EventTuple(
    val ou: String,
    val program: String,
    val programStage: String,
    val enrollment: String,
    val tei: String,
    val rowAction: RowAction,
    val date: String,
)
