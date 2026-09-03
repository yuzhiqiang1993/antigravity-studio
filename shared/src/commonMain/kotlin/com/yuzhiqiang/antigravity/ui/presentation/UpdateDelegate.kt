package com.yuzhiqiang.antigravity.ui.presentation

import com.yuzhiqiang.antigravity.data.storage.ConfigStore
import com.yuzhiqiang.antigravity.update.engine.AppUpdateDownloader
import com.yuzhiqiang.antigravity.update.engine.AppUpdateInstaller
import com.yuzhiqiang.antigravity.update.engine.DownloadProgress
import com.yuzhiqiang.antigravity.update.engine.UpdateChecker
import com.yuzhiqiang.antigravity.update.model.AppUpdateDownloadState
import com.yuzhiqiang.antigravity.update.model.AppVersion
import com.yuzhiqiang.antigravity.update.model.ReleaseInfo
import com.yuzhiqiang.antigravity.update.model.UpdateState
import com.yuzhiqiang.antigravity.ui.components.NoticeKind
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 负责版本检查、下载、安装以及更新弹窗状态。
 *
 * 下载任务仍使用传入的 ViewModel scope，生命周期和原实现保持一致。
 */
class UpdateDelegate(
    private val scope: CoroutineScope,
    private val configStore: ConfigStore,
    private val updateStateFlow: MutableStateFlow<UpdateState>,
    private val showUpdateDialogFlow: MutableStateFlow<Boolean>,
    private val activeReleaseFlow: MutableStateFlow<ReleaseInfo?>,
    private val downloadStateFlow: MutableStateFlow<AppUpdateDownloadState>,
    private val showNotice: (String, NoticeKind) -> Unit,
    private val showUpdateRestrictedDialogFlow: MutableStateFlow<Boolean>? = null,
    private val updateRestrictedErrorFlow: MutableStateFlow<String?>? = null
) {

    private val s get() = com.yuzhiqiang.antigravity.i18n.I18nManager.strings
    private var downloadJob: Job? = null

    fun checkForUpdates(isManual: Boolean = true) {
        scope.launch(Dispatchers.IO) {
            updateStateFlow.value = UpdateState.Checking(isManual)
            val result = UpdateChecker.checkUpdate(currentVersion = AppVersion.CURRENT)
            val now = System.currentTimeMillis()
            configStore.updateConfig { it.copy(lastCheckUpdateTimestamp = now) }

            result.fold(
                onSuccess = { release ->
                    if (release != null) {
                        updateStateFlow.value = UpdateState.Available(
                            release = release,
                            currentVersion = AppVersion.CURRENT,
                            isManual = isManual
                        )
                        activeReleaseFlow.value = release
                        val isIgnored =
                            configStore.currentConfig.ignoredVersion.equals(release.cleanVersion, ignoreCase = true)
                        if (isManual || !isIgnored) {
                            showUpdateDialogFlow.value = true
                        }
                        if (isManual) {
                            showNotice(s.updateAvailableTitle + ": v${release.cleanVersion}", NoticeKind.SUCCESS)
                        }
                    } else {
                        updateStateFlow.value = UpdateState.UpToDate(
                            currentVersion = AppVersion.CURRENT,
                            lastCheckedTimestamp = now,
                            isManual = isManual
                        )
                        if (isManual) {
                            showNotice(s.updateUpToDate, NoticeKind.SUCCESS)
                        }
                    }
                },
                onFailure = { error ->
                    val msg = error.message ?: s.commonUnknown
                    updateStateFlow.value = UpdateState.Error(msg, isManual)
                    if (isManual) {
                        showUpdateRestrictedDialogFlow?.value = true
                        updateRestrictedErrorFlow?.value = msg
                        showNotice(s.updateCheckFailed(msg), NoticeKind.ERROR)
                    }
                }
            )
        }
    }

    fun dismissUpdateDialog() {
        showUpdateDialogFlow.value = false
    }

    fun dismissUpdateRestrictedDialog() {
        showUpdateRestrictedDialogFlow?.value = false
        updateRestrictedErrorFlow?.value = null
    }

    fun openUpdateDialog() {
        if (activeReleaseFlow.value != null) {
            showUpdateDialogFlow.value = true
        } else {
            checkForUpdates(isManual = true)
        }
    }

    fun startDownloadUpdate(release: ReleaseInfo) {
        val asset = release.resolvePlatformAsset()
        if (asset == null) {
            val message = "No update asset matches the current platform"
            downloadStateFlow.value = AppUpdateDownloadState.Failed(message)
            showNotice(s.updateDownloadFailed(message), NoticeKind.ERROR)
            return
        }
        val targetFile = AppUpdateDownloader.resolveTargetFile(asset.name)

        downloadJob?.cancel()
        downloadJob = scope.launch {
            downloadStateFlow.value = AppUpdateDownloadState.Downloading(
                bytesDownloaded = 0L,
                totalBytes = -1L,
                progressRatio = 0f,
                speedBytesPerSec = 0L
            )
            try {
                AppUpdateDownloader.download(asset, release.cleanVersion, targetFile)
                    .collect { progress ->
                        when (progress) {
                            is DownloadProgress.Progress -> {
                                downloadStateFlow.value = AppUpdateDownloadState.Downloading(
                                    bytesDownloaded = progress.bytesDownloaded,
                                    totalBytes = progress.totalBytes,
                                    progressRatio = progress.progressRatio,
                                    speedBytesPerSec = progress.speedBytesPerSec
                                )
                            }

                            is DownloadProgress.Completed -> {
                                downloadStateFlow.value = AppUpdateDownloadState.Completed(progress.artifact)
                                showNotice(s.updateDownloadCompleted, NoticeKind.SUCCESS)
                            }
                        }
                    }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                downloadStateFlow.value = AppUpdateDownloadState.Idle
            } catch (e: Exception) {
                val errMsg = e.message ?: s.commonUnknown
                downloadStateFlow.value = AppUpdateDownloadState.Failed(errMsg)
                showNotice(s.updateDownloadFailed(errMsg), NoticeKind.ERROR)
            }
        }
    }

    fun cancelDownloadUpdate() {
        downloadJob?.cancel()
        downloadJob = null
        downloadStateFlow.value = AppUpdateDownloadState.Idle
    }

    fun installUpdate(artifact: com.yuzhiqiang.antigravity.update.engine.VerifiedUpdateArtifact) {
        scope.launch {
            val result = AppUpdateInstaller.launchInstaller(artifact)
            result.onFailure { error ->
                showNotice(s.updateDownloadFailed(error.message ?: s.commonUnknown), NoticeKind.ERROR)
            }
        }
    }

    fun showDownloadedFileInFolder(file: File) {
        scope.launch {
            AppUpdateInstaller.showInFolder(file)
        }
    }

    fun resetDownloadState() {
        downloadJob?.cancel()
        downloadJob = null
        downloadStateFlow.value = AppUpdateDownloadState.Idle
    }

    fun ignoreUpdateVersion(version: String) {
        configStore.updateConfig { it.copy(ignoredVersion = version) }
        showUpdateDialogFlow.value = false
        showNotice(s.updateIgnoredNotice, NoticeKind.INFO)
    }

    fun updateAutoCheckUpdate(enabled: Boolean) {
        configStore.updateConfig { it.copy(autoCheckUpdate = enabled) }
    }

    fun updateIncludePrerelease(enabled: Boolean) {
        configStore.updateConfig { it.copy(includePrerelease = enabled) }
    }
}
