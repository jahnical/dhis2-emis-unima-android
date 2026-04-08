package org.saudigitus.emis.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.dhis2.commons.bindings.event
import org.dhis2.commons.bindings.organisationUnit
import org.dhis2.commons.bindings.programStage
import org.dhis2.commons.rules.RuleEngineContextData
import org.dhis2.mobileProgramRules.toRuleDataValue
import org.dhis2.mobileProgramRules.toRuleEngineInstant
import org.dhis2.mobileProgramRules.toRuleEngineLocalDate
import org.dhis2.mobileProgramRules.toRuleEngineObject
import org.dhis2.mobileProgramRules.toRuleVariable
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.event.EventStatus
import org.hisp.dhis.android.core.program.ProgramRuleActionType
import org.hisp.dhis.rules.api.RuleEngine
import org.hisp.dhis.rules.api.RuleEngineContext
import org.hisp.dhis.rules.models.Rule
import org.hisp.dhis.rules.models.RuleDataValue
import org.hisp.dhis.rules.models.RuleEvent
import org.hisp.dhis.rules.models.RuleEventStatus
import org.hisp.dhis.rules.models.RuleVariable
import org.hisp.dhis.rules.models.RuleEffect
import org.saudigitus.emis.utils.DateHelper
import java.util.Collections
import javax.inject.Inject

class RuleEngineRepository @Inject constructor(
    private val d2: D2,
) {

    private val ruleEngine by lazy { RuleEngine.getInstance() }

    private suspend fun supplementaryData(ou: String) = withContext(Dispatchers.IO) {
        val suppData = HashMap<String, List<String>>()

        d2.organisationUnitModule().organisationUnits()
            .withOrganisationUnitGroups()
            .uid(ou).blockingGet()
            .let { orgUnit ->
                orgUnit?.organisationUnitGroups()?.mapNotNull {
                    if (it.code() != null) {
                        suppData[it.code()!!] = listOf(orgUnit.uid())
                    }
                    suppData[it.uid()] = listOf(orgUnit.uid())
                }
            }

        return@withContext suppData
    }

    private suspend fun ruleVariables(program: String) = withContext(Dispatchers.IO) {
        return@withContext d2.programModule().programRuleVariables()
            .byProgramUid().eq(program)
            .blockingGet()
            .map {
                it.toRuleVariable(
                    d2.trackedEntityModule().trackedEntityAttributes(),
                    d2.dataElementModule().dataElements(),
                )
            }
    }

    suspend fun rules(program: String) = withContext(Dispatchers.IO) {
        return@withContext d2.programModule().programRules()
            .byProgramUid().eq(program)
            .withProgramRuleActions()
            .blockingGet()
            .map {
                it.toRuleEngineObject()
            }
    }

    suspend fun constants() = withContext(Dispatchers.IO) {
        return@withContext d2.constantModule()
            .constants().blockingGet()
            .associate { constant ->
                Pair(constant.uid(), "${constant.value()}")
            }
    }

    @Suppress("DEPRECATION")
    private suspend fun ruleEvents(
        ou: String,
        program: String,
    ) = withContext(Dispatchers.IO) {
        return@withContext d2.eventModule().events()
            .byOrganisationUnitUid().eq(ou)
            .byProgramUid().eq(program)
            .withTrackedEntityDataValues()
            .blockingGet()
            .mapNotNull { event ->
                // Skip events missing fields required by the rule engine
                val programStage = event.programStage() ?: return@mapNotNull null
                val eventDate = event.eventDate() ?: return@mapNotNull null
                val status = event.status() ?: return@mapNotNull null
                val organisationUnit = event.organisationUnit() ?: return@mapNotNull null
                val stageName = d2.programModule().programStages()
                    .uid(programStage).blockingGet()?.name() ?: return@mapNotNull null

                RuleEvent(
                    event = event.uid(),
                    programStage = programStage,
                    programStageName = stageName,
                    status = when (status) {
                        EventStatus.VISITED -> RuleEventStatus.ACTIVE
                        else -> try {
                            RuleEventStatus.valueOf(status.name)
                        } catch (e: IllegalArgumentException) {
                            RuleEventStatus.ACTIVE
                        }
                    },
                    eventDate = Instant.fromEpochMilliseconds(eventDate.time),
                    dueDate = event.dueDate()?.let {
                        Instant.fromEpochMilliseconds(it.time)
                            .toLocalDateTime(TimeZone.currentSystemDefault()).date
                    },
                    completedDate = event.completedDate()?.let {
                        Instant.fromEpochMilliseconds(it.time)
                            .toLocalDateTime(TimeZone.currentSystemDefault()).date
                    },
                    organisationUnit = organisationUnit,
                    organisationUnitCode = d2.organisationUnitModule().organisationUnits()
                        .uid(organisationUnit).blockingGet()?.code(),
                    dataValues = event.trackedEntityDataValues()?.toRuleDataValue(
                        event,
                        d2.dataElementModule().dataElements(),
                        d2.programModule().programRuleVariables(),
                        d2.optionModule().options(),
                    ) ?: emptyList(),
                )
            }
    }

    private suspend fun ruleContext(
        ruleVariables: List<RuleVariable>,
        rules: List<Rule>,
        supplementaryData: Map<String, List<String>> = emptyMap(),
        constants: Map<String, String>,
    ) = withContext(Dispatchers.IO) {
        return@withContext RuleEngineContext(
            rules = rules,
            ruleVariables = ruleVariables,
            supplementaryData = supplementaryData,
            constantsValues = constants,
        )
    }

    private suspend fun executeContext(
        ou: String? = null,
        program: String,
    ) = withContext(Dispatchers.IO) {
        val rules = async { rules(program) }.await()
        val ruleVariables = ruleVariables(program)
        val constants = async { constants() }.await()
        val supplementaryData = ou?.let {
            async { supplementaryData(it) }.await()
        } ?: emptyMap()

        return@withContext ruleContext(
            ruleVariables,
            rules,
            supplementaryData,
            constants,
        )
    }

    private suspend fun ruleEngineContextData(
        ou: String,
        program: String,
    ) = withContext(Dispatchers.IO) {
        val rules = async { rules(program) }.await()
        val ruleVariables = ruleVariables(program)
        val constants = async { constants() }.await()
        val ruleEvents = async { ruleEvents(ou, program) }.await()
        val supplementaryData = async { supplementaryData(ou) }.await()

        return@withContext RuleEngineContextData(
            ruleEngineContext = ruleContext(
                ruleVariables,
                rules,
                supplementaryData,
                constants,
            ),
            ruleEnrollment = null,
            ruleEvents = ruleEvents,
        )
    }

    private fun getRuleEvent(
        eventUid: String,
        dataValues: List<RuleDataValue> = emptyList(),
    ): RuleEvent {
        val event = d2.event(eventUid) ?: throw NullPointerException()
        return RuleEvent(
            event = event.uid(),
            programStage = event.programStage()!!,
            programStageName = d2.programStage(event.programStage()!!)?.name()!!,
            status = RuleEventStatus.valueOf(event.status()!!.name),
            eventDate = event.eventDate()!!.toRuleEngineInstant(),
            dueDate = event.dueDate()?.toRuleEngineLocalDate(),
            completedDate = event.completedDate()?.toRuleEngineLocalDate(),
            organisationUnit = event.organisationUnit()!!,
            organisationUnitCode = d2.organisationUnit(event.organisationUnit()!!)?.code(),
            dataValues = dataValues,
        )
    }

    private fun buildRuleEventForNewEntry(
        ou: String,
        stage: String,
        dataValues: List<RuleDataValue> = emptyList(),
        eventDate: String,
    ): RuleEvent {
        val eventInstant = Instant.fromEpochSeconds(DateHelper.dateStringToSeconds(eventDate))
        val programStageName = d2.programModule().programStages().uid(stage).blockingGet()?.name()

        return RuleEvent(
            event = "",
            programStage = stage,
            programStageName = programStageName ?: "",
            status = RuleEventStatus.ACTIVE,
            eventDate = eventInstant,
            dueDate = null,
            completedDate = null,
            organisationUnit = ou,
            organisationUnitCode = d2.organisationUnit(ou)?.code(),
            dataValues = dataValues,
        )
    }

    private fun dataEntry(
        event: String,
        stage: String,
        dataElement: String,
        value: String,
    ) = RuleDataValue(
        eventDate = Instant.fromEpochSeconds(DateHelper.dateStringToSeconds(event)),
        programStage = stage,
        dataElement = dataElement,
        value = value
    )

    /**
     * Evaluate rules for a single data entry and return all rule effects. This will construct a
     * temporary RuleEvent for unsaved/new entries (when event is blank) to avoid NPEs.
     */
    suspend fun evaluateDataEntryEffects(
        ou: String,
        program: String,
        stage: String,
        dataElement: String,
        event: String,
        eventDate: String,
        value: String,
    ): List<RuleEffect> = withContext(Dispatchers.IO) {
        val dataValues = Collections.singletonList(
            dataEntry(
                eventDate,
                stage,
                dataElement,
                value
            )
        )

        val ruleEngineContextData = ruleEngineContextData(ou, program)
        val events = ruleEngineContextData.ruleEvents.filter { it.event != event }

        val targetEvent = if (event.isNotBlank()) {
            try {
                getRuleEvent(event, dataValues)
            } catch (e: Exception) {
                // fall back to constructed event if lookup fails
                buildRuleEventForNewEntry(ou, stage, dataValues, eventDate)
            }
        } else {
            buildRuleEventForNewEntry(ou, stage, dataValues, eventDate)
        }

        return@withContext ruleEngine.evaluate(
            target = targetEvent,
            ruleEnrollment = ruleEngineContextData.ruleEnrollment,
            ruleEvents = events,
            executionContext = ruleEngineContextData.ruleEngineContext,
        )
    }

    suspend fun applyOptionRules(
        ou: String? = null,
        program: String,
        dataElement: String,
    ) = withContext(Dispatchers.IO) {
        val ruleContext = executeContext(ou, program)
        ruleContext.rules
            .asSequence()
            .flatMap { it.actions.asSequence() }
            .filter {
                it.type in setOf(
                    ProgramRuleActionType.HIDEOPTION.name,
                    ProgramRuleActionType.HIDEOPTIONGROUP.name
                )
            }
            .mapNotNull { action ->
                val fieldMatches = action.values["field"] == dataElement
                if (!fieldMatches) return@mapNotNull null

                when (action.type) {
                    ProgramRuleActionType.HIDEOPTION.name -> action.values["option"]
                    ProgramRuleActionType.HIDEOPTIONGROUP.name -> action.values["optionGroup"]
                    else -> null
                }
            }
            .toList()
    }

    /**
     * Evaluate rules for a single data entry and return the first rule effect that shows an error.
     * This will construct a temporary RuleEvent for unsaved/new entries (when event is blank) to
     * avoid NPEs.
     */
    suspend fun evaluateDataEntry(
        ou: String,
        program: String,
        stage: String,
        dataElement: String,
        event: String,
        eventDate: String,
        value: String
    ) = evaluateDataEntryEffects(
        ou,
        program,
        stage,
        dataElement,
        event,
        eventDate,
        value,
    ).find { effect ->
        effect.ruleAction.type == ProgramRuleActionType.SHOWERROR.name
    }

    suspend fun evaluate(
        ou: String,
        program: String,
        event: String,
        dataValues: List<RuleDataValue> = emptyList()
    ) = withContext(Dispatchers.IO) {
        val ruleEngineContextData = ruleEngineContextData(ou, program)
        val events = ruleEngineContextData.ruleEvents.filter {
            it.event != event
        }

        return@withContext ruleEngine.evaluate(
            target = getRuleEvent(event, dataValues),
            ruleEnrollment = ruleEngineContextData.ruleEnrollment,
            ruleEvents = events,
            executionContext = ruleEngineContextData.ruleEngineContext,
        )
    }
}
