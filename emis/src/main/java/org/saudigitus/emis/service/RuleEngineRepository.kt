package org.saudigitus.emis.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.dhis2.commons.bindings.event
import org.dhis2.commons.bindings.organisationUnit
import org.dhis2.commons.bindings.programStage
import org.dhis2.commons.rules.RuleEngineContextData
import org.dhis2.mobileProgramRules.sortForRuleEngine
import org.dhis2.mobileProgramRules.toRuleDataValue
import org.dhis2.mobileProgramRules.toRuleEngineInstant
import org.dhis2.mobileProgramRules.toRuleEngineInstantOrNow
import org.dhis2.mobileProgramRules.toRuleEngineLocalDate
import org.dhis2.mobileProgramRules.toRuleEngineObject
import org.dhis2.mobileProgramRules.toRuleVariable
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.event.EventStatus
import org.hisp.dhis.android.core.program.ProgramRuleActionType
import org.hisp.dhis.rules.api.RuleEngine
import org.hisp.dhis.rules.api.RuleEngineContext
import org.hisp.dhis.rules.api.RuleSupplementaryData
import org.hisp.dhis.rules.models.Rule
import org.hisp.dhis.rules.models.RuleDataValue
import org.hisp.dhis.rules.models.RuleEffect
import org.hisp.dhis.rules.models.RuleEvent
import org.hisp.dhis.rules.models.RuleEventStatus
import org.hisp.dhis.rules.models.RuleVariable
import org.saudigitus.emis.utils.DateHelper
import java.util.Collections
import java.util.Date
import javax.inject.Inject

class RuleEngineRepository @Inject constructor(
    private val d2: D2,
) {

    private val ruleEngine by lazy { RuleEngine.getInstance() }

    private suspend fun supplementaryData(ou: String?) = withContext(Dispatchers.IO) {
        val organisationUnitGroups = HashMap<String, List<String>>()

        ou?.let { organisationUnitUid ->
            d2.organisationUnitModule().organisationUnits()
                .withOrganisationUnitGroups()
                .uid(organisationUnitUid).blockingGet()
                .let { orgUnit ->
                    orgUnit?.organisationUnitGroups()?.mapNotNull {
                        it.code()?.let { code ->
                            organisationUnitGroups[code] = listOf(orgUnit.uid())
                        }
                        organisationUnitGroups[it.uid()] = listOf(orgUnit.uid())
                    }
                }
        }

        return@withContext RuleSupplementaryData(
            userGroups = d2.userModule().userGroups().blockingGetUids(),
            userRoles = d2.userModule().userRoles().blockingGetUids(),
            orgUnitGroups = organisationUnitGroups,
        )
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

    suspend fun rules(
        program: String,
        stage: String? = null,
    ) = withContext(Dispatchers.IO) {
        return@withContext d2.programModule().programRules()
            .byProgramUid().eq(program)
            .withProgramRuleActions()
            .blockingGet()
            .map {
                it.toRuleEngineObject()
            }
            .filter { rule ->
                stage == null || rule.programStage == null || rule.programStage == stage
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
        stage: String,
        targetEvent: String,
    ) = withContext(Dispatchers.IO) {
        val events = d2.eventModule().events()
            .byOrganisationUnitUid().eq(ou)
            .byProgramUid().eq(program)
            .byProgramStageUid().eq(stage)
            .byStatus()
            .notIn(EventStatus.SCHEDULE, EventStatus.SKIPPED, EventStatus.OVERDUE)
            .byEventDate()
            .beforeOrEqual(Date())
            .byDeleted()
            .isFalse
            .withTrackedEntityDataValues()
            .blockingGet()
            .sortForRuleEngine()

        val targetIndex = events.indexOfFirst { it.uid() == targetEvent }
        val contextEvents = if (targetIndex >= 0) {
            events.take(targetIndex).take(10) + events.drop(targetIndex + 1).take(10)
        } else {
            events
        }

        return@withContext contextEvents.mapNotNull { event ->
            // Skip events missing fields required by the rule engine.
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
                status = status.toRuleEventStatus(),
                eventDate = eventDate.toRuleEngineLocalDate(),
                createdDate = event.created().toRuleEngineInstantOrNow(),
                createdAtClientDate = event.createdAtClient()?.toRuleEngineInstant(),
                dueDate = event.dueDate()?.toRuleEngineLocalDate(),
                completedDate = event.completedDate()?.toRuleEngineLocalDate(),
                organisationUnit = organisationUnit,
                organisationUnitCode = d2.organisationUnitModule().organisationUnits()
                    .uid(organisationUnit).blockingGet()?.code(),
                dataValues = event.trackedEntityDataValues()?.toRuleDataValue() ?: emptyList(),
            )
        }
    }

    private fun EventStatus.toRuleEventStatus() = when (this) {
        EventStatus.VISITED -> RuleEventStatus.ACTIVE
        else -> try {
            RuleEventStatus.valueOf(name)
        } catch (e: IllegalArgumentException) {
            RuleEventStatus.ACTIVE
        }
    }

    private suspend fun ruleContext(
        ruleVariables: List<RuleVariable>,
        rules: List<Rule>,
        supplementaryData: RuleSupplementaryData,
        constants: Map<String, String>,
    ) = withContext(Dispatchers.IO) {
        return@withContext RuleEngineContext(
            rules = rules,
            ruleVariables = ruleVariables,
            ruleSupplementaryData = supplementaryData,
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
        val supplementaryData = async { supplementaryData(ou) }.await()

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
        stage: String,
        targetEvent: String,
    ) = withContext(Dispatchers.IO) {
        val rules = async { rules(program, stage) }.await()
        val ruleVariables = ruleVariables(program)
        val constants = async { constants() }.await()
        val ruleEvents = async { ruleEvents(ou, program, stage, targetEvent) }.await()
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
            status = event.status()!!.toRuleEventStatus(),
            eventDate = event.eventDate()!!.toRuleEngineLocalDate(),
            createdDate = event.created().toRuleEngineInstantOrNow(),
            createdAtClientDate = event.createdAtClient()?.toRuleEngineInstant(),
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
        val eventDateValue = Date(DateHelper.dateStringToSeconds(eventDate) * 1000)
        val createdAt = Date()
        val programStageName = d2.programModule().programStages().uid(stage).blockingGet()?.name()

        return RuleEvent(
            event = "",
            programStage = stage,
            programStageName = programStageName ?: "",
            status = RuleEventStatus.ACTIVE,
            eventDate = eventDateValue.toRuleEngineLocalDate(),
            createdDate = createdAt.toRuleEngineInstant(),
            createdAtClientDate = createdAt.toRuleEngineInstant(),
            dueDate = null,
            completedDate = null,
            organisationUnit = ou,
            organisationUnitCode = d2.organisationUnit(ou)?.code(),
            dataValues = dataValues,
        )
    }

    private fun dataEntry(
        dataElement: String,
        value: String,
    ) = RuleDataValue(
        dataElement = dataElement,
        value = value,
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
        val dataValues = Collections.singletonList(dataEntry(dataElement, value))

        val targetEvent = if (event.isNotBlank()) {
            getRuleEvent(event, dataValues)
        } else {
            buildRuleEventForNewEntry(ou, stage, dataValues, eventDate)
        }
        val ruleEngineContextData = ruleEngineContextData(
            ou,
            program,
            targetEvent.programStage,
            targetEvent.event,
        )
        val events = ruleEngineContextData.ruleEvents.filter { it.event != event }

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
        val targetEvent = getRuleEvent(event, dataValues)
        val ruleEngineContextData = ruleEngineContextData(
            ou,
            program,
            targetEvent.programStage,
            targetEvent.event,
        )
        val events = ruleEngineContextData.ruleEvents.filter {
            it.event != event
        }

        return@withContext ruleEngine.evaluate(
            target = targetEvent,
            ruleEnrollment = ruleEngineContextData.ruleEnrollment,
            ruleEvents = events,
            executionContext = ruleEngineContextData.ruleEngineContext,
        )
    }
}
