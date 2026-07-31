package org.saudigitus.emis.data.local.repository

import android.R.attr.valueType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.dhis2.commons.bindings.dataElement
import org.dhis2.commons.bindings.enrollment
import org.dhis2.commons.date.DateUtils
import org.dhis2.form.ui.provider.HintProvider
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.common.ValueType
import org.hisp.dhis.android.core.event.Event
import org.hisp.dhis.android.core.event.EventCreateProjection
import org.saudigitus.emis.data.local.DataManager
import org.saudigitus.emis.data.local.FormRepository
import org.saudigitus.emis.data.model.EventTuple
import org.saudigitus.emis.data.model.Option
import org.saudigitus.emis.ui.form.FormData
import org.saudigitus.emis.ui.form.FormField
import org.saudigitus.emis.utils.Constants
import org.saudigitus.emis.utils.DateHelper
import timber.log.Timber
import javax.inject.Inject

class FormRepositoryImpl
@Inject constructor(
    private val d2: D2,
    private val hintProvider: HintProvider,
    private val dataManager: DataManager,
) : FormRepository {

    private fun getAttributeOptionCombo() =
        d2.categoryModule().categoryOptionCombos()
            .byDisplayName().eq(Constants.DEFAULT).one().blockingGet()?.uid()

    private fun createEventProjection(
        ou: String,
        program: String,
        programStage: String,
        enrollment: String,
    ): String {
        return d2.eventModule().events()
            .blockingAdd(
                EventCreateProjection.builder()
                    .organisationUnit(ou)
                    .program(program).programStage(programStage)
                    .attributeOptionCombo(getAttributeOptionCombo())
                    .enrollment(enrollment).build(),
            )
    }

    private fun eventUid(
        tei: String,
        ou: String,
        program: String,
        programStage: String,
        enrollment: String,
    ): String? {
        return d2.eventModule().events()
            .byTrackedEntityInstanceUids(listOf(tei))
            .byEnrollmentUid().eq(enrollment)
            .byProgramUid().eq(program)
            .byOrganisationUnitUid().eq(ou)
            .byProgramStageUid().eq(programStage)
            .one().blockingGet()?.uid()
    }

    override suspend fun save(
        eventTuple: EventTuple,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val uid = eventUid(
                eventTuple.tei,
                eventTuple.ou,
                eventTuple.program,
                eventTuple.programStage,
                enrollment = eventTuple.enrollment,
            ) ?: createEventProjection(
                eventTuple.ou,
                eventTuple.program,
                eventTuple.programStage,
                eventTuple.enrollment,
            )

            d2.trackedEntityModule().trackedEntityDataValues()
                .value(uid, eventTuple.rowAction.id)
                .blockingSet(eventTuple.rowAction.value)

            val repository = d2.eventModule().events().uid(uid)

            val result = repository.blockingGet()

            return@withContext result != null
        } catch (e: Exception) {
            Timber.tag("SAVE_EVENT").e(e)
            return@withContext false
        }
    }

    override suspend fun keyboardInputTypeByStage(
        program: String,
        stage: String,
        dl: String,
    ) = withContext(Dispatchers.IO) {
        d2.programModule().programStageDataElements()
            .byProgramStage().eq(stage)
            .byDataElement().eq(dl)
            .blockingGet()
            .map { stageDls ->
                FormField(
                    uid = stageDls.dataElement()?.uid().orEmpty(),
                    label = stageDls.dataElement()?.displayFormName().orEmpty(),
                    type = stageDls.dataElement()?.valueType(),
                    placeholder = hintProvider
                        .provideDateHint(stageDls.dataElement()?.valueType() ?: ValueType.TEXT),
                    options = getOptions(program, stageDls.dataElement()?.uid().orEmpty())
                )
            }
    }

    override suspend fun getOptions(
        program: String,
        dataElement: String,
    ): List<Option> = withContext(Dispatchers.IO) {
        return@withContext dataManager.getOptions(
            program = program,
            dataElement = dataElement
        ).map {
            Option(
                uid = it.id,
                code = it.code,
                displayName = it.itemName,
            )
        }
    }

    @Throws(IllegalArgumentException::class)
    override fun getEvents(
        ou: String,
        program: String,
        programStage: String,
        dataElement: String,
        enrollments: List<String>,
    ): Flow<List<FormData>> = flow {
        emit(
            d2.eventModule().events()
                .byEnrollmentUid().`in`(enrollments)
                .byProgramUid().eq(program)
                .byProgramStageUid().eq(programStage)
                .withTrackedEntityDataValues()
                .blockingGet()
                .mapNotNull {
                    eventsTransform(it, program, dataElement)
                },
        )
    }.flowOn(Dispatchers.IO)

    private suspend fun eventsTransform(
        event: Event,
        program: String,
        dataElement: String,
    ): FormData? {
        val dataValue = event.trackedEntityDataValues()?.find { it.dataElement() == dataElement }
        val tei = d2.enrollment(event.enrollment().toString())?.trackedEntityInstance() ?: ""

        val dl = d2.dataElement(dataElement)
        val options = this.getOptions(program, dataElement)

        return if (dataValue != null) {
            FormData(
                tei = tei,
                event = event.uid(),
                dataElement = dl?.uid() ?: "",
                value = dataValue.value(),
                date = DateHelper.formatDate(
                    event.eventDate()?.time ?: DateUtils.getInstance().today.time,
                ).toString(),
                valueType = dl?.valueType(),
                hasOptions = options.isNotEmpty(),
                itemOptions = if (options.isNotEmpty()) {
                    options.find { option -> option.code == dataValue.value() }
                } else {
                    null
                },
            )
        } else {
            null
        }
    }
}
