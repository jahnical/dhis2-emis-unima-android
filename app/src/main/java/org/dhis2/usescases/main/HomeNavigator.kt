package org.dhis2.usescases.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import org.dhis2.android.rtsm.ui.home.HomeActivity
import org.dhis2.commons.Constants
import org.dhis2.usescases.datasets.datasetDetail.DataSetDetailActivity
import org.dhis2.usescases.main.program.ProgramUiModel
import org.dhis2.usescases.programEventDetail.ProgramEventDetailActivity
import org.dhis2.usescases.searchTrackEntity.SearchTEActivity
import org.hisp.dhis.android.core.D2Manager
import org.hisp.dhis.android.core.program.ProgramType
import org.saudigitus.emis.utils.ProgramValidator
import timber.log.Timber

sealed class HomeItemData(
    open val uid: String,
    open val label: String,
    open val accessDataWrite: Boolean,
) {
    data class TrackerProgram(
        override val uid: String,
        override val label: String,
        override val accessDataWrite: Boolean,
        val trackedEntityType: String,
        val isSEMIS: Boolean,
        val isStockUseCase: Boolean,
    ) : HomeItemData(uid, label, accessDataWrite)

    data class EventProgram(
        override val uid: String,
        override val label: String,
        override val accessDataWrite: Boolean,
    ) : HomeItemData(uid, label, accessDataWrite)

    data class DataSet(
        override val uid: String,
        override val label: String,
        override val accessDataWrite: Boolean,
    ) : HomeItemData(uid, label, accessDataWrite)
}

fun ProgramUiModel.toHomeItemData(): HomeItemData =
    when (programType) {
        ProgramType.WITHOUT_REGISTRATION.name ->
            HomeItemData.EventProgram(
                uid = uid,
                label = title,
                accessDataWrite = accessDataWrite,
            )

        ProgramType.WITH_REGISTRATION.name -> {
            val checkSEMIS = isSEMIS || (D2Manager.isD2Instantiated() && ProgramValidator(D2Manager.getD2()).isSEMIS(uid))
            HomeItemData.TrackerProgram(
                uid = uid,
                label = title,
                accessDataWrite = accessDataWrite,
                trackedEntityType = type!!,
                isSEMIS = checkSEMIS,
                isStockUseCase = isStockUseCase,
            )
        }

        else ->
            HomeItemData.DataSet(
                uid = uid,
                label = title,
                accessDataWrite = accessDataWrite,
            )
    }


fun ActivityResultLauncher<Intent>.navigateTo(
    context: Context,
    homeItemData: HomeItemData,
) {
    val bundle = Bundle()
    val idTag =
        if (homeItemData is HomeItemData.DataSet) {
        Constants.DATASET_UID
    } else {
        Constants.PROGRAM_UID
    }

    bundle.putString(idTag, homeItemData.uid)
    bundle.putString(Constants.DATA_SET_NAME, homeItemData.label)
    bundle.putString(
        Constants.ACCESS_DATA,
        homeItemData.accessDataWrite.toString(),
    )

    when (homeItemData) {
        is HomeItemData.DataSet ->
            Intent(context, DataSetDetailActivity::class.java).apply {
                putExtras(bundle)
                launch(this)
            }

        is HomeItemData.EventProgram ->{
            Intent(context, ProgramEventDetailActivity::class.java).apply {
                putExtras(ProgramEventDetailActivity.getBundle(homeItemData.uid))
                launch(this)
            }
    }
        is HomeItemData.TrackerProgram -> {
            val branch =
                when {
                    homeItemData.isSEMIS -> "SEMIS"
                    homeItemData.isStockUseCase -> "STOCK"
                    else -> "TRACKER"
                }
            Timber.tag("EMIS_WIRING").i(
                "programUid=%s isSEMIS=%s isStockUseCase=%s branch=%s",
                homeItemData.uid,
                homeItemData.isSEMIS,
                homeItemData.isStockUseCase,
                branch,
            )
            if (homeItemData.isSEMIS) {
                Intent(context, org.saudigitus.emis.MainActivity::class.java).apply {
                    putExtras(bundle)
                    launch(this)
                }
            } else if (homeItemData.isStockUseCase) {
                Intent(context, HomeActivity::class.java).apply {
                    putExtras(bundle)
                    Timber.tag("EMIS_WIRING").i(
                        "programUid=%s isSEMIS=%s isStockUseCase=%s branch=STOCK launch=HomeActivity programExtra=%s",
                        homeItemData.uid,
                        homeItemData.isSEMIS,
                        homeItemData.isStockUseCase,
                        getStringExtra(Constants.PROGRAM_UID),
                    )
                    launch(this)
                }
            } else {
                bundle.putString(Constants.TRACKED_ENTITY_UID, homeItemData.trackedEntityType)
                Intent(context, SearchTEActivity::class.java).apply {
                    putExtras(bundle)
                    launch(this)
                }
            }
        }
    }
}
