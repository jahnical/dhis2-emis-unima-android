package org.saudigitus.emis.helper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.dhis2.commons.network.NetworkUtils
import org.hisp.dhis.android.core.D2
import org.hisp.dhis.android.core.organisationunit.OrganisationUnitMode
import javax.inject.Inject

class SEMISSync
@Inject constructor(
    private val d2: D2,
    private val networkUtils: NetworkUtils,
) : ISEMISSync {

    override suspend fun downloadTEIsByUids(
        ou: String,
        program: String,
        dataElementIds: List<String>,
        dataValues: List<String>,
    ) = withContext(Dispatchers.IO) {
        val teiUids = async {
            searchTrackedEntityInstances(ou, program, dataElementIds, dataValues)
        }

        val uids = teiUids.await()

        d2.trackedEntityModule().trackedEntityInstanceDownloader()
            .byUid().`in`(uids)
            .byProgramUid(program)
            .blockingDownload()
    }

    private fun searchTrackedEntityInstances(
        ou: String,
        program: String,
        dataElementIds: List<String>,
        dataValues: List<String>,
    ): List<String> {
        var repository = d2.trackedEntityModule().trackedEntitySearch()

        repository = if (networkUtils.isOnline()) {
            repository.onlineFirst().allowOnlineCache().eq(true)
                .byOrgUnits().eq(ou)
                .byOrgUnitMode().eq(OrganisationUnitMode.DESCENDANTS)
                .byProgram().eq(program)
        } else {
            repository.offlineOnly().allowOnlineCache().eq(false)
                .byOrgUnits().eq(ou)
                .byProgram().eq(program)
        }

        val filterCount = minOf(dataElementIds.size, dataValues.size)

        for (i in 0 until filterCount) {
            val id = dataElementIds[i]
            val value = dataValues[i]

            if (id.isNotEmpty() && value.isNotEmpty()) {
                repository = repository.byDataValue(id).eq(value)
            }
        }

        return try {
            repository.blockingGet().map { tei -> tei.uid() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /*private fun searchTrackedEntityInstances(
        ou: String,
        program: String,
        dataElementIds: List<String>,
        dataValues: List<String>,
    ): List<String> {
        val repository = d2.trackedEntityModule().trackedEntitySearch()

        Log.e("DATA_ELEMENTS_IDS", dataElementIds.toString())
        Log.e("DATA_VALUES", dataValues.toString())

        return if (networkUtils.isOnline()) {
            repository.onlineFirst().allowOnlineCache().eq(true)
                .byOrgUnits().eq(ou)
                .byOrgUnitMode().eq(OrganisationUnitMode.ACCESSIBLE)
                .byProgram().eq(program)
                .byDataValue(dataElementIds[0]).eq(dataValues[0])
                .byDataValue(dataElementIds[1]).eq(dataValues[1])
                .byDataValue(dataElementIds[2]).eq(dataValues[2])
                .blockingGet()
                .flatMap { tei -> listOf(tei) }
                .map { tei -> tei.uid() }
        } else {
            repository.offlineOnly().allowOnlineCache().eq(false)
                .byOrgUnits().eq(ou)
                .byProgram().eq(program)
                .byDataValue(dataElementIds[0]).eq(dataValues[0])
                .byDataValue(dataElementIds[1]).eq(dataValues[1])
                .byDataValue(dataElementIds[2]).eq(dataValues[2])
                .blockingGet()
                .flatMap { tei -> listOf(tei) }
                .map { tei -> tei.uid() }
        }
    }*/
}
