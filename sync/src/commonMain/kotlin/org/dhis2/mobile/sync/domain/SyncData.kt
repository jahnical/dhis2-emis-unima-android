package org.dhis2.mobile.sync.domain

import org.dhis2.mobile.commons.domain.UseCase
import org.dhis2.mobile.commons.error.DomainError
import org.dhis2.mobile.sync.data.SyncRepository
import org.dhis2.mobile.sync.model.DataSyncProgress
import org.dhis2.mobile.sync.model.DataSyncTask

private const val SYNC_DATA_NAME = "SYNC_DATA"

class SyncData(
    private val repository: SyncRepository,
    private val syncStatusController: SyncStatusController,
) : UseCase<(progress: DataSyncProgress) -> Unit, Unit> {
    override suspend fun invoke(input: (progress: DataSyncProgress) -> Unit): Result<Unit> =
        try {
            when (repository.isServerAvailable(SYNC_DATA_NAME)) {
                true -> repository.removeUnnavailableFlag(SYNC_DATA_NAME)
                false -> {
                    repository.setUnnavailableFlag(SYNC_DATA_NAME)
                    return Result.failure(DomainError.NetworkError("Server not available"))
                }
            }

            syncStatusController.initDownloadProcess(
                repository.getAllProgramsInitialStatus().getOrNull() ?: emptyMap(),
            )
            input(DataSyncProgress(DataSyncTask.UploadEvent, null))
            val uploadEventResult = repository.uploadEvents()
            syncStatusController.startDownloadingEvents()
            val downloadEventResult =
                repository.downloadEvents { progressData ->
                    val done = progressData.count { (_, value) -> value.isComplete() }
                    val progress = 100.0 * done / progressData.size
                    input(DataSyncProgress(DataSyncTask.DownloadEvent, progress))
                    syncStatusController.updateDownloadProcess(progressData)
                }
            syncStatusController.finishDownloadingEvents(
                repository.getAllEventPrograms().getOrNull() ?: emptyList(),
            )

            input(DataSyncProgress(DataSyncTask.UploadTEI, null))
            val uploadTEIResult = repository.uploadTEIs()
            syncStatusController.startDownloadingTracker()
            val downloadTEIResult =
                repository.downloadTEIs { progressData ->
                    val done = progressData.count { (_, value) -> value.isComplete() }
                    val progress = 100.0 * done / progressData.size
                    input(DataSyncProgress(DataSyncTask.DownloadTEI, progress))
                    syncStatusController.updateDownloadProcess(progressData)
                }
            syncStatusController.finishDownloadingTracker(
                repository.getAllTrackerPrograms().getOrNull() ?: emptyList(),
            )

            input(DataSyncProgress(DataSyncTask.UploadDataValue, null))
            val uploadDataValueResult = repository.uploadDataValues()
            syncStatusController.startDownloadingDataSets()
            val downloadDataValueResult =
                repository.downloadDataValues { progressData ->
                    val done = progressData.count { (_, value) -> value.isComplete() }
                    val progress = 100.0 * done / progressData.size
                    input(DataSyncProgress(DataSyncTask.DownloadDataValue, progress))
                }
            syncStatusController.finishDownloadingDataSets(
                repository.getAllDataSets().getOrNull() ?: emptyList(),
            )

            syncStatusController.initDownloadMedia()
            val downloadMediaResult =
                repository.downloadDataFileResources { progress ->
                    input(DataSyncProgress(DataSyncTask.DownloadFileResource, progress))
                }

            val downloadReservedValuesResult =
                repository.downloadReservedValues { progress ->
                    input(DataSyncProgress(DataSyncTask.SyncReservedValues, progress))
                }

            val syncResults =
                listOf(
                    uploadEventResult,
                    downloadEventResult,
                    uploadTEIResult,
                    downloadTEIResult,
                    uploadDataValueResult,
                    downloadDataValueResult,
                    downloadMediaResult,
                    downloadReservedValuesResult,
                )
            val syncFailure = syncResults.firstNotNullOfOrNull { it.exceptionOrNull() }
            val saveStateResult = repository.saveDataSyncState(syncFailure == null)

            when {
                syncFailure != null -> Result.failure(syncFailure)
                else -> saveStateResult
            }
        } catch (domainError: DomainError) {
            if (domainError is DomainError.NetworkError) {
                syncStatusController.onNetworkUnavailable()
            }
            Result.failure(domainError)
        } catch (e: Exception) {
            repository.saveDataSyncError(e.stackTraceToString())
            Result.failure(e)
        } finally {
            syncStatusController.finishSync()
        }
}
