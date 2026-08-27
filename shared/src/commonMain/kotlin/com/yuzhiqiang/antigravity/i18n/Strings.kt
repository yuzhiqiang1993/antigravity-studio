package com.yuzhiqiang.antigravity.i18n

interface Strings {
    val appName: String
    val appSubtitle: String

    // Navigation & Sidebar
    val navOverview: String
    val navAccounts: String
    val navModels: String
    val navActivity: String
    val navSettings: String
    val navDoctor: String
    val sidebarCollapse: String
    val sidebarExpand: String

    // Accounts Screen
    val accountsTitle: String
    val accountsSubtitle: String
    val accountsAddAccount: String
    val accountsAddViaBrowser: String
    val accountsAddViaToken: String
    val accountsActiveInIde: String
    val accountsSetActive: String
    val accountsDelete: String
    val accountsRefreshToken: String
    val accountsCopyToken: String
    val accountsEmptyState: String
    val accountsEmptyDesc: String
    val accountsTokenExpiringSoon: String
    val accountsTokenExpired: String
    val accountsTokenHealthy: String
    val accountsExpiresIn: String
    val accountsAddDialogTitle: String
    val accountsAddDialogBrowserDesc: String
    val accountsAddDialogTokenDesc: String
    val accountsAddDialogTokenPlaceholder: String
    val accountsWaitingBrowserAuth: String
    val accountsAuthSuccess: String
    val accountsAuthFailed: String
    val accountsCopiedEmail: String
    fun accountsEmailTooltip(email: String): String


    // Overview & Proxy Card
    val overviewProxyCardTitle: String
    val overviewProxyRunning: String
    val overviewProxyStopped: String
    val overviewProxyPort: String
    val overviewStartProxy: String
    val overviewStopProxy: String
    val overviewRestartProxy: String
    val overviewSubtitle: String
    val overviewCopyAddress: String
    val overviewDiagnostics: String
    val overviewProviderMetric: String
    val overviewModelMetric: String
    val overviewDisabledMetric: String
    val overviewHostSection: String
    val overviewNotice: String
    val overviewCopiedProxyAddress: String
    val hostUpdateFailed: String

    // Host Titles & Descriptions
    val hostIdeTitle: String
    val hostIdeDesc: String
    val hostAppTitle: String
    val hostAppDesc: String
    val hostCliTitle: String
    val hostCliDesc: String

    // Host Status Labels
    val hostStatusActive: String
    val hostStatusInactive: String
    val hostStatusNotInstalled: String
    val hostStatusReady: String
    val hostStatusInstalled: String
    val hostStatusRunning: String
    val hostStatusNeedsUpdate: String
    val hostStatusMismatch: String

    // Host Actions
    val hostEnable: String
    val hostDisable: String
    val hostRestartNotice: String
    val hostLaunch: String
    val hostRestart: String
    val hostUpdateAction: String
    val hostConfigurePath: String
    val hostForceReset: String
    fun hostCustomPath(path: String): String
    val hostProxyMode: String

    // Host Detail Status Text
    fun hostIdePortMismatch(endpoint: String): String
    val hostIdeRunning: String
    val hostIdeRunningAndConfigured: String
    val hostIdeReady: String
    val hostIdeNotDetected: String
    fun hostIdePendingUpdate(port: Int): String
    val hostIdeActiveDesc: String
    val hostOfficialDirectDesc: String

    fun hostAppPortMismatch(endpoint: String): String
    val hostAppRunning: String
    val hostAppRunningAndConfigured: String
    val hostAppReady: String
    val hostAppNotDetected: String
    fun hostAppPendingUpdate(port: Int): String
    val hostAppActiveDesc: String

    fun hostCliPortMismatch(endpoint: String): String
    val hostCliInstalledDesc: String
    val hostCliNotDetected: String
    fun hostCliPendingUpdate(port: Int): String
    val hostCliActiveDesc: String
    val hostCliOfficialDirectDesc: String

    // Host Confirm Dialogs & Notices
    val hostIdeUpdateConfirmTitle: String
    fun hostIdeUpdateConfirmMessageRunning(endpoint: String, port: Int): String
    fun hostIdeUpdateConfirmMessageStopped(endpoint: String, port: Int): String
    val hostIdeEnableConfirmTitle: String
    val hostIdeEnableConfirmMessageRunning: String
    val hostIdeEnableConfirmMessageStopped: String
    val hostIdeDisableConfirmTitle: String
    val hostIdeDisableConfirmMessageRunning: String
    val hostIdeDisableConfirmMessageStopped: String
    val hostIdeUpdatedAndRestarted: String
    val hostIdeEnabledAndRestarted: String
    val hostIdeEnabledPendingStart: String
    val hostIdeConfigUpdatedRestartFailed: String
    val hostIdeEnableFailed: String
    val hostIdeRestoredAndRestarted: String
    val hostIdeRestored: String
    val hostIdeDisableFailed: String

    val hostAppUpdateConfirmTitle: String
    fun hostAppUpdateConfirmMessageRunning(endpoint: String, port: Int): String
    fun hostAppUpdateConfirmMessageStopped(endpoint: String, port: Int): String
    val hostAppEnableConfirmTitle: String
    val hostAppEnableConfirmMessageRunning: String
    val hostAppEnableConfirmMessageStopped: String
    val hostAppDisableConfirmTitle: String
    val hostAppDisableConfirmMessageRunning: String
    val hostAppDisableConfirmMessageStopped: String
    val hostAppUpdatedAndRestarted: String
    val hostAppEnabledAndRestarted: String
    val hostAppEnabledPendingStart: String
    val hostAppConfigUpdatedRestartFailed: String
    val hostAppEnableFailed: String
    val hostAppRestoredAndRestarted: String
    val hostAppRestored: String
    val hostAppDisableFailed: String
    val hostAppNotInstalled: String

    val hostCliUpdateConfirmTitle: String
    fun hostCliUpdateConfirmMessage(endpoint: String, port: Int): String
    val hostCliEnableConfirmTitle: String
    val hostCliEnableConfirmMessage: String
    val hostCliDisableConfirmTitle: String
    val hostCliDisableConfirmMessage: String
    val hostCliEnabledNotice: String
    val hostCliDisabledNotice: String
    val hostCliEnableFailed: String
    val hostCliDisableFailed: String
    val hostCliNotInstalled: String

    fun hostStartProxyFirstNotice(hostName: String): String
    fun hostForceResetConfirmTitle(hostName: String): String
    fun hostForceResetConfirmMessage(hostName: String): String
    fun hostForceResetSuccess(hostName: String): String
    fun hostRestartConfirmTitle(hostName: String): String
    fun hostRestartConfirmMessage(hostName: String): String
    fun hostRestartSuccess(hostName: String): String
    fun hostRestartFailed(hostName: String): String
    fun hostLaunchSuccess(hostName: String): String
    fun hostLaunchFailed(hostName: String): String
    fun hostLaunchProxyNotRunning(hostName: String): String

    // Custom Host Path Dialog
    fun hostPathDialogTitle(hostTitle: String): String
    val hostPathDialogDesc: String
    val hostPathInputLabel: String
    val hostPathStatusValid: String
    val hostPathStatusNotFound: String
    val hostPathStatusEmpty: String
    val hostPathResetDefault: String
    val hostPathSavedCustom: String
    val hostPathResetNotice: String
    val hostPathBrowse: String
    val hostPathSuggestedTitle: String
    val hostPathSelectFile: String

    // Models Screen & Views
    val modelsTitle: String
    val modelsSubtitle: String
    val modelsOfficialTab: String
    val modelsCustomTab: String
    val modelsOfficialTitle: String
    val modelsOfficialSubtitle: String
    val modelsCustomTitle: String
    val modelsAddProvider: String
    val modelsEditProvider: String
    val modelsDeleteProvider: String
    val modelsTestConnection: String
    val modelsFetchModels: String
    val modelsFetchingModels: String
    val modelsNoProviders: String
    val modelsCompressionPolicy: String
    val modelsReasoningConfig: String
    val modelsCollapse: String
    val modelsExpand: String
    val modelsContext: String
    val modelsVision: String
    val modelsTools: String
    val modelsReasoning: String
    val modelsNoModels: String
    val modelsTesting: String
    val modelsConnectionOk: String
    val modelsConnectionFailed: String
    val modelsRefreshOfficial: String
    val modelsFetchingOfficial: String
    fun modelsOfficialSyncFailed(error: String): String
    val modelsOfficialSyncing: String
    val modelsOfficialSynced: String
    val modelsOfficialWaitingSync: String
    val modelsRawJson: String
    val modelsModifiedJson: String
    val modelsNoOfficialDetected: String
    val modelsNoOfficialHint: String
    val modelsCopiedProviderUrl: String
    val modelsCopiedJson: String
    fun modelsPassedCount(passed: Int, total: Int): String
    fun modelsPassedWithFailed(passed: Int, total: Int, failed: Int): String
    fun modelsRetryFailed(count: Int): String
    val modelsBatchTest: String
    val modelsEditConfig: String
    val modelsNoModelsHint: String
    val modelsDeleteProviderConfirmTitle: String
    fun modelsDeleteProviderConfirmMessage(providerName: String, modelCount: Int): String
    val modelsDeleteModelConfirmTitle: String
    fun modelsDeleteModelConfirmMessage(modelName: String): String
    val modelsRawJsonTitle: String
    val modelsModifiedJsonTitle: String
    val modelsJsonData: String
    fun modelsPolicyCapacityWithPrep(limit: String, prep: String): String
    val modelsOfficialDefault: String
    val modelsVisionDesc: String
    val modelsToolsDesc: String
    val modelsSpecsDesc: String
    fun modelsTestSuccess(latency: String): String
    val modelsTestFailed: String
    val modelsEnabledDesc: String
    val modelsDisabledDesc: String
    val modelsEditModel: String
    val modelsDeleteModel: String
    val modelsReasoningLevelLabel: String
    val modelsCompressionPolicyLabel: String
    val modelsEditPolicy: String
    val modelsCopyJson: String
    val modelsNotSet: String

    // Models Presentation Notices & Exceptions
    fun modelsProviderSaved(name: String): String
    fun modelsProviderSaveFailed(error: String): String
    fun modelsProviderDeleted(name: String): String
    fun modelsProviderDeleteFailed(error: String): String
    fun modelsModelDeleted(name: String): String
    fun modelsModelDeleteFailed(error: String): String
    fun modelsModelUpdated(name: String): String
    fun modelsModelUpdateFailed(error: String): String
    fun modelsModelTestSuccess(modelName: String, latencyMs: Long): String
    fun modelsModelTestFailed(modelName: String, error: String): String
    fun modelsBatchTestSuccess(passed: Int, total: Int): String
    fun modelsBatchTestPartial(passed: Int, total: Int, failed: Int): String
    val modelsProviderNotFound: String
    fun modelsVirtualModelNotFound(id: String): String
    val providerPresetCustomName: String
    val providerPresetCustomDesc: String

    // Model Capability & Specs Dialogs
    val modelReasoningTitle: String
    val modelReasoningDesc: String
    fun modelReasoningLevel(level: String): String
    val modelReasoningHighDesc: String
    val modelReasoningMediumDesc: String
    val modelReasoningLowDesc: String
    val modelReasoningAdaptiveDesc: String
    val modelVisionTitle: String
    val modelVisionDesc: String
    val modelVisionImageTitle: String
    val modelVisionImageDesc: String
    val modelVisionDocTitle: String
    val modelVisionDocDesc: String
    val modelToolsFunctionTitle: String
    val modelToolsFunctionDesc: String
    val modelSpecsTitle: String
    val modelSpecsId: String
    val modelSpecsContextWindow: String
    val modelSpecsDynamicConfig: String
    val modelSpecsMaxOutput: String
    val modelSpecsDefaultLimit: String
    val modelSpecsRoles: String

    // Policy Editor Dialog
    val policyPresetDefault: String
    val policyPresetCustom: String
    val policyRecommended: String
    val policyDefaultDesc: String
    val policyCustomDesc: String
    val policyPresetDesc: String
    val policyCompressorModel: String
    val policyFollowCurrent: String
    val policyOfficialDefault: String
    val policyCheckpoint: String
    val policyContextLimit: String
    val policyOutputReserve: String
    val policyByPercentage: String
    val policyExactTokens: String
    val policyQuickPreset: String
    val policyDistribution: String
    val policyLegendNormal: String
    val policyLegendArchive: String
    val policyLegendUnused: String
    fun policyModelContext(context: String): String
    val policyLimitMustPositive: String
    val policyThresholdMustPositive: String
    val policyReserveMustPositive: String
    fun policyLimitExceedsContext(limit: String, context: String): String
    fun policyLimitExceedsSafeLimit(limit: String, safeLimit: String, context: String, reserve: String): String
    fun policyThresholdExceedsLimit(threshold: String, limit: String): String
    fun policySumExceedsLimit(sum: String, limit: String): String
    val policyFormulaHint: String
    val policyFormulaHintDesc: String

    // Reasoning Config Dialog
    val reasoningDialogTitle: String
    val reasoningEnableTitle: String
    val reasoningEnableSubtitle: String
    val reasoningAvailableLevels: String
    val reasoningCustomValue: String
    val reasoningOptional: String
    val reasoningCustomValueDesc: String
    val reasoningDefaultBudget: String
    val reasoningDynamicBudgetPlaceholder: String
    val reasoningMinBudgetTitle: String
    fun reasoningExamplePlaceholder(example: String): String
    val reasoningBudget: String
    val reasoningMinBudget: String
    fun reasoningMustBeInteger(label: String): String
    val reasoningGeminiBudgetValidation: String
    val reasoningMinBudgetMustPositive: String
    val reasoningMinBudgetExceedsBudget: String
    val reasoningOnlyGeminiSupportsBudget: String
    val reasoningCustomValueInvalid: String
    fun reasoningLevelInvalid(level: String): String
    val reasoningSelectAtLeastOne: String

    // Provider Editor & Steps
    val providerPresetCategoryAll: String
    val providerPresetCategoryAggregator: String
    val providerPresetCategoryRecommended: String
    val providerPresetCategoryOfficial: String
    val providerPresetCategoryLocalCustom: String
    val providerSearchPlaceholder: String
    val providerTagOfficial: String
    val providerTagAggregator: String
    val providerTagLocal: String
    val providerTagCustom: String
    val providerNameLabel: String
    val providerNamePlaceholder: String
    val providerNameDesc: String
    val providerProtocolLabel: String
    val providerProtocolOpenAIChatDesc: String
    val providerProtocolAnthropicDesc: String
    val providerProtocolGeminiDesc: String
    val providerProtocolOpenAIResponsesDesc: String
    val providerBaseUrlLabel: String
    val providerBaseUrlPlaceholder: String
    val providerBaseUrlDesc: String
    val providerApiKeyLabel: String
    val providerApiKeyPlaceholder: String
    val providerApiKeyDesc: String
    val providerAdvancedSettings: String
    val providerAdvancedAutoGenerated: String
    val providerAdvancedCollapse: String
    val providerCustomModelsEndpoint: String
    val providerCustomCompletionsEndpoint: String
    val providerEndpointAutoInferPlaceholder: String
    val providerStepPreset: String
    val providerStepConnection: String
    val providerStepModels: String
    val providerNextStep: String
    val providerPrevStep: String
    val providerSearchModelsPlaceholder: String
    fun providerFilterAll(count: Int): String
    fun providerFilterSelected(count: Int): String
    fun providerFilterUnselected(count: Int): String
    fun providerSelectAll(count: Int): String
    val providerUnselectAll: String
    val providerViewModelsResponse: String
    fun providerModelsResponseUnavailable(error: String): String
    fun providerNoModelsFound(query: String): String
    val providerNoModelsEmpty: String
    fun providerTestLatency(latencyMs: Long): String
    val providerTestFailed: String
    val providerTestFailureDetailsTitle: String
    val providerTestFailureStatusCode: String
    val providerTestFailureErrorDetails: String
    val providerTestFailureRetry: String
    val providerTestFailureCopy: String
    val providerTestFailureCopied: String
    val providerTestFailureClose: String
    val providerTokenLimitNotSet: String
    fun providerCustomInputTokenTitle(model: String): String
    fun providerCustomOutputTokenTitle(model: String): String
    val providerUnprobedCatalog: String
    val providerTesting: String
    val providerTestBtn: String
    fun providerInputTokenPrefix(label: String): String
    fun providerOutputTokenPrefix(label: String): String
    val providerCustomTokenOption: String
    val providerClearTokenOption: String
    val providerCustomTokenDialogTitle: String
    val providerCustomTokenPlaceholder: String
    val providerCustomTokenHint: String
    fun providerCustomTokenParsed(tokens: String): String
    val providerFetchFailedCheckUrlKey: String
    fun providerFetchFailedWithError(error: String): String
    val providerDiscardConfirmTitle: String
    val providerDiscardConfirmMessage: String
    val providerSkipFetchManualAdd: String
    val providerAddNewModel: String
    val providerManualAddModelTitle: String
    val providerModelIdPlaceholder: String
    val providerModelNamePlaceholder: String
    val providerModelVendorPlaceholder: String
    val providerModelAlreadyExists: String
    val providerModelIdRequired: String
    val providerAddAndSelect: String
    val providerNoModelsEmptyPrompt: String

    // Activity Screen & Detail Dialog
    val activityTitle: String
    val activitySubtitle: String
    val activityFilterAll: String
    val activityFilterFailed: String
    val activityClear: String
    val activityEmpty: String
    val activityEmptyDesc: String
    val activityNoMatchingLogs: String
    val activityNoMatchingDesc: String
    val activityPassthrough: String
    val activityRouted: String
    val activitySearchPlaceholder: String
    val activityRecent: String
    val activityTotal: String
    val activityFailedTotal: String
    val activityAverage: String
    val activityCacheHitRate: String
    val activityFirstTokenLabel: String
    val activityPending: String
    val activityProcessing: String
    val activityAllTags: String
    val activityTagFilterTitle: String
    val activitySelectAll: String
    val activityClearFilter: String
    fun activitySelectedTagsCount(count: Int): String
    val activityTokenInput: String
    val activityTokenOutput: String
    val activityTokenCache: String
    val activityTokenTotal: String
    val activityDetailCacheHitRate: String
    val activityAutoScroll: String
    val activityInMemory: String
    val activityHealthy: String
    val activityHasErrors: String
    val activityUnknownProvider: String
    val activityDetailTitle: String
    val activityDetailRouteSection: String
    val activityDetailMethod: String
    val activityDetailPath: String
    val activityDetailDuration: String
    val activityDetailFirstToken: String
    val activityDetailTimestamp: String
    val activityDetailRouteMode: String
    val activityDetailPassthroughMode: String
    val activityDetailForwardMode: String
    val activityDetailTargetModel: String
    val activityDetailRequestedModel: String
    val activityDetailProvider: String
    val activityDetailTokenSection: String
    val activityDetailPromptTokens: String
    val activityDetailCompletionTokens: String
    val activityDetailTotalTokens: String
    val activityDetailReasoningTokens: String
    val activityDetailCacheReadTokens: String
    val activityDetailCacheWriteTokens: String
    val activityDetailErrorSection: String
    val activityDetailCopyJson: String
    val activityDetailCopyError: String
    val activityDetailCopiedError: String
    val activityRetryCount: String
    fun activityRetryBadge(count: Int): String

    // Settings Screen & Sections
    val settingsTitle: String
    val settingsSubtitle: String
    val settingsGeneral: String
    val settingsNetwork: String
    val settingsData: String
    val settingsAboutSection: String
    val settingsLanguage: String
    val settingsLanguageDescription: String
    val settingsTheme: String
    val settingsThemeDescription: String
    val settingsThemeSystem: String
    val settingsThemeLight: String
    val settingsThemeDark: String
    val settingsThemePalette: String
    val settingsThemePaletteDescription: String
    val paletteIndigo: String
    val paletteOcean: String
    val paletteEmerald: String
    val paletteViolet: String
    val paletteRose: String
    val paletteAmber: String
    val settingsPort: String
    val settingsPortDescription: String
    val settingsPortInvalid: String
    fun settingsPortUpdated(port: Int): String
    fun settingsPortRestartFailed(error: String): String
    fun settingsPortUpdateFailed(error: String): String
    val settingsHostPathsTitle: String
    val settingsHostPathsDesc: String
    val settingsDefaultSwitchTargetTitle: String
    val settingsDefaultSwitchTargetDesc: String
    val settingsDefaultSwitchTargetAll: String
    val settingsDefaultSwitchTargetIdeOnly: String
    val settingsDefaultSwitchTargetAppCliOnly: String
    val settingsDefaultSwitchTargetRemember: String
    fun settingsHostPathCustom(title: String): String
    fun settingsHostPathAuto(title: String): String
    val settingsStoragePath: String
    val settingsStorageDescription: String
    val settingsOpenDirectory: String
    val settingsDirectoryOpenError: String
    val settingsUnsupportedPlatform: String
    fun settingsOpenDirFailed(error: String): String
    val settingsAbout: String
    val settingsAboutDescription: String
    val settingsVersion: String
    val settingsRepo: String
    val settingsConfigDir: String
    val settingsOpenConfigDir: String
    val settingsDeveloper: String
    val settingsFeedback: String
    val settingsFeedbackDesc: String

    // Update & Version Checker
    val updateCheck: String
    val updateChecking: String
    val updateUpToDate: String
    val updateAvailableTitle: String
    fun updateAvailableSubtitle(version: String): String
    val updateChangelogTitle: String
    val updateCurrentVersionLabel: String
    val updateLatestVersionLabel: String
    val updateDownloadNow: String
    val updateLater: String
    val updateIgnoreThisVersion: String
    val updateIgnoredNotice: String
    fun updateCheckFailed(error: String): String
    val updateNoChangelog: String
    fun updateDownloadProgress(downloaded: String, total: String, percent: Int): String
    fun updateDownloadSpeed(speed: String): String
    val updateDownloading: String
    val updateDownloadCompleted: String
    val updateInstallNow: String
    val updateShowInFolder: String
    fun updateDownloadFailed(error: String): String
    val updateRetryDownload: String
    val updateOpenInBrowser: String
    val updateCancelDownload: String
    val settingsAutoCheckUpdate: String
    val settingsAutoCheckUpdateDesc: String
    val settingsCheckUpdateBtn: String
    val settingsCheckingUpdate: String
    val settingsLatestVersionBadge: String
    val settingsNewVersionBadge: String
    fun settingsLastChecked(time: String): String
    val settingsDeveloperMode: String
    val settingsDeveloperModeDesc: String
    val settingsDeveloperModeEnabled: String
    val settingsDeveloperModeDisabled: String
    val developerModeDialogTitle: String
    val developerModeUnlockPrompt: String
    val developerModeTurnOn: String
    val developerModeTurnOff: String
    val developerModeWrongPassword: String
    val developerModeKeepEnabled: String
    val developerModeCancel: String

    // Doctor Diagnostics
    val doctorTitle: String
    val doctorSubtitle: String
    val doctorRunAll: String
    val doctorScanning: String
    val doctorPassed: String
    val doctorFailed: String
    val doctorWarning: String
    val doctorDirect: String
    val doctorFixSuggestions: String
    val doctorDialogTitle: String
    val doctorDialogSubtitle: String
    val doctorBannerGood: String
    val doctorBannerWarning: String
    val doctorBannerError: String
    fun doctorBannerIssueCount(count: Int): String
    fun doctorBannerStats(total: Int, passed: Int, issues: Int): String
    fun doctorCheckedAt(time: String): String
    val doctorCategoryProxy: String
    val doctorCategoryNetwork: String
    val doctorCategoryConfig: String
    val doctorCategoryProvider: String
    val doctorCategoryHost: String
    val doctorScanningStatus: String
    val doctorRealtimeStatus: String
    val doctorScanningTitle: String
    val doctorScanningDesc: String
    val doctorFixStartProxy: String
    val doctorFixGoConfigure: String
    val doctorFixOneClickEnable: String
    val doctorFixUpdateConfig: String
    val doctorFixResetOfficial: String
    val doctorFixRestartIde: String
    val doctorFixRestartApp: String
    val doctorFixPruneModels: String
    val doctorFixRetry: String
    val doctorSuggestionPrefix: String
    val doctorAutoFixSuccess: String
    val doctorAutoFixFailed: String

    // Doctor Engine Checks
    val doctorCheckProxyStoppedTitle: String
    fun doctorCheckProxyStoppedMsg(port: Int): String
    val doctorCheckProxyStoppedSugg: String
    val doctorCheckProxyOkTitle: String
    fun doctorCheckProxyOkMsg(port: Int): String
    val doctorCheckProxyUnreachableTitle: String
    fun doctorCheckProxyUnreachableMsg(port: Int): String
    val doctorCheckProxyUnreachableSugg: String
    val doctorCheckNetworkOkTitle: String
    fun doctorCheckNetworkOkMsg(latencyMs: Long): String
    val doctorCheckNetworkFailedTitle: String
    fun doctorCheckNetworkFailedMsg(error: String): String
    val doctorCheckNetworkFailedSugg: String
    val doctorCheckNoProvidersTitle: String
    val doctorCheckNoProvidersMsg: String
    val doctorCheckNoProvidersSugg: String
    fun doctorCheckProviderNoModelsTitle(provider: String): String
    val doctorCheckProviderNoModelsMsg: String
    val doctorCheckProviderNoModelsSugg: String
    val doctorCheckIdeMismatchTitle: String
    fun doctorCheckIdeMismatchMsg(current: String, targetPort: Int): String
    val doctorCheckIdeMismatchSugg: String
    val doctorCheckIdeRunningSuffix: String
    val doctorCheckIdeOkTitle: String
    fun doctorCheckIdeOkMsg(port: Int, runningSuffix: String): String
    val doctorCheckIdeOfficialTitle: String
    val doctorCheckIdeOfficialMsg: String
    val doctorCheckAppMismatchTitle: String
    fun doctorCheckAppMismatchMsg(current: String, targetPort: Int): String
    val doctorCheckAppMismatchSugg: String
    val doctorCheckAppRunningSuffix: String
    val doctorCheckAppOkTitle: String
    fun doctorCheckAppOkMsg(port: Int, runningSuffix: String): String
    val doctorCheckAppOfficialTitle: String
    val doctorCheckAppOfficialMsg: String
    val doctorCheckCliMismatchTitle: String
    fun doctorCheckCliMismatchMsg(current: String, targetPort: Int): String
    val doctorCheckCliMismatchSugg: String
    val doctorCheckCliOkTitle: String
    fun doctorCheckCliOkMsg(port: Int): String
    val doctorCheckCliOfficialTitle: String
    val doctorCheckCliOfficialMsg: String
    fun doctorCheckProviderInvalidModelsTitle(provider: String): String
    fun doctorCheckProviderInvalidModelsMsg(models: String): String
    val doctorCheckProviderInvalidModelsSugg: String
    fun doctorCheckProviderOkTitle(provider: String): String
    fun doctorCheckProviderOkMsg(count: Int): String
    fun doctorCheckProviderUnverifiedTitle(provider: String): String
    val doctorCheckProviderUnverifiedMsg: String
    val doctorCheckProviderUnverifiedSugg: String

    // Common Feedback & Tray
    val commonSave: String
    val commonCancel: String
    val commonConfirm: String
    val commonDelete: String
    val commonEdit: String
    val commonClose: String
    val commonSuccess: String
    val commonError: String
    val commonCopied: String
    val commonGotIt: String
    val commonRefresh: String
    val commonRetry: String
    val commonSearch: String
    val commonClear: String
    val commonSelectAll: String
    val commonUnselectAll: String
    val commonNotSet: String
    val commonUnknown: String
    val commonAndMore: String
    val commonOptional: String
    val commonUnsaved: String
    val trayShowMainWindow: String
    val trayQuitApplication: String

    // Account Switch Dialog & Process
    val accountsSwitchDialogTitle: String
    val accountsSwitchSelectTargetTitle: String
    val accountsSwitchStatusIdeNotInstalled: String
    val accountsSwitchStatusIdeRunning: String
    val accountsSwitchStatusIdeStopped: String
    val accountsSwitchSharedTitleCli: String
    val accountsSwitchSharedTitleSystem: String
    val accountsSwitchStatusAppRunning: String
    val accountsSwitchStatusAppStopped: String
    val accountsSwitchStatusCliOnly: String
    val accountsSwitchStatusNone: String
    val accountsSwitchRememberChoice: String
    val accountsSwitching: String
    val accountsSwitchConfirmRestart: String
    val accountsSwitchConfirmLaunch: String
    val accountsSwitchConfirm: String
    val accountsSwitchTargetIde: String
    val accountsSwitchTargetAppCli: String

    // Smart Switch Dialog & Strategy
    val smartSwitchTitle: String
    val smartSwitchSubtitle: String
    val smartSwitchEnableTitle: String
    val smartSwitchEnableDesc: String
    val smartSwitchThresholdLabel: String
    val smartSwitchStrategyLabel: String
    val smartSwitchStrategyHighestQuota: String
    val smartSwitchStrategyRoundRobin: String
    val smartSwitchCooldownLabel: String
    fun smartSwitchSeconds(seconds: Int): String
    val smartSwitchProtectGenerationTitle: String
    val smartSwitchProtectGenerationDesc: String
    val smartSwitchInterruptTip: String
    val smartSwitchReasonDisabled: String
    fun smartSwitchReasonCooldown(remainingSec: Long): String
    val smartSwitchReasonWorkflowLocked: String
    val smartSwitchReasonNoBackupAccounts: String
    val smartSwitchReasonNoEligibleCandidate: String
    val smartSwitchTriggerReason429: String
    val smartSwitchTriggerReasonLowQuota: String
    fun smartSwitchReasonTaskRunning(trigger: String): String
    fun smartSwitchReasonSuggestSwitch(trigger: String, email: String): String
    val hotSwitchTaskAlreadyRunning: String
    val hotSwitchIdeNotApplied: String
    val hotSwitchNotAllTargetsApplied: String

    // Quota Auto-Refresh Dialog
    val quotaRefreshTitle: String
    val quotaRefreshSubtitle: String
    val quotaRefreshActiveIntervalTitle: String
    val quotaRefreshBackgroundIntervalTitle: String
    val quotaRefreshCustomOption: String
    val quotaRefreshPlaceholderActive: String
    val quotaRefreshPlaceholderBackground: String
    val quotaRefreshActiveHint: String
    val quotaRefreshDefaultSummary: String
    val quotaRefreshResetDefault: String
    val quotaRefreshUnitSecond: String
    val quotaRefreshUnitMinute: String
    val quotaRefreshUnitHour: String
    fun quotaRefreshPresetRecommended(label: String): String
    val quotaRefreshInputInvalid: String
    fun quotaRefreshMinActiveSeconds(sec: Int): String
    fun quotaRefreshMinBackgroundMinutes(min: Int): String
    fun quotaRefreshMaxHours(hr: Int): String

    // Account Cards & Screen
    fun accountsLastSyncTime(time: String): String
    val accountsSyncToOtherHost: String
    val accountsSetAsActiveTooltip: String
    val accountsRefreshThisTooltip: String
    val accountsRefreshingTooltip: String
    val accountsDeleteThisTooltip: String
    fun accountsModelFamily(label: String): String
    val accountsQuotaFiveHour: String
    val accountsQuotaWeekly: String
    val accountsFetchingQuota: String
    val accountsNoQuotaData: String
    val accountsQuotaResetSoon: String
    val accountsQuotaFull: String
    val accountsQuotaResetInSuffix: String

    // Time & Countdown formatting
    fun formatCountdownDaysHours(days: Long, hours: Long): String
    fun formatCountdownDays(days: Long): String
    fun formatCountdownHoursMinutes(hours: Long, minutes: Long): String
    fun formatCountdownHours(hours: Long): String
    fun formatCountdownMinutes(minutes: Long): String
    val formatCountdownLessThanMinute: String

    // Quota natural language descriptions
    val quotaDescFiveHourFull: String
    val quotaDescWeeklyFull: String
    val quotaDescGeneralFull: String
    fun quotaDescFiveHourResetting(timeStr: String): String
    fun quotaDescWeeklyResetting(timeStr: String): String
    fun quotaDescGeneralResetting(timeStr: String): String

    // Quota Window
    val quotaWindowFiveHour: String
    val quotaWindowWeekly: String
    val quotaWindowDaily: String
    val quotaWindowGeneral: String
    val accountsSearchPlaceholder: String
    val accountsSortByQuotaDesc: String
    val accountsSortByQuotaDescActive: String
    val accountsSortChipLabel: String
    val accountsAddAccountTooltip: String
    val accountsRefreshAllTooltip: String
    fun accountsAutoRefreshTooltip(activeSec: Int, bgMin: Int): String
    val accountsPrivacyHideTooltip: String
    val accountsPrivacyShowTooltip: String
    val accountsExportTooltip: String
    val accountsExportCopyToClipboard: String
    val accountsExportSaveJson: String
    fun accountsExportCopiedNotice(count: Int): String
    fun accountsExportSuccessNotice(count: Int, filename: String): String
    fun accountsExportFailedNotice(error: String): String
    val accountsExportDialogTitle: String
    val accountsSmartSwitchTooltip: String
    fun accountsSearchNoMatch(query: String): String
    val accountsDeleteConfirmTitle: String
    fun accountsDeleteConfirmMsg(email: String): String
    val accountsDeleteConfirmBtn: String

    // Overview Screen & Hero Card
    val overviewTodayRequests: String
    fun overviewRequestsUnit(count: Long): String
    val overviewServiceUptime: String
    val overviewAvgLatency: String
    val overviewRouteUpstreamStatus: String
    val overviewOfficialDirect: String
    fun overviewCustomUpstreamSummary(providerCount: Int, modelCount: Int): String
    fun overviewSourceInUse(sources: String): String
    val overviewActiveAccountBadge: String
    val overviewSyncingQuotas: String

    // Notices & ViewModel Messages
    val noticeAuthLinkCopied: String
    val noticeAuthLinkCopiedBrowser: String
    val noticeSwitchAlreadyRunning: String
    fun noticeSwitchResult(summary: String): String
    fun noticeSwitchFailed(error: String): String
    fun noticeAccountNotFound(idOrEmail: String): String
    val noticeSmartSwitchEnabled: String
    val noticeSmartSwitchDisabled: String
    val noticeQuotaAutoRefreshEnabled: String
    val noticeQuotaAutoRefreshDisabled: String
    val noticeAccountRemoved: String
    val noticeTokenRefreshed: String
    fun noticeTokenRefreshFailed(error: String): String
    val noticeRemarkUpdated: String
    fun noticeCleanAccountsSuccess(count: Int): String
    fun noticeCleanAccountsFailed(error: String): String
    fun noticeBatchImportSuccess(count: Int): String
    fun noticeBatchImportPartial(successCount: Int, failedCount: Int): String
    fun noticeBatchImportFailedAll(failedCount: Int): String
    val noticeQuotasUpdatedAll: String
    fun noticeQuotasUpdateFailedAll(error: String): String
    val noticeQuotaRefreshedSingle: String
    fun noticeQuotaRefreshFailedSingle(error: String): String
    fun switchStatusNotAvailable(target: String): String
    fun switchStatusConfigured(target: String): String
    fun switchStatusConfirmed(target: String): String
    fun switchStatusPendingRestart(target: String): String
    fun switchStatusFailed(target: String): String

    // Add Account Dialog & Import
    val accountsAddTabOAuth: String
    val accountsAddTabTokenImport: String
    val accountsAddSelectJsonFileTitle: String
    val accountsAddInvalidAuthCode: String
    val accountsAddReopenBrowser: String
    val accountsAddOpenBrowser: String
    val accountsAddCopyAuthUrl: String
    val accountsAddCancelAuth: String
    val accountsAddFallbackManualHint: String
    val accountsAddFallbackManualPlaceholder: String
    val accountsAddSubmit: String
    val accountsAddTokenBatchDesc: String
    val accountsAddImportJsonFile: String
    val accountsAddPasteClipboard: String
    val accountsAddTokenPlaceholder: String
    fun accountsAddRecognizedCount(count: Int, preview: String): String
    val accountsAddUnrecognizedTokens: String
    val accountsAddImporting: String
    fun accountsAddConfirmImport(count: Int): String

    // Other UI Components & Relative Times
    val settingsAccountAndAppCardTitle: String
    fun accountsCountSummary(count: Int): String
    val accountsFiveHourLabel: String
    val accountsWeeklyLabel: String
    fun accountsResetInCountdown(countdown: String): String
    val timeNeverRefreshed: String
    val timeJustNow: String
    fun timeMinutesAgo(min: Long): String
    fun timeHoursAgo(hours: Long): String
    fun timeDaysAgo(days: Long): String

    // Local Proxy Server Notices
    fun proxyStarted(port: Int): String
    fun proxyStartFailed(error: String): String
    val proxyStopped: String
    fun proxyRestarted(port: Int): String
    fun proxyRestartFailed(error: String): String
    fun proxyTestSuccess(latencyMs: Long): String
    fun proxyTestFailed(error: String): String
}

object StringsZh : Strings {
    override val appName = "Antigravity Studio"
    override val appSubtitle = "Antigravity 桌面智能代理与模型管理中枢"

    override val navOverview = "运行概览"
    override val navAccounts = "账号配额"
    override val navModels = "模型管理"
    override val navActivity = "调用日志"
    override val navSettings = "应用设置"
    override val navDoctor = "健康诊断"
    override val sidebarCollapse = "折叠侧边栏"
    override val sidebarExpand = "展开侧边栏"

    override val accountsTitle = "账号与配额管理"
    override val accountsSubtitle = "集中管理多个 Google 账号，实时监控配额余量并支持无缝一键切换"
    override val accountsAddAccount = "添加账号"
    override val accountsAddViaBrowser = "Google 浏览器登录"
    override val accountsAddViaToken = "手动输入 Token"
    override val accountsActiveInIde = "当前 IDE 生效账号"
    override val accountsSetActive = "设为当前账号"
    override val accountsDelete = "删除账号"
    override val accountsRefreshToken = "刷新凭据"
    override val accountsCopyToken = "复制凭据"
    override val accountsEmptyState = "暂无托管账号"
    override val accountsEmptyDesc = "点击右上角「添加账号」以登录 Google 账号或导入 Refresh Token"
    override val accountsTokenExpiringSoon = "凭据即将到期"
    override val accountsTokenExpired = "凭据已过期"
    override val accountsTokenHealthy = "有效"
    override val accountsExpiresIn = "后过期"
    override val accountsAddDialogTitle = "添加 Google 账号"
    override val accountsAddDialogBrowserDesc = "将在系统默认浏览器中打开 Google 授权页面，授权后自动返回并录入。"
    override val accountsAddDialogTokenDesc = "直接粘贴 Google OAuth Refresh Token 字符串，系统将自动换取并拉取用户资料。"
    override val accountsAddDialogTokenPlaceholder = "粘贴 Refresh Token (例如 1//0g...)"
    override val accountsWaitingBrowserAuth = "正在等待浏览器授权回调..."
    override val accountsAuthSuccess = "账号授权成功！"
    override val accountsAuthFailed = "账号授权失败"
    override val accountsCopiedEmail = "已复制账号邮箱"
    override fun accountsEmailTooltip(email: String) = "账号邮箱: $email (点击复制)"


    override val overviewProxyCardTitle = "本地代理服务"
    override val overviewProxyRunning = "运行中"
    override val overviewProxyStopped = "已停止"
    override val overviewProxyPort = "服务地址"
    override val overviewStartProxy = "启动代理"
    override val overviewStopProxy = "停止代理"
    override val overviewRestartProxy = "重启服务"
    override val overviewSubtitle = "统一管理本地代理、应用客户端接入与模型路由"
    override val overviewCopyAddress = "复制地址"
    override val overviewDiagnostics = "健康诊断"
    override val overviewProviderMetric = "自定义服务商"
    override val overviewModelMetric = "可用模型"
    override val overviewDisabledMetric = "隐藏官方模型"
    override val overviewHostSection = "应用客户端接入"
    override val overviewNotice = "切换接入模式后，请重启对应应用使配置生效。"
    override val overviewCopiedProxyAddress = "已复制代理地址"
    override val hostUpdateFailed = "应用接入配置失败，请检查配置文件权限"

    override val hostIdeTitle = "Antigravity IDE"
    override val hostIdeDesc = "接管 IDE 模型请求，通过 settings.json 注入本地代理并扩展模型能力"
    override val hostAppTitle = "Antigravity App"
    override val hostAppDesc = "接管桌面独立 App 模型请求，通过环境变量注入本地代理"
    override val hostCliTitle = "Antigravity CLI"
    override val hostCliDesc = "接管终端 CLI 命令，管理 CLOUD_CODE_URL 环境变量"

    override val hostStatusActive = "已接入"
    override val hostStatusInactive = "官方直连"
    override val hostStatusNotInstalled = "未安装"
    override val hostStatusReady = "已就绪"
    override val hostStatusInstalled = "已安装"
    override val hostStatusRunning = "运行中"
    override val hostStatusNeedsUpdate = "配置待更新"
    override val hostStatusMismatch = "代理端口不匹配"

    override val hostEnable = "接入代理"
    override val hostDisable = "恢复官方直连"
    override val hostRestartNotice = "切换后请重启对应应用以使配置完全生效"
    override val hostLaunch = "打开"
    override val hostRestart = "重启"
    override val hostUpdateAction = "更新配置"
    override val hostConfigurePath = "配置路径"
    override val hostForceReset = "重置为官方模式"
    override fun hostCustomPath(path: String) = "自定义路径: $path"
    override val hostProxyMode = "代理模式"

    override fun hostIdePortMismatch(endpoint: String) = "检测到代理配置与当前端口不一致（$endpoint）"
    override val hostIdeRunning = "Antigravity IDE 正在运行"
    override val hostIdeRunningAndConfigured = "Antigravity IDE 正在运行并已配置"
    override val hostIdeReady = "Antigravity IDE 已安装"
    override val hostIdeNotDetected = "未检测到 Antigravity IDE 安装目录"
    override fun hostIdePendingUpdate(port: Int) = "代理配置待更新为 http://127.0.0.1:$port"
    override val hostIdeActiveDesc = "已通过 settings.json 接入代理"
    override val hostOfficialDirectDesc = "当前处于官方直连模式"

    override fun hostAppPortMismatch(endpoint: String) = "检测到环境变量与当前端口不一致（$endpoint）"
    override val hostAppRunning = "Antigravity App 正在运行"
    override val hostAppRunningAndConfigured = "Antigravity App 正在运行并已配置"
    override val hostAppReady = "Antigravity App 已安装"
    override val hostAppNotDetected = "未检测到 Antigravity App 安装"
    override fun hostAppPendingUpdate(port: Int) = "环境变量待更新为 http://127.0.0.1:$port"
    override val hostAppActiveDesc = "已通过环境变量配置代理"

    override fun hostCliPortMismatch(endpoint: String) = "检测到 CLI 代理配置与当前端口不一致（$endpoint）"
    override val hostCliInstalledDesc = "Antigravity CLI (agy) 已安装"
    override val hostCliNotDetected = "未检测到 agy CLI 配置文件"
    override fun hostCliPendingUpdate(port: Int) = "CLI 配置待更新为 http://127.0.0.1:$port"
    override val hostCliActiveDesc = "CLI 配置文件代理接入生效中"
    override val hostCliOfficialDirectDesc = "CLI 当前处于官方直连模式"

    override val hostIdeUpdateConfirmTitle = "更新 Antigravity IDE 代理配置"
    override fun hostIdeUpdateConfirmMessageRunning(endpoint: String, port: Int) =
        "检测到 IDE 当前代理配置（$endpoint）与本地代理端口（$port）不匹配。更新后将自动重启 IDE 使配置生效。是否继续？"

    override fun hostIdeUpdateConfirmMessageStopped(endpoint: String, port: Int) =
        "检测到 IDE 当前代理配置（$endpoint）与本地代理端口（$port）不匹配。是否更新为当前代理端口？"

    override val hostIdeEnableConfirmTitle = "确认启用代理模式"
    override val hostIdeEnableConfirmMessageRunning =
        "启用代理模式后，Antigravity IDE 会接入配置的模型并自动重启使配置生效。是否继续？"
    override val hostIdeEnableConfirmMessageStopped = "启用代理模式将使 Antigravity IDE 在启动时连接本地代理。是否继续？"
    override val hostIdeDisableConfirmTitle = "确认停用代理接入"
    override val hostIdeDisableConfirmMessageRunning =
        "将停用 Antigravity IDE 的代理接入并重启恢复官方直连模式。是否继续？"
    override val hostIdeDisableConfirmMessageStopped = "将停用 Antigravity IDE 的代理接入，恢复官方直连模式。是否继续？"
    override val hostIdeUpdatedAndRestarted = "Antigravity IDE 代理配置已更新并完成重启"
    override val hostIdeEnabledAndRestarted = "Antigravity IDE 已启用代理模式并完成重启"
    override val hostIdeEnabledPendingStart = "Antigravity IDE 已启用代理模式，启动后生效"
    override val hostIdeConfigUpdatedRestartFailed = "Antigravity IDE 配置已更新，但自动重启失败"
    override val hostIdeEnableFailed = "Antigravity IDE 代理接入配置失败"
    override val hostIdeRestoredAndRestarted = "Antigravity IDE 已恢复官方直连并完成重启"
    override val hostIdeRestored = "Antigravity IDE 已恢复官方直连"
    override val hostIdeDisableFailed = "Antigravity IDE 停用代理接入失败"

    override val hostAppUpdateConfirmTitle = "更新 Antigravity App 代理配置"
    override fun hostAppUpdateConfirmMessageRunning(endpoint: String, port: Int) =
        "检测到 App 当前代理环境变量（$endpoint）与本地代理端口（$port）不匹配。更新后将自动重启 App 使配置生效。是否继续？"

    override fun hostAppUpdateConfirmMessageStopped(endpoint: String, port: Int) =
        "检测到 App 当前代理环境变量（$endpoint）与本地代理端口（$port）不匹配。是否更新为当前代理端口？"

    override val hostAppEnableConfirmTitle = "确认启用代理模式"
    override val hostAppEnableConfirmMessageRunning =
        "启用代理模式后，Antigravity App 会接入配置的模型并自动重启使配置生效。是否继续？"
    override val hostAppEnableConfirmMessageStopped = "启用代理模式将使 Antigravity App 在启动时连接本地代理。是否继续？"
    override val hostAppDisableConfirmTitle = "确认停用代理接入"
    override val hostAppDisableConfirmMessageRunning =
        "将停用 Antigravity App 的代理接入并重启恢复官方直连模式。是否继续？"
    override val hostAppDisableConfirmMessageStopped = "将停用 Antigravity App 的代理接入，恢复官方直连模式。是否继续？"
    override val hostAppUpdatedAndRestarted = "Antigravity App 代理配置已更新并完成重启"
    override val hostAppEnabledAndRestarted = "Antigravity App 已启用代理模式并完成重启"
    override val hostAppEnabledPendingStart = "Antigravity App 已启用代理模式，启动后生效"
    override val hostAppConfigUpdatedRestartFailed = "Antigravity App 配置已更新，但自动重启失败"
    override val hostAppEnableFailed = "Antigravity App 代理接入配置失败"
    override val hostAppRestoredAndRestarted = "Antigravity App 已恢复官方直连并完成重启"
    override val hostAppRestored = "Antigravity App 已恢复官方直连"
    override val hostAppDisableFailed = "Antigravity App 停用代理接入失败"
    override val hostAppNotInstalled = "未检测到 Antigravity App 安装"

    override val hostCliUpdateConfirmTitle = "更新 Antigravity CLI 代理配置"
    override fun hostCliUpdateConfirmMessage(endpoint: String, port: Int) =
        "检测到 CLI 当前代理配置（$endpoint）与本地代理端口（$port）不匹配。更新后请完全退出并重新打开终端应用生效。是否继续？"

    override val hostCliEnableConfirmTitle = "确认启用代理模式"
    override val hostCliEnableConfirmMessage =
        "启用代理模式后会在用户环境中配置 CLOUD_CODE_URL；完全退出并重新打开终端应用后生效。是否继续？"
    override val hostCliDisableConfirmTitle = "确认停用代理接入"
    override val hostCliDisableConfirmMessage =
        "将停用 CLI 的代理接入并恢复官方直连模式；完全退出并重新打开终端应用后生效。是否继续？"
    override val hostCliEnabledNotice = "CLI 已启用代理模式；请完全退出并重新打开终端应用"
    override val hostCliDisabledNotice = "CLI 代理接入已停用；请完全退出并重新打开终端应用"
    override val hostCliEnableFailed = "CLI 代理接入配置失败"
    override val hostCliDisableFailed = "CLI 停用代理接入失败"
    override val hostCliNotInstalled = "未检测到 agy CLI 安装"

    override fun hostStartProxyFirstNotice(hostName: String) = "请先启动本地代理服务，再接入 $hostName"
    override fun hostForceResetConfirmTitle(hostName: String) = "强制重置 $hostName 为官方直连"
    override fun hostForceResetConfirmMessage(hostName: String) =
        "此操作将清除 $hostName 的所有代理配置与环境变量，恢复为干净的官方直连模式。若应用正在运行将自动重启生效。是否确认重置？"

    override fun hostForceResetSuccess(hostName: String) = "$hostName 已强制重置为官方直连模式"
    override fun hostRestartConfirmTitle(hostName: String) = "确认重启 $hostName"
    override fun hostRestartConfirmMessage(hostName: String) =
        "确定要重启 $hostName 吗？重启将关闭当前运行中的实例并重新打开。是否继续？"

    override fun hostRestartSuccess(hostName: String) = "已重启 $hostName"
    override fun hostRestartFailed(hostName: String) = "重启 $hostName 失败"
    override fun hostLaunchSuccess(hostName: String) = "已打开 $hostName"
    override fun hostLaunchFailed(hostName: String) = "打开 $hostName 失败"
    override fun hostLaunchProxyNotRunning(hostName: String) = "当前 $hostName 已接入代理，请先启动本地代理服务"

    override fun hostPathDialogTitle(hostTitle: String) = "配置 $hostTitle 路径"
    override val hostPathDialogDesc =
        "未检测到默认安装时，可在此指定安装目录（如 .app 目录、安装文件夹）或主可执行文件的绝对路径。"
    override val hostPathInputLabel = "安装目录或可执行文件路径"
    override val hostPathStatusValid = "路径已检测到并存在于文件系统中"
    override val hostPathStatusNotFound = "该路径在当前文件系统中不存在，请确认路径无误"
    override val hostPathStatusEmpty = "留空保存将清除自定义配置，恢复为自动检测。"
    override val hostPathResetDefault = "重置为默认"
    override val hostPathSavedCustom = "已保存自定义路径并重新检测"
    override val hostPathResetNotice = "已重置为默认自动检测路径"
    override val hostPathBrowse = "选择路径 / 浏览..."
    override val hostPathSuggestedTitle = "推荐 / 发现的候选路径"
    override val hostPathSelectFile = "浏览"

    override val modelsTitle = "模型管理"
    override val modelsSubtitle = "统一管理 Google 官方模型与第三方模型服务，灵活配置上下文压缩与深度思考"
    override val modelsOfficialTab = "官方模型"
    override val modelsCustomTab = "自定义服务商"
    override val modelsOfficialTitle = "官方原生模型"
    override val modelsOfficialSubtitle = "管理 Antigravity 官方内置模型，支持按需隐藏或禁用"
    override val modelsCustomTitle = "自定义模型服务商"
    override val modelsAddProvider = "添加服务商"
    override val modelsEditProvider = "编辑服务商"
    override val modelsDeleteProvider = "删除服务商"
    override val modelsTestConnection = "测试连接"
    override val modelsFetchModels = "获取模型列表"
    override val modelsFetchingModels = "获取中…"
    override val modelsNoProviders = "暂无配置的模型服务商，点击右上角添加"
    override val modelsCompressionPolicy = "上下文策略"
    override val modelsReasoningConfig = "思考与推理"
    override val modelsCollapse = "收起"
    override val modelsExpand = "展开"
    override val modelsContext = "上下文"
    override val modelsVision = "多模态"
    override val modelsTools = "工具调用"
    override val modelsReasoning = "深度思考"
    override val modelsNoModels = "暂无模型"
    override val modelsTesting = "测试中…"
    override val modelsConnectionOk = "连通正常"
    override val modelsConnectionFailed = "连接失败"
    override val modelsRefreshOfficial = "刷新官方模型"
    override val modelsFetchingOfficial = "正在扫描探测语言服务并获取官方模型…"
    override fun modelsOfficialSyncFailed(error: String) = "官方模型同步失败：$error"
    override val modelsOfficialSyncing = "正在同步官方模型数据..."
    override val modelsOfficialSynced = "官方模型数据已同步"
    override val modelsOfficialWaitingSync = "等待同步官方模型数据"
    override val modelsRawJson = "原始 JSON"
    override val modelsModifiedJson = "修改后 JSON"
    override val modelsNoOfficialDetected = "当前未检测到官方模型"
    override val modelsNoOfficialHint = "请确认已在「运行概览」中打开 Antigravity IDE 或 App，随后点击「刷新」"
    override val modelsCopiedProviderUrl = "已复制服务地址"
    override val modelsCopiedJson = "已复制 JSON 数据"
    override fun modelsPassedCount(passed: Int, total: Int) = "$passed/$total 项通过"
    override fun modelsPassedWithFailed(passed: Int, total: Int, failed: Int) = "$passed/$total 项通过 ($failed 项失败)"
    override fun modelsRetryFailed(count: Int) = "重试失败项 ($count)"
    override val modelsBatchTest = "批量测试"
    override val modelsEditConfig = "编辑配置"
    override val modelsNoModelsHint = "该服务商尚未添加模型，点击「编辑配置」添加或拉取"
    override val modelsDeleteProviderConfirmTitle = "删除服务商"
    override fun modelsDeleteProviderConfirmMessage(providerName: String, modelCount: Int) =
        "确定要删除服务商「$providerName」吗？该服务商下的 $modelCount 个模型配置将被一并删除。"

    override val modelsDeleteModelConfirmTitle = "删除模型"
    override fun modelsDeleteModelConfirmMessage(modelName: String) = "确定要删除模型「$modelName」吗？"
    override val modelsRawJsonTitle = "官方模型原始 JSON 数据"
    override val modelsModifiedJsonTitle = "官方模型下发给 IDE 的 JSON 数据"
    override val modelsJsonData = "JSON 数据"
    override fun modelsPolicyCapacityWithPrep(limit: String, prep: String) = "$limit 容量 ($prep 预备)"
    override val modelsOfficialDefault = "官方默认"
    override val modelsVisionDesc = "多模态能力 (Multimodal)"
    override val modelsToolsDesc = "原生工具调用 (Tool Call)"
    override val modelsSpecsDesc = "查看模型规格与参数"
    override fun modelsTestSuccess(latency: String) = "测试成功 ($latency)"
    override val modelsTestFailed = "测试失败"
    override val modelsEnabledDesc = "已启用（点击禁用）"
    override val modelsDisabledDesc = "已禁用（点击启用）"
    override val modelsEditModel = "编辑模型配置"
    override val modelsDeleteModel = "删除此模型"
    override val modelsReasoningLevelLabel = "思考/推理等级"
    override val modelsCompressionPolicyLabel = "上下文策略"
    override val modelsEditPolicy = "编辑策略"
    override val modelsCopyJson = "复制 JSON"
    override val modelsNotSet = "未设置"

    override fun modelsProviderSaved(name: String) = "已保存服务商「$name」"
    override fun modelsProviderSaveFailed(error: String) = "保存服务商失败：$error"
    override fun modelsProviderDeleted(name: String) = "已删除服务商「$name」"
    override fun modelsProviderDeleteFailed(error: String) = "删除服务商失败：$error"
    override fun modelsModelDeleted(name: String) = "已删除模型「$name」"
    override fun modelsModelDeleteFailed(error: String) = "删除模型失败：$error"
    override fun modelsModelUpdated(name: String) = "已更新模型「$name」配置"
    override fun modelsModelUpdateFailed(error: String) = "更新模型失败：$error"
    override fun modelsModelTestSuccess(modelName: String, latencyMs: Long) = "$modelName 测试成功 (${latencyMs}ms)"
    override fun modelsModelTestFailed(modelName: String, error: String) = "$modelName 测试失败: $error"
    override fun modelsBatchTestSuccess(passed: Int, total: Int) = "服务商测试完成：$passed/$total 项测试通过"
    override fun modelsBatchTestPartial(passed: Int, total: Int, failed: Int) =
        "服务商测试完成：$passed/$total 项通过，${failed} 项失败"

    override val modelsProviderNotFound = "模型关联的服务商不存在"
    override fun modelsVirtualModelNotFound(id: String) = "模型不存在：$id"
    override val providerPresetCustomName = "自定义"
    override val providerPresetCustomDesc = "手动配置兼容 OpenAI、Anthropic 或 Google Gemini 协议的 API 服务"

    override val modelReasoningTitle = "深度思考与推理能力"
    override val modelReasoningDesc =
        "该模型支持深度思考/推理模式。在与 IDE 对话协同开发时，模型可开启思考链，深入分析复杂逻辑与架构："

    override fun modelReasoningLevel(level: String) = "档位: $level"
    override val modelReasoningHighDesc = "高预算思考 (适合极度复杂的算法与重构方案)"
    override val modelReasoningMediumDesc = "标准思考 (平衡推理深度与响应延迟)"
    override val modelReasoningLowDesc = "轻量思考 (快速给出思考结论)"
    override val modelReasoningAdaptiveDesc = "模型原生自适应深度思考"
    override val modelVisionTitle = "多模态输入支持"
    override val modelVisionDesc = "多模态能力允许模型直接理解视觉截图、设计图纸、架构图与代码引用："
    override val modelVisionImageTitle = "图像解析 (Vision)"
    override val modelVisionImageDesc = "支持上传 PNG / JPEG / WEBP 设计图、UI 报错截图进行直接分析"
    override val modelVisionDocTitle = "文档理解 (Document)"
    override val modelVisionDocDesc = "支持原生阅读 PDF / 文本规范文档并提取代码上下文"
    override val modelToolsFunctionTitle = "工具调用 (Function Calling)"
    override val modelToolsFunctionDesc = "支持 IDE 工具自动化执行与终端命令联动"
    override val modelSpecsTitle = "模型规格与元数据"
    override val modelSpecsId = "模型标识 (ID)"
    override val modelSpecsContextWindow = "上下文总窗口"
    override val modelSpecsDynamicConfig = "官方动态配置"
    override val modelSpecsMaxOutput = "单次最大输出"
    override val modelSpecsDefaultLimit = "官方默认限制"
    override val modelSpecsRoles = "分配角色"

    override val policyPresetDefault = "官方默认"
    override val policyPresetCustom = "自定义"
    override val policyRecommended = "推荐"
    override val policyDefaultDesc = "保持官方默认设置，遵循模型原生上下文与压缩策略。"
    override val policyCustomDesc = "可点击百分比快速设定，也可手动输入具体 Token 数值进行微调。"
    override val policyPresetDesc = "选择会话上下文容量：可直接使用预设值，或切换为自定义策略进行调整。"
    override val policyCompressorModel = "负责压缩的执行模型"
    override val policyFollowCurrent = "跟随当前模型"
    override val policyOfficialDefault = "官方默认"
    override val policyCheckpoint = "自动压缩点"
    override val policyContextLimit = "会话上下文容量"
    override val policyOutputReserve = "输出预留"
    override val policyByPercentage = "按百分比"
    override val policyExactTokens = "精准 Token"
    override val policyQuickPreset = "快捷预设"
    override val policyDistribution = "上下文容量分布"
    override val policyLegendNormal = "正常对话历史"
    override val policyLegendArchive = "自动压缩触发区"
    override val policyLegendUnused = "剩余未用容量"
    override fun policyModelContext(context: String) = "模型上下文 · $context"
    override val policyLimitMustPositive = "会话上下文容量必须大于 0"
    override val policyThresholdMustPositive = "自动压缩点必须大于 0"
    override val policyReserveMustPositive = "输出预留必须大于 0"
    override fun policyLimitExceedsContext(limit: String, context: String) =
        "会话上下文容量 ($limit) 不能超过模型上下文 ($context)"

    override fun policyLimitExceedsSafeLimit(limit: String, safeLimit: String, context: String, reserve: String) =
        "会话容量 ($limit) 超过了最大安全上限 ($safeLimit) [计算公式: 模型上下文 $context - 输出预留 $reserve]"

    override fun policyThresholdExceedsLimit(threshold: String, limit: String) =
        "自动压缩点 ($threshold) 必须小于会话上下文容量 ($limit)"

    override fun policySumExceedsLimit(sum: String, limit: String) =
        "自动压缩点与输出预留之和 ($sum) 超过了会话上下文容量 ($limit)"

    override val policyFormulaHint =
        "约束公式：触发压缩上限 (MaxTokenLimit) ≤ 模型上下文 (ContextWindow) - 最大输出预留 (OutputLimit)"
    override val policyFormulaHintDesc = "客户端要求输入上下文必须为模型生成预留足够空间，超出上限会导致对话异常中断。"

    override val reasoningDialogTitle = "配置深度思考"
    override val reasoningEnableTitle = "开启深度思考与推理 (Reasoning)"
    override val reasoningEnableSubtitle = "为当前模型配置推理档位与思考预算"
    override val reasoningAvailableLevels = "可用推理档位"
    override val reasoningCustomValue = "自定义推理参数 (Custom Reasoning)"
    override val reasoningOptional = "选填"
    override val reasoningCustomValueDesc = "如需使用服务商特有的推理档位参数，可在此手动输入覆盖"
    override val reasoningDefaultBudget = "默认思考预算 (选填)"
    override val reasoningDynamicBudgetPlaceholder = "-1 表示动态预算"
    override val reasoningMinBudgetTitle = "最小思考预算 (选填)"
    override fun reasoningExamplePlaceholder(example: String) = "例如 $example"
    override val reasoningBudget = "思考预算"
    override val reasoningMinBudget = "最小思考预算"
    override fun reasoningMustBeInteger(label: String) = "${label}必须是整数"
    override val reasoningGeminiBudgetValidation = "Gemini 思考预算只能为 -1、0 或正整数"
    override val reasoningMinBudgetMustPositive = "最小思考预算必须大于 0"
    override val reasoningMinBudgetExceedsBudget = "最小思考预算不能大于思考预算"
    override val reasoningOnlyGeminiSupportsBudget = "仅 Gemini 协议支持模型级思考预算"
    override val reasoningCustomValueInvalid = "自定义推理参数不符合当前协议或输出上限约束"
    override fun reasoningLevelInvalid(level: String) = "推理档位 $level 不符合当前协议或输出上限约束"
    override val reasoningSelectAtLeastOne = "请至少选择一个推理档位，或填写思考预算"

    override val providerPresetCategoryAll = "全部"
    override val providerPresetCategoryAggregator = "聚合平台"
    override val providerPresetCategoryRecommended = "常用推荐"
    override val providerPresetCategoryOfficial = "官方厂商"
    override val providerPresetCategoryLocalCustom = "本地/自定义"
    override val providerSearchPlaceholder = "搜索服务..."
    override val providerTagOfficial = "官方"
    override val providerTagAggregator = "聚合平台"
    override val providerTagLocal = "本地"
    override val providerTagCustom = "自定义"
    override val providerNameLabel = "服务商名称"
    override val providerNamePlaceholder = "例如 CLIProxyAPI、公司代理、DeepSeek"
    override val providerNameDesc = "自定义显示名称，用于在模型列表中识别服务商"
    override val providerProtocolLabel = "API 协议"
    override val providerProtocolOpenAIChatDesc = "适用于 /v1/chat/completions；CLIProxyAPI、Sub2API 及标准兼容网关"
    override val providerProtocolAnthropicDesc = "适用于 Anthropic 官方 /v1/messages 协议"
    override val providerProtocolGeminiDesc = "适用于 Google Gemini 官方 generateContent 协议"
    override val providerProtocolOpenAIResponsesDesc = "适用于 OpenAI Responses API 原生结构协议"
    override val providerBaseUrlLabel = "API 地址 (Base URL)"
    override val providerBaseUrlPlaceholder = "例如 https://api.openai.com/v1"
    override val providerBaseUrlDesc = "输入根地址后系统将自动推断补全模型列表与生成对话接口"
    override val providerApiKeyLabel = "API Key"
    override val providerApiKeyPlaceholder = "输入 API Key（无需鉴权可留空）"
    override val providerApiKeyDesc = "如服务无需鉴权可留空"
    override val providerAdvancedSettings = "高级设置（自定义 API 端点）"
    override val providerAdvancedAutoGenerated = "默认由 Base URL 自动推断生成"
    override val providerAdvancedCollapse = "收起"
    override val providerCustomModelsEndpoint = "模型列表接口 URL (自定义)"
    override val providerCustomCompletionsEndpoint = "生成对话接口 URL (自定义)"
    override val providerEndpointAutoInferPlaceholder = "留空自动推断"
    override val providerStepPreset = "选择预设"
    override val providerStepConnection = "网络与鉴权"
    override val providerStepModels = "选择可用模型"
    override val providerNextStep = "下一步"
    override val providerPrevStep = "上一步"
    override val providerSearchModelsPlaceholder = "搜索模型名称或 ID..."
    override fun providerFilterAll(count: Int) = "全部 ($count)"
    override fun providerFilterSelected(count: Int) = "已选 ($count)"
    override fun providerFilterUnselected(count: Int) = "未选 ($count)"
    override fun providerSelectAll(count: Int) = "全选 ($count)"
    override val providerUnselectAll = "取消全选"
    override val providerViewModelsResponse = "查看模型列表数据"
    override fun providerModelsResponseUnavailable(error: String) = "未能读取模型列表数据：$error"
    override fun providerNoModelsFound(query: String) = "未搜索到匹配「$query」的模型"
    override val providerNoModelsEmpty = "当前筛选下暂无模型"
    override fun providerTestLatency(latencyMs: Long) = "${latencyMs}ms"
    override val providerTestFailed = "失败"
    override val providerTestFailureDetailsTitle = "模型连通性测试失败"
    override val providerTestFailureStatusCode = "HTTP 状态码"
    override val providerTestFailureErrorDetails = "错误响应详情"
    override val providerTestFailureRetry = "重新测试"
    override val providerTestFailureCopy = "复制错误信息"
    override val providerTestFailureCopied = "已复制到剪贴板"
    override val providerTestFailureClose = "关闭"
    override val providerTokenLimitNotSet = "未设置"
    override fun providerCustomInputTokenTitle(model: String) = "自定义模型上下文上限 · $model"
    override fun providerCustomOutputTokenTitle(model: String) = "自定义最大输出 · $model"
    override val providerUnprobedCatalog = "未获取模型列表"
    override val providerTesting = "测试中"
    override val providerTestBtn = "测试"
    override fun providerInputTokenPrefix(label: String) = "模型上下文：$label"
    override fun providerOutputTokenPrefix(label: String) = "最大输出：$label"
    override val providerCustomTokenOption = "自定义数值..."
    override val providerClearTokenOption = "清除 (设为未设置)"
    override val providerCustomTokenDialogTitle = "自定义 Token 上限"
    override val providerCustomTokenPlaceholder = "例如：128K, 1M, 200000"
    override val providerCustomTokenHint = "支持 128K / 1M / 纯数字 等格式"
    override fun providerCustomTokenParsed(tokens: String) = "解析为: $tokens tokens"
    override val providerFetchFailedCheckUrlKey = "未能获取模型列表，请检查 API 地址与 API Key 是否正确"
    override fun providerFetchFailedWithError(error: String) = "获取模型列表失败: $error"
    override val providerDiscardConfirmTitle = "放弃未保存的更改？"
    override val providerDiscardConfirmMessage = "当前正在编辑的服务商配置尚未保存，退出后更改将丢失。"
    override val providerSkipFetchManualAdd = "跳过探测，手动配置模型"
    override val providerAddNewModel = "添加模型"
    override val providerManualAddModelTitle = "手动添加自定义模型"
    override val providerModelIdPlaceholder = "模型 ID (如 gpt-4o, claude-3-7-sonnet)"
    override val providerModelNamePlaceholder = "显示名称 (选填)"
    override val providerModelVendorPlaceholder = "厂商名称 (选填，如 OpenAI)"
    override val providerModelAlreadyExists = "该模型 ID 已在列表中"
    override val providerModelIdRequired = "模型 ID 不能为空"
    override val providerAddAndSelect = "添加并选中"
    override val providerNoModelsEmptyPrompt = "暂无模型，可点击上方「添加模型」手动录入"

    override val activityTitle = "调用日志"
    override val activitySubtitle = "查看请求状态、路由来源与响应耗时"
    override val activityFilterAll = "全部请求"
    override val activityFilterFailed = "仅看失败"
    override val activityClear = "清空日志"
    override val activityEmpty = "暂无请求日志"
    override val activityEmptyDesc = "当 Antigravity 发起模型代理调用时，此处将实时展示调用明细"
    override val activityNoMatchingLogs = "未找到匹配日志"
    override val activityNoMatchingDesc = "尝试输入其他关键词或清除筛选条件"
    override val activityPassthrough = "官方直连"
    override val activityRouted = "三方路由"
    override val activitySearchPlaceholder = "搜索模型或服务商名称..."
    override val activityRecent = "最近日志"
    override val activityTotal = "总请求量"
    override val activityFailedTotal = "异常请求"
    override val activityAverage = "平均耗时"
    override val activityCacheHitRate = "缓存命中率"
    override val activityFirstTokenLabel = "首字耗时"
    override val activityPending = "请求中"
    override val activityProcessing = "处理中..."
    override val activityAllTags = "日志筛选"
    override val activityTagFilterTitle = "日志筛选"
    override val activitySelectAll = "全选"
    override val activityClearFilter = "清空"
    override fun activitySelectedTagsCount(count: Int) = "已选 $count 项"
    override val activityTokenInput = "输入"
    override val activityTokenOutput = "输出"
    override val activityTokenCache = "缓存"
    override val activityTokenTotal = "总计"
    override val activityDetailCacheHitRate = "缓存命中率"
    override val activityAutoScroll = "自动滚动"
    override val activityInMemory = "内存日志"
    override val activityHealthy = "运行正常"
    override val activityHasErrors = "存在错误"
    override val activityUnknownProvider = "未知服务商"
    override val activityDetailTitle = "请求调用详情"
    override val activityDetailRouteSection = "路由与调用信息"
    override val activityDetailMethod = "请求方法"
    override val activityDetailPath = "完整请求路径"
    override val activityDetailDuration = "总响应耗时"
    override val activityDetailFirstToken = "首字响应耗时 (TTFT)"
    override val activityDetailTimestamp = "请求发起时间"
    override val activityDetailRouteMode = "路由模式"
    override val activityDetailPassthroughMode = "官方直连透传"
    override val activityDetailForwardMode = "三方服务商转发 (BYOK)"
    override val activityDetailTargetModel = "目标匹配模型"
    override val activityDetailRequestedModel = "原始请求模型"
    override val activityDetailProvider = "接入服务商"
    override val activityDetailTokenSection = "Token 消耗明细"
    override val activityDetailPromptTokens = "输入 (Prompt)"
    override val activityDetailCompletionTokens = "输出 (Completion)"
    override val activityDetailTotalTokens = "总计 (Total)"
    override val activityDetailReasoningTokens = "推理 (Thinking)"
    override val activityDetailCacheReadTokens = "缓存读取 (Read)"
    override val activityDetailCacheWriteTokens = "缓存写入 (Write)"
    override val activityDetailErrorSection = "错误详情与服务端原始响应"
    override val activityDetailCopyJson = "复制完整 JSON"
    override val activityDetailCopyError = "复制错误信息"
    override val activityDetailCopiedError = "已复制错误信息"
    override val activityRetryCount = "重试次数"
    override fun activityRetryBadge(count: Int) = "重试 $count 次"

    override val settingsTitle = "应用偏好与配置"
    override val settingsSubtitle = "管理语言、外观、代理端口与数据存储"
    override val settingsGeneral = "常规偏好"
    override val settingsNetwork = "网络代理"
    override val settingsData = "数据存储"
    override val settingsAboutSection = "关于应用"
    override val settingsLanguage = "界面语言"
    override val settingsLanguageDescription = "切换应用显示语言"
    override val settingsTheme = "外观主题"
    override val settingsThemeDescription = "选择系统、浅色或深色外观"
    override val settingsThemeSystem = "跟随系统"
    override val settingsThemeLight = "浅色模式"
    override val settingsThemeDark = "深色模式"
    override val settingsThemePalette = "配色方案"
    override val settingsThemePaletteDescription = "选择应用核心主题色系 (Material Design 3)"
    override val paletteIndigo = "极光靛蓝"
    override val paletteOcean = "深海青蓝"
    override val paletteEmerald = "自然翠绿"
    override val paletteViolet = "幻境紫罗兰"
    override val paletteRose = "活力珊瑚"
    override val paletteAmber = "晨曦金珀"
    override val settingsPort = "本地代理默认端口"
    override val settingsPortDescription = "修改后会重启本地代理服务"
    override val settingsPortInvalid = "端口必须在 1024 到 65535 之间"
    override fun settingsPortUpdated(port: Int) = "代理端口已更新为 $port"
    override fun settingsPortRestartFailed(error: String) = "代理端口更新后启动失败：$error"
    override fun settingsPortUpdateFailed(error: String) = "更新代理端口失败：$error"
    override val settingsHostPathsTitle = "应用安装路径"
    override val settingsHostPathsDesc = "自定义 Antigravity IDE、App 与 CLI 的安装目录或可执行文件路径"
    override val settingsDefaultSwitchTargetTitle = "切号默认目标应用"
    override val settingsDefaultSwitchTargetDesc = "配置在账号列表切换账号时，默认勾选生效的目标应用"
    override val settingsDefaultSwitchTargetAll = "全部应用 (推荐)"
    override val settingsDefaultSwitchTargetIdeOnly = "仅 IDE"
    override val settingsDefaultSwitchTargetAppCliOnly = "仅 App & CLI"
    override val settingsDefaultSwitchTargetRemember = "记住上次选择"
    override fun settingsHostPathCustom(title: String) = "$title: 自定义"
    override fun settingsHostPathAuto(title: String) = "$title: 自动"
    override val settingsStoragePath = "配置文件目录"
    override val settingsStorageDescription = "查看或备份本地持久化的服务商配置与策略数据"
    override val settingsOpenDirectory = "打开目录"
    override val settingsDirectoryOpenError = "目录打开失败"
    override val settingsUnsupportedPlatform = "当前平台不支持直接打开文件夹"
    override fun settingsOpenDirFailed(error: String) = "打开配置目录失败：$error"
    override val settingsAbout = "关于 Antigravity Studio"
    override val settingsAboutDescription =
        "基于 Kotlin Multiplatform 与 Compose Desktop 构建的 Antigravity 智能代理与模型接入中枢。"
    override val settingsVersion =
        "Antigravity Studio v${com.yuzhiqiang.antigravity.update.model.AppVersion.CURRENT} · Kotlin Multiplatform & Compose Desktop"
    override val settingsRepo = "开源仓库"
    override val settingsConfigDir = "配置目录"
    override val settingsOpenConfigDir = "打开数据与模型配置文件"
    override val settingsDeveloper = "开发者"
    override val settingsFeedback = "反馈建议"
    override val settingsFeedbackDesc = "提交 Issue 或加入交流群"

    // Update & Version Checker
    override val updateCheck = "检查更新"
    override val updateChecking = "正在检查更新..."
    override val updateUpToDate = "当前已是最新版本"
    override val updateAvailableTitle = "发现新版本"
    override fun updateAvailableSubtitle(version: String) =
        "Antigravity Studio $version 已发布，建议立即更新以获得更佳体验。"

    override val updateChangelogTitle = "更新日志"
    override val updateCurrentVersionLabel = "当前版本"
    override val updateLatestVersionLabel = "最新版本"
    override val updateDownloadNow = "立即下载"
    override val updateLater = "稍后提醒"
    override val updateIgnoreThisVersion = "跳过此版本"
    override val updateIgnoredNotice = "已忽略此版本的后续启动提醒"
    override fun updateCheckFailed(error: String) = "检查更新失败：$error"
    override val updateNoChangelog = "暂无详细发布说明。"
    override fun updateDownloadProgress(downloaded: String, total: String, percent: Int) =
        "$downloaded / $total ($percent%)"

    override fun updateDownloadSpeed(speed: String) = "$speed/s"
    override val updateDownloading = "正在下载更新…"
    override val updateDownloadCompleted = "下载完成，正在打开安装器…"
    override val updateInstallNow = "立即安装"
    override val updateShowInFolder = "打开文件位置"
    override fun updateDownloadFailed(error: String) = "下载失败：$error"
    override val updateRetryDownload = "重试下载"
    override val updateOpenInBrowser = "在浏览器中下载"
    override val updateCancelDownload = "取消"
    override val settingsAutoCheckUpdate = "启动时自动检查更新"
    override val settingsAutoCheckUpdateDesc = "应用启动时在后台静默检查是否有新版本，并在有更新时提醒"
    override val settingsCheckUpdateBtn = "检查更新"
    override val settingsCheckingUpdate = "正在检测..."
    override val settingsLatestVersionBadge = "最新版本"
    override val settingsNewVersionBadge = "可更新"
    override fun settingsLastChecked(time: String) = "上次检查：$time"
    override val settingsDeveloperMode = "开发者调试模式"
    override val settingsDeveloperModeDesc = "显示官方模型原始 JSON、修改后 JSON 等协议调试入口"
    override val settingsDeveloperModeEnabled = "已开启开发者调试模式"
    override val settingsDeveloperModeDisabled = "已关闭开发者调试模式"
    override val developerModeDialogTitle = "开发者调试模式"
    override val developerModeUnlockPrompt = "请输入密码确认开启开发者调试模式："
    override val developerModeTurnOn = "确认开启"
    override val developerModeTurnOff = "关闭开发者模式"
    override val developerModeWrongPassword = "密码错误，请重新输入"
    override val developerModeKeepEnabled = "保持开启"
    override val developerModeCancel = "取消"

    override val doctorTitle = "系统健康诊断"
    override val doctorSubtitle = "一键诊断网络连通性、本地配置、应用接入与代理服务健康状态"
    override val doctorRunAll = "重新诊断"
    override val doctorScanning = "诊断中..."
    override val doctorPassed = "正常"
    override val doctorFailed = "异常"
    override val doctorWarning = "警告"
    override val doctorDirect = "直连"
    override val doctorFixSuggestions = "修复建议"
    override val doctorDialogTitle = "系统健康诊断"
    override val doctorDialogSubtitle = "检测本地代理服务、模型服务商连通性与 Antigravity 应用接入状态"
    override val doctorBannerGood = "系统状态良好，各项配置已就绪"
    override val doctorBannerWarning = "部分配置需要处理"
    override val doctorBannerError = "检测到系统运行异常"
    override fun doctorBannerIssueCount(count: Int) = " • $count 项待处理"
    override fun doctorBannerStats(total: Int, passed: Int, issues: Int) =
        "共 $total 项检测 • $passed 项正常" + if (issues > 0) " • $issues 项待处理" else ""

    override fun doctorCheckedAt(time: String) = "诊断于 $time"
    override val doctorCategoryProxy = "本地代理服务"
    override val doctorCategoryNetwork = "Google 官方服务连通性"
    override val doctorCategoryConfig = "本地配置文件"
    override val doctorCategoryProvider = "模型服务商连通性"
    override val doctorCategoryHost = "Antigravity 应用客户端"
    override val doctorScanningStatus = "正在诊断系统环境..."
    override val doctorRealtimeStatus = "诊断结果已就绪"
    override val doctorScanningTitle = "正在执行系统健康诊断..."
    override val doctorScanningDesc = "逐项排查代理端口、模型服务商网络连通性与应用代理配置"
    override val doctorFixStartProxy = "启动代理"
    override val doctorFixGoConfigure = "去配置"
    override val doctorFixOneClickEnable = "一键接入"
    override val doctorFixUpdateConfig = "更新配置"
    override val doctorFixResetOfficial = "重置为官方直连"
    override val doctorFixRestartIde = "重启 IDE"
    override val doctorFixRestartApp = "重启 App"
    override val doctorFixPruneModels = "清理模型"
    override val doctorFixRetry = "重试"
    override val doctorSuggestionPrefix = "💡 建议: "
    override val doctorAutoFixSuccess = "已执行自动修复"
    override val doctorAutoFixFailed = "自动修复失败，请手动检查"

    override val doctorCheckProxyStoppedTitle = "本地代理服务未运行"
    override fun doctorCheckProxyStoppedMsg(port: Int) = "代理服务处于停止状态，无法拦截转发请求（配置端口：$port）。"
    override val doctorCheckProxyStoppedSugg = "请启动本地代理服务。"
    override val doctorCheckProxyOkTitle = "本地代理服务运行正常"
    override fun doctorCheckProxyOkMsg(port: Int) = "代理已就绪并正常监听 http://127.0.0.1:$port。"
    override val doctorCheckProxyUnreachableTitle = "本地代理端点无法连通"
    override fun doctorCheckProxyUnreachableMsg(port: Int) = "无法连接 127.0.0.1:$port，请检查端口占用或权限。"
    override val doctorCheckProxyUnreachableSugg = "尝试重启代理服务。"
    override val doctorCheckNetworkOkTitle = "连接官方服务"
    override fun doctorCheckNetworkOkMsg(latencyMs: Long) = "官方 Cloud Code 服务通信正常（${latencyMs}ms）。"
    override val doctorCheckNetworkFailedTitle = "连接官方服务失败"
    override fun doctorCheckNetworkFailedMsg(error: String) = "无法连通 Google 官方服务：$error。"
    override val doctorCheckNetworkFailedSugg =
        "请检查网络与代理配置；如直连正常但 Studio 仍失败，请重启 Studio 后重新检测。"
    override val doctorCheckNoProvidersTitle = "未配置或未启用任何服务商"
    override val doctorCheckNoProvidersMsg = "当前没有已启用的模型服务商，自定义模型请求将无法转发。"
    override val doctorCheckNoProvidersSugg = "前往「模型管理」添加服务商。"
    override fun doctorCheckProviderNoModelsTitle(provider: String) = "服务商「$provider」未配置模型"
    override val doctorCheckProviderNoModelsMsg = "该服务商已启用，但尚未添加任何可用模型。"
    override val doctorCheckProviderNoModelsSugg = "请在模型管理中配置可用模型。"
    override val doctorCheckIdeMismatchTitle = "Antigravity IDE 代理配置不匹配（待更新）"
    override fun doctorCheckIdeMismatchMsg(current: String, targetPort: Int) =
        "检测到 settings.json 中代理配置为「$current」，与当前代理服务端口「http://127.0.0.1:$targetPort」不一致，可能导致请求失败。"

    override val doctorCheckIdeMismatchSugg = "点击一键修复将更新为当前端口并自动重启生效，或重置为官方直连模式。"
    override val doctorCheckIdeRunningSuffix = "（IDE 正在运行）"
    override val doctorCheckIdeOkTitle = "Antigravity IDE 代理接入正常"
    override fun doctorCheckIdeOkMsg(port: Int, runningSuffix: String) =
        "settings.json 已正确配置为 http://127.0.0.1:$port $runningSuffix。"

    override val doctorCheckIdeOfficialTitle = "Antigravity IDE 使用官方模式（未接入代理）"
    override val doctorCheckIdeOfficialMsg =
        "当前直连 Google 官方服务，可正常使用。如需在 IDE 中使用自定义模型，可启用代理接入。"
    override val doctorCheckAppMismatchTitle = "Antigravity App 代理环境变量不匹配（待更新）"
    override fun doctorCheckAppMismatchMsg(current: String, targetPort: Int) =
        "检测到环境变量 CLOUD_CODE_URL 当前为「$current」，与当前代理服务端口「http://127.0.0.1:$targetPort」不一致。"

    override val doctorCheckAppMismatchSugg = "点击一键修复将更新环境变量并重启 App 生效，或重置为官方模式。"
    override val doctorCheckAppRunningSuffix = "（App 正在运行）"
    override val doctorCheckAppOkTitle = "Antigravity App 代理接入正常"
    override fun doctorCheckAppOkMsg(port: Int, runningSuffix: String) =
        "环境变量 CLOUD_CODE_URL 已正确配置为 http://127.0.0.1:$port $runningSuffix。"

    override val doctorCheckAppOfficialTitle = "Antigravity App 使用官方模式（未接入代理）"
    override val doctorCheckAppOfficialMsg = "当前直连 Google 官方服务。如需在 App 中使用自定义模型，可启用代理接入。"
    override val doctorCheckCliMismatchTitle = "Antigravity CLI 代理配置不匹配（待更新）"
    override fun doctorCheckCliMismatchMsg(current: String, targetPort: Int) =
        "检测到 CLI 代理配置为「$current」，与当前代理服务端口「http://127.0.0.1:$targetPort」不一致。"

    override val doctorCheckCliMismatchSugg = "点击一键修复更新为当前端口，或重置为官方模式。"
    override val doctorCheckCliOkTitle = "Antigravity CLI 代理接入正常"
    override fun doctorCheckCliOkMsg(port: Int) = "已在 CLI 配置文件中配置 cloud_code_url 为 http://127.0.0.1:$port。"
    override val doctorCheckCliOfficialTitle = "Antigravity CLI 使用官方模式（未接入代理）"
    override val doctorCheckCliOfficialMsg = "CLI 当前处于官方直连模式。"
    override fun doctorCheckProviderInvalidModelsTitle(provider: String) = "服务商「$provider」存在失效模型"
    override fun doctorCheckProviderInvalidModelsMsg(models: String) = "服务商当前未提供以下模型：$models。"
    override val doctorCheckProviderInvalidModelsSugg = "建议清理失效模型，避免请求因模型不存在而失败。"
    override fun doctorCheckProviderOkTitle(provider: String) = "服务商「$provider」连通正常"
    override fun doctorCheckProviderOkMsg(count: Int) = "鉴权成功，已配置的 $count 个模型均可用。"
    override fun doctorCheckProviderUnverifiedTitle(provider: String) = "服务商「$provider」已连通但无法获取模型列表"
    override val doctorCheckProviderUnverifiedMsg = "已成功连接服务商，但该端点未返回可解析的模型列表。"
    override val doctorCheckProviderUnverifiedSugg = "请确认模型列表接口已正确配置，或手动核对模型 ID。"

    override val commonSave = "保存配置"
    override val commonCancel = "取消"
    override val commonConfirm = "确认"
    override val commonDelete = "删除"
    override val commonEdit = "编辑"
    override val commonClose = "关闭"
    override val commonSuccess = "操作成功"
    override val commonError = "操作失败"
    override val commonCopied = "已复制到剪贴板"
    override val commonGotIt = "知道了"
    override val commonRefresh = "刷新"
    override val commonRetry = "重试"
    override val commonSearch = "搜索..."
    override val commonClear = "清除"
    override val commonSelectAll = "全选"
    override val commonUnselectAll = "取消全选"
    override val commonNotSet = "未设置"
    override val commonUnknown = "未知"
    override val commonAndMore = "等"
    override val commonOptional = "选填"
    override val commonUnsaved = "未保存"
    override val trayShowMainWindow = "显示主窗口"
    override val trayQuitApplication = "退出应用"

    // Account Switch Dialog & Process
    override val accountsSwitchDialogTitle = "切换账号"
    override val accountsSwitchSelectTargetTitle = "选择目标应用"
    override val accountsSwitchStatusIdeNotInstalled = "未安装 · 本机未检测到 IDE"
    override val accountsSwitchStatusIdeRunning = "运行中 · 选中后将安全退出并重启"
    override val accountsSwitchStatusIdeStopped = "未运行 · 选中后将写入凭据并直接启动"
    override val accountsSwitchSharedTitleCli = "Antigravity CLI (共享凭据)"
    override val accountsSwitchSharedTitleSystem = "系统共享凭据 (CLI / 本地)"
    override val accountsSwitchStatusAppRunning = "App 运行中 · 选中后将安全退出并重启"
    override val accountsSwitchStatusAppStopped = "App 未运行 · 选中后将写入凭据并直接启动"
    override val accountsSwitchStatusCliOnly = "未安装 App · 将同步 ~/.gemini/ 凭据供 CLI 使用"
    override val accountsSwitchStatusNone = "未检测到客户端 · 仅更新 Studio 活跃账号与共享凭据"
    override val accountsSwitchRememberChoice = "记住本次选择"
    override val accountsSwitching = "正在切换..."
    override val accountsSwitchConfirmRestart = "确定并重启"
    override val accountsSwitchConfirmLaunch = "确定并启动"
    override val accountsSwitchConfirm = "确定切换"
    override val accountsSwitchTargetIde = "Antigravity IDE"
    override val accountsSwitchTargetAppCli = "Antigravity App & CLI"

    // Smart Switch Dialog & Strategy
    override val smartSwitchTitle = "自动智能切号"
    override val smartSwitchSubtitle = "当配额耗尽或遇到 429 限流时，自动无缝切换至最佳备用账号"
    override val smartSwitchEnableTitle = "启用自动智能切号"
    override val smartSwitchEnableDesc = "遇到 429 限流或配额不足时自动切换账号"
    override val smartSwitchThresholdLabel = "触发切号的配额阈值"
    override val smartSwitchStrategyLabel = "备用账号选择策略"
    override val smartSwitchStrategyHighestQuota = "剩余配额最多优先 (推荐)"
    override val smartSwitchStrategyRoundRobin = "循环轮询"
    override val smartSwitchCooldownLabel = "两次切号最小间隔"
    override fun smartSwitchSeconds(seconds: Int) = "$seconds 秒"
    override val smartSwitchProtectGenerationTitle = "生成中防打断保护"
    override val smartSwitchProtectGenerationDesc = "在模型流式回复或智能体执行期间，暂缓自动切号"
    override val smartSwitchInterruptTip = "保护说明：模型流式生成或智能体正在执行时，暂缓切号以避免请求中断"
    override val smartSwitchReasonDisabled = "智能切号未启用"
    override fun smartSwitchReasonCooldown(remainingSec: Long) = "处于切号冷却期 (${remainingSec}s 剩余)"
    override val smartSwitchReasonWorkflowLocked = "当前工作流处于锁定保护状态"
    override val smartSwitchReasonNoBackupAccounts = "无可用备用账号"
    override val smartSwitchReasonNoEligibleCandidate = "未找到满足配额要求的备用账号"
    override val smartSwitchTriggerReason429 = "遭遇 429 配额耗尽"
    override val smartSwitchTriggerReasonLowQuota = "配额低于阈值"
    override fun smartSwitchReasonTaskRunning(trigger: String) = "$trigger，但当前已有切号任务正在执行"
    override fun smartSwitchReasonSuggestSwitch(trigger: String, email: String) =
        "$trigger，建议切换至 $email；请在账号管理中确认应用重启"

    override val hotSwitchTaskAlreadyRunning = "已有账号切换任务正在执行，请稍后再试"
    override val hotSwitchIdeNotApplied = "IDE 账号尚未生效"
    override val hotSwitchNotAllTargetsApplied = "账号尚未在所有目标应用生效"

    // Quota Auto-Refresh Dialog
    override val quotaRefreshTitle = "设置配额自动刷新频率"
    override val quotaRefreshSubtitle = "配置多账号配额后台自动同步频率（每个卡片左下角展示最后更新时间）"
    override val quotaRefreshActiveIntervalTitle = "当前活跃账号刷新间隔"
    override val quotaRefreshBackgroundIntervalTitle = "其他后台账号刷新间隔"
    override val quotaRefreshCustomOption = "自定义…"
    override val quotaRefreshPlaceholderActive = "例如 45"
    override val quotaRefreshPlaceholderBackground = "例如 15"
    override val quotaRefreshActiveHint = "提示：当前活跃账号的刷新间隔会直接影响配额更新及时性与自动切号时机。"
    override val quotaRefreshDefaultSummary = "默认：当前账号 1 分钟，其他账号 10 分钟"
    override val quotaRefreshResetDefault = "恢复默认"
    override val quotaRefreshUnitSecond = "秒"
    override val quotaRefreshUnitMinute = "分钟"
    override val quotaRefreshUnitHour = "小时"
    override fun quotaRefreshPresetRecommended(label: String) = "$label (推荐)"
    override val quotaRefreshInputInvalid = "请输入有效的刷新时间"
    override fun quotaRefreshMinActiveSeconds(sec: Int) = "最短刷新时间为 $sec 秒"
    override fun quotaRefreshMinBackgroundMinutes(min: Int) = "最短刷新时间为 $min 分钟"
    override fun quotaRefreshMaxHours(hr: Int) = "最长刷新时间为 $hr 小时"

    // Account Cards & Screen
    override fun accountsLastSyncTime(time: String) = "配额最后同步时间: $time"
    override val accountsSyncToOtherHost = "同步生效到其他客户端"
    override val accountsSetAsActiveTooltip = "设为当前生效账号"
    override val accountsRefreshThisTooltip = "刷新此账号实时配额"
    override val accountsRefreshingTooltip = "正在刷新配额..."
    override val accountsDeleteThisTooltip = "删除此账号"
    override fun accountsModelFamily(label: String) = "$label 模型"
    override val accountsQuotaFiveHour = "5 小时配额"
    override val accountsQuotaWeekly = "周配额"
    override val accountsFetchingQuota = "正在获取配额数据..."
    override val accountsNoQuotaData = "暂无数据"
    override val accountsQuotaResetSoon = "即将重置"
    override val accountsQuotaFull = "● 满额可用"
    override val accountsQuotaResetInSuffix = " 后重置"

    // Time & Countdown formatting
    override fun formatCountdownDaysHours(days: Long, hours: Long) = "${days}天 ${hours}小时"
    override fun formatCountdownDays(days: Long) = "${days}天"
    override fun formatCountdownHoursMinutes(hours: Long, minutes: Long) = "${hours}小时 ${minutes}分钟"
    override fun formatCountdownHours(hours: Long) = "${hours}小时"
    override fun formatCountdownMinutes(minutes: Long) = "${minutes}分钟"
    override val formatCountdownLessThanMinute = "< 1分钟"

    // Quota natural language descriptions
    override val quotaDescFiveHourFull = "您的五小时额度目前处于完全可用状态。"
    override val quotaDescWeeklyFull = "您的周额度目前处于完全可用状态。"
    override val quotaDescGeneralFull = "您的额度目前处于完全可用状态。"
    override fun quotaDescFiveHourResetting(timeStr: String) = "您已消耗部分五小时额度，将在 $timeStr 后完全重置。"
    override fun quotaDescWeeklyResetting(timeStr: String) = "您已消耗部分周额度，将在 $timeStr 后完全重置。"
    override fun quotaDescGeneralResetting(timeStr: String) = "额度将在 $timeStr 后完全重置。"

    // Quota Window
    override val quotaWindowFiveHour = "5 小时额度"
    override val quotaWindowWeekly = "周度额度"
    override val quotaWindowDaily = "每日额度"
    override val quotaWindowGeneral = "周期额度"
    override val accountsSearchPlaceholder = "按邮箱快速检索..."
    override val accountsSortByQuotaDesc = "按账号剩余配额从高到低排序"
    override val accountsSortByQuotaDescActive = "当前按剩余配额从高到低排序 (点击切换为默认排序)"
    override val accountsSortChipLabel = "按配额排序"
    override val accountsAddAccountTooltip = "添加单个 Refresh Token 或批量导入多个凭据"
    override val accountsRefreshAllTooltip = "立即并发刷新所有账号的最新配额数据"
    override fun accountsAutoRefreshTooltip(activeSec: Int, bgMin: Int) =
        "配置配额自动刷新频率 (当前: 活跃账号 $activeSec 秒 / 后台账号 $bgMin 分钟)"

    override val accountsPrivacyHideTooltip = "开启隐私脱敏，隐藏邮箱敏感字符"
    override val accountsPrivacyShowTooltip = "关闭脱敏，显示完整邮箱地址"
    override val accountsExportTooltip = "导出账号凭据 (支持复制到剪贴板或保存为 JSON 文件)"
    override val accountsExportCopyToClipboard = "复制到剪贴板"
    override val accountsExportSaveJson = "保存为 JSON 文件..."
    override fun accountsExportCopiedNotice(count: Int) = "已将 $count 个账号凭据复制到剪贴板"
    override fun accountsExportSuccessNotice(count: Int, filename: String) = "已成功导出 $count 个账号凭据至 $filename"
    override fun accountsExportFailedNotice(error: String) = "导出文件失败: $error"
    override val accountsExportDialogTitle = "保存账号凭据"
    override val accountsSmartSwitchTooltip = "配置配额不足或遇到 429 限流时的自动切号策略"
    override fun accountsSearchNoMatch(query: String) = "未找到与「$query」匹配的账号"
    override val accountsDeleteConfirmTitle = "删除账号"
    override fun accountsDeleteConfirmMsg(email: String) =
        "确定要从 Studio 移除账号「$email」吗？移除后将停止自动刷新与配额监控。"

    override val accountsDeleteConfirmBtn = "确定删除"

    // Overview Screen & Hero Card
    override val overviewTodayRequests = "今日请求总量"
    override fun overviewRequestsUnit(count: Long) = "$count 次"
    override val overviewServiceUptime = "服务正常率"
    override val overviewAvgLatency = "平均响应延迟"
    override val overviewRouteUpstreamStatus = "路由上游状态"
    override val overviewOfficialDirect = "官方默认直连"
    override fun overviewCustomUpstreamSummary(providerCount: Int, modelCount: Int) =
        "$providerCount 个服务商 · $modelCount 个模型"

    override fun overviewSourceInUse(sources: String) = "$sources 正在使用"
    override val overviewActiveAccountBadge = "当前生效账号"
    override val overviewSyncingQuotas = "正在同步配额数据..."

    // Notices & ViewModel Messages
    override val noticeAuthLinkCopied = "授权链接已复制到剪贴板"
    override val noticeAuthLinkCopiedBrowser = "授权链接已复制到剪贴板，请在浏览器中打开"
    override val noticeSwitchAlreadyRunning = "已有账号切换任务正在执行，请稍后再试"
    override fun noticeSwitchResult(summary: String) = "账号切换结果：$summary"
    override fun noticeSwitchFailed(error: String) = "切换账号失败: $error"
    override fun noticeAccountNotFound(idOrEmail: String) = "未找到账号: $idOrEmail"
    override val noticeSmartSwitchEnabled = "已启用自动智能切号"
    override val noticeSmartSwitchDisabled = "已停用自动智能切号"
    override val noticeQuotaAutoRefreshEnabled = "已更新配额自动刷新配置"
    override val noticeQuotaAutoRefreshDisabled = "已停用配额自动刷新"
    override val noticeAccountRemoved = "已移除账号"
    override val noticeTokenRefreshed = "账号凭据已成功刷新"
    override fun noticeTokenRefreshFailed(error: String) = "凭据刷新失败: $error"
    override val noticeRemarkUpdated = "已更新账号备注"
    override fun noticeCleanAccountsSuccess(count: Int) = "已清理 $count 个异常/过期账号"
    override fun noticeCleanAccountsFailed(error: String) = "清理失败: $error"
    override fun noticeBatchImportSuccess(count: Int) = "成功批量导入 $count 个账号"
    override fun noticeBatchImportPartial(successCount: Int, failedCount: Int) =
        "批量导入完成：成功 $successCount 个，已跳过 $failedCount 个无效 Token"

    override fun noticeBatchImportFailedAll(failedCount: Int) =
        "批量导入失败：所有输入的 $failedCount 个 Token 均已失效或被撤销"

    override val noticeQuotasUpdatedAll = "已更新所有账号配额数据"
    override fun noticeQuotasUpdateFailedAll(error: String) = "配额刷新异常: $error"
    override val noticeQuotaRefreshedSingle = "已刷新账号配额"
    override fun noticeQuotaRefreshFailedSingle(error: String) = "配额拉取失败: $error"
    override fun switchStatusNotAvailable(target: String) = "$target 当前不可用"
    override fun switchStatusConfigured(target: String) = "$target 已配置"
    override fun switchStatusConfirmed(target: String) = "$target 已生效"
    override fun switchStatusPendingRestart(target: String) = "$target 待确认重启"
    override fun switchStatusFailed(target: String) = "$target 未确认生效"

    // Add Account Dialog & Import
    override val accountsAddTabOAuth = "Google 浏览器登录 (OAuth)"
    override val accountsAddTabTokenImport = "Token / JSON 导入"
    override val accountsAddSelectJsonFileTitle = "选择账号备份 JSON 文件"
    override val accountsAddInvalidAuthCode = "未识别出有效授权码，请确认完整 URL"
    override val accountsAddReopenBrowser = "重新在浏览器打开"
    override val accountsAddOpenBrowser = "在浏览器中打开授权"
    override val accountsAddCopyAuthUrl = "复制授权链接"
    override val accountsAddCancelAuth = "取消授权"
    override val accountsAddFallbackManualHint = "若自动回调受阻，可将浏览器地址栏中的完整网址复制粘贴至下方："
    override val accountsAddFallbackManualPlaceholder = "http://127.0.0.1:41321/... 或授权码"
    override val accountsAddSubmit = "提交"
    override val accountsAddTokenBatchDesc =
        "支持粘贴单个/多行 Refresh Token（每行一个）、Cockpit 导出的 JSON 数组，或直接选择备份文件。"
    override val accountsAddImportJsonFile = "导入 JSON 文件"
    override val accountsAddPasteClipboard = "从剪贴板粘贴"
    override val accountsAddTokenPlaceholder = "粘贴 1//0g... 字符串（支持多行批量粘贴或 JSON 数组/对象）"
    override fun accountsAddRecognizedCount(count: Int, preview: String) = "已识别 $count 个有效账号凭据 $preview"
    override val accountsAddUnrecognizedTokens = "未能识别出有效的 Refresh Token 或 JSON 数据"
    override val accountsAddImporting = "正在验证并导入账号..."
    override fun accountsAddConfirmImport(count: Int) = when {
        count > 1 -> "批量导入账号 ($count 个)"
        count == 1 -> "确认导入账号 (1 个)"
        else -> "确认导入账号"
    }

    // Other UI Components & Relative Times
    override val settingsAccountAndAppCardTitle = "账号与应用设置"
    override fun accountsCountSummary(count: Int) = "$count 个账号"
    override val accountsFiveHourLabel = "5小时"
    override val accountsWeeklyLabel = "周"
    override fun accountsResetInCountdown(countdown: String) = "$countdown 后重置"
    override val timeNeverRefreshed = "从未刷新"
    override val timeJustNow = "刚刚更新"
    override fun timeMinutesAgo(min: Long) = "上次刷新 $min 分钟前"
    override fun timeHoursAgo(hours: Long) = "上次刷新 $hours 小时前"
    override fun timeDaysAgo(days: Long) = "上次刷新 $days 天前"

    override fun proxyStarted(port: Int) = "本地代理已启动 ($port)"
    override fun proxyStartFailed(error: String) = "本地代理启动失败：$error"
    override val proxyStopped = "本地代理已停止"
    override fun proxyRestarted(port: Int) = "本地代理已重启 ($port)"
    override fun proxyRestartFailed(error: String) = "本地代理重启失败：$error"
    override fun proxyTestSuccess(latencyMs: Long) = "代理连接测试成功 (${latencyMs}ms)"
    override fun proxyTestFailed(error: String) = "代理连接测试失败：$error"
}

object StringsEn : Strings {
    override val appName = "Antigravity Studio"
    override val appSubtitle = "The all-in-one desktop hub and BYOK model suite for Antigravity"

    override val navOverview = "Overview"
    override val navAccounts = "Accounts"
    override val navModels = "Models"
    override val navActivity = "Activity"
    override val navSettings = "Settings"
    override val navDoctor = "Doctor"
    override val sidebarCollapse = "Collapse sidebar"
    override val sidebarExpand = "Expand sidebar"

    override val accountsTitle = "Accounts & Quota"
    override val accountsSubtitle = "Manage multiple accounts, monitor AI quotas, and switch seamlessly"
    override val accountsAddAccount = "Add Account"
    override val accountsAddViaBrowser = "Google Sign In"
    override val accountsAddViaToken = "Manual Token"
    override val accountsActiveInIde = "Active in IDE"
    override val accountsSetActive = "Set Active"
    override val accountsDelete = "Delete"
    override val accountsRefreshToken = "Refresh Token"
    override val accountsCopyToken = "Copy Token"
    override val accountsEmptyState = "No accounts configured"
    override val accountsEmptyDesc = "Click 'Add Account' above to sign in or import a Refresh Token"
    override val accountsTokenExpiringSoon = "Expiring soon"
    override val accountsTokenExpired = "Expired"
    override val accountsTokenHealthy = "Active"
    override val accountsExpiresIn = "expires in"
    override val accountsAddDialogTitle = "Add Google Account"
    override val accountsAddDialogBrowserDesc =
        "Opens Google authorization in your default browser and automatically captures the token."
    override val accountsAddDialogTokenDesc =
        "Paste a Google OAuth Refresh Token. Studio will fetch user profile and tokens automatically."
    override val accountsAddDialogTokenPlaceholder = "Paste Refresh Token (e.g. 1//0g...)"
    override val accountsWaitingBrowserAuth = "Waiting for browser authorization..."
    override val accountsAuthSuccess = "Account authorized successfully!"
    override val accountsAuthFailed = "Authorization failed"
    override val accountsCopiedEmail = "Account email copied to clipboard"
    override fun accountsEmailTooltip(email: String) = "Account email: $email (Click to copy)"


    override val overviewProxyCardTitle = "Local Proxy Server"
    override val overviewProxyRunning = "Running"
    override val overviewProxyStopped = "Stopped"
    override val overviewProxyPort = "Listening Address"
    override val overviewStartProxy = "Start Proxy"
    override val overviewStopProxy = "Stop Proxy"
    override val overviewRestartProxy = "Restart Server"
    override val overviewSubtitle = "Manage the local proxy, host integrations and model routes"
    override val overviewCopyAddress = "Copy address"
    override val overviewDiagnostics = "Run Diagnostics"
    override val overviewProviderMetric = "Custom providers"
    override val overviewModelMetric = "Available models"
    override val overviewDisabledMetric = "Hidden official"
    override val overviewHostSection = "Host integrations"
    override val overviewNotice = "Restart the host application after changing an integration."
    override val overviewCopiedProxyAddress = "Proxy address copied to clipboard"
    override val hostUpdateFailed = "Host integration failed; check settings file permissions"

    override val hostIdeTitle = "Antigravity IDE"
    override val hostIdeDesc = "Intercept IDE requests via settings.json cloudCodeUrl"
    override val hostAppTitle = "Antigravity App"
    override val hostAppDesc = "Intercept App requests via session environment variable"
    override val hostCliTitle = "Antigravity CLI"
    override val hostCliDesc = "Intercept CLI commands via user CLOUD_CODE_URL"

    override val hostStatusActive = "Active"
    override val hostStatusInactive = "Official Direct"
    override val hostStatusNotInstalled = "Not Detected"
    override val hostStatusReady = "Ready"
    override val hostStatusInstalled = "Installed"
    override val hostStatusRunning = "Running"
    override val hostStatusNeedsUpdate = "Needs Update"
    override val hostStatusMismatch = "Port Mismatch"

    override val hostEnable = "Enable Proxy"
    override val hostDisable = "Restore Official"
    override val hostRestartNotice = "Please restart host application to take full effect"
    override val hostLaunch = "Launch"
    override val hostRestart = "Restart"
    override val hostUpdateAction = "Update Config"
    override val hostConfigurePath = "Configure Path"
    override val hostForceReset = "Reset to Official"
    override fun hostCustomPath(path: String) = "Custom path: $path"
    override val hostProxyMode = "Proxy Mode"

    override fun hostIdePortMismatch(endpoint: String) = "Proxy config differs from current port ($endpoint)"
    override val hostIdeRunning = "Antigravity IDE is running"
    override val hostIdeRunningAndConfigured = "Antigravity IDE is running and configured"
    override val hostIdeReady = "Antigravity IDE is installed"
    override val hostIdeNotDetected = "Antigravity IDE installation not detected"
    override fun hostIdePendingUpdate(port: Int) = "Proxy config pending update to http://127.0.0.1:$port"
    override val hostIdeActiveDesc = "settings.json proxy integration active"
    override val hostOfficialDirectDesc = "Currently using official direct mode"

    override fun hostAppPortMismatch(endpoint: String) = "Environment variable differs from current port ($endpoint)"
    override val hostAppRunning = "Antigravity App is running"
    override val hostAppRunningAndConfigured = "Antigravity App is running and configured"
    override val hostAppReady = "Antigravity App is installed"
    override val hostAppNotDetected = "Antigravity App installation not detected"
    override fun hostAppPendingUpdate(port: Int) = "Environment variable pending update to http://127.0.0.1:$port"
    override val hostAppActiveDesc = "CLOUD_CODE_URL environment proxy active"

    override fun hostCliPortMismatch(endpoint: String) = "CLI proxy config differs from current port ($endpoint)"
    override val hostCliInstalledDesc = "Antigravity CLI (agy) is installed"
    override val hostCliNotDetected = "agy CLI config file not detected"
    override fun hostCliPendingUpdate(port: Int) = "CLI config pending update to http://127.0.0.1:$port"
    override val hostCliActiveDesc = "CLI config proxy integration active"
    override val hostCliOfficialDirectDesc = "CLI currently in official direct mode"

    override val hostIdeUpdateConfirmTitle = "Update Antigravity IDE Proxy Config"
    override fun hostIdeUpdateConfirmMessageRunning(endpoint: String, port: Int) =
        "Detected IDE proxy endpoint ($endpoint) differs from local proxy port ($port). Updating will restart IDE to apply changes. Continue?"

    override fun hostIdeUpdateConfirmMessageStopped(endpoint: String, port: Int) =
        "Detected IDE proxy endpoint ($endpoint) differs from local proxy port ($port). Update to current proxy port?"

    override val hostIdeEnableConfirmTitle = "Enable Proxy Mode for IDE"
    override val hostIdeEnableConfirmMessageRunning =
        "Enabling proxy mode will inject configured models and restart Antigravity IDE to apply changes. Continue?"
    override val hostIdeEnableConfirmMessageStopped =
        "Enabling proxy mode will configure Antigravity IDE to connect to the local proxy when started. Continue?"
    override val hostIdeDisableConfirmTitle = "Disable Proxy Mode for IDE"
    override val hostIdeDisableConfirmMessageRunning =
        "Disabling proxy mode will restore official direct connection and restart Antigravity IDE. Continue?"
    override val hostIdeDisableConfirmMessageStopped =
        "Disabling proxy mode will restore official direct connection for Antigravity IDE. Continue?"
    override val hostIdeUpdatedAndRestarted = "Antigravity IDE proxy config updated and restarted"
    override val hostIdeEnabledAndRestarted = "Antigravity IDE proxy mode enabled and restarted"
    override val hostIdeEnabledPendingStart = "Antigravity IDE proxy mode enabled; will apply on launch"
    override val hostIdeConfigUpdatedRestartFailed = "Antigravity IDE config updated, but auto-restart failed"
    override val hostIdeEnableFailed = "Failed to configure Antigravity IDE proxy integration"
    override val hostIdeRestoredAndRestarted = "Antigravity IDE restored to official direct mode and restarted"
    override val hostIdeRestored = "Antigravity IDE restored to official direct mode"
    override val hostIdeDisableFailed = "Failed to disable proxy integration for Antigravity IDE"

    override val hostAppUpdateConfirmTitle = "Update Antigravity App Proxy Config"
    override fun hostAppUpdateConfirmMessageRunning(endpoint: String, port: Int) =
        "Detected App proxy environment ($endpoint) differs from local proxy port ($port). Updating will restart App to apply changes. Continue?"

    override fun hostAppUpdateConfirmMessageStopped(endpoint: String, port: Int) =
        "Detected App proxy environment ($endpoint) differs from local proxy port ($port). Update to current proxy port?"

    override val hostAppEnableConfirmTitle = "Enable Proxy Mode for App"
    override val hostAppEnableConfirmMessageRunning =
        "Enabling proxy mode will inject configured models and restart Antigravity App to apply changes. Continue?"
    override val hostAppEnableConfirmMessageStopped =
        "Enabling proxy mode will configure Antigravity App to connect to the local proxy when started. Continue?"
    override val hostAppDisableConfirmTitle = "Disable Proxy Mode for App"
    override val hostAppDisableConfirmMessageRunning =
        "Disabling proxy mode will restore official direct connection and restart Antigravity App. Continue?"
    override val hostAppDisableConfirmMessageStopped =
        "Disabling proxy mode will restore official direct connection for Antigravity App. Continue?"
    override val hostAppUpdatedAndRestarted = "Antigravity App proxy config updated and restarted"
    override val hostAppEnabledAndRestarted = "Antigravity App proxy mode enabled and restarted"
    override val hostAppEnabledPendingStart = "Antigravity App proxy mode enabled; will apply on launch"
    override val hostAppConfigUpdatedRestartFailed = "Antigravity App config updated, but auto-restart failed"
    override val hostAppEnableFailed = "Failed to configure Antigravity App proxy integration"
    override val hostAppRestoredAndRestarted = "Antigravity App restored to official direct mode and restarted"
    override val hostAppRestored = "Antigravity App restored to official direct mode"
    override val hostAppDisableFailed = "Failed to disable proxy integration for Antigravity App"
    override val hostAppNotInstalled = "Antigravity App not detected"

    override val hostCliUpdateConfirmTitle = "Update Antigravity CLI Proxy Config"
    override fun hostCliUpdateConfirmMessage(endpoint: String, port: Int) =
        "Detected CLI proxy config ($endpoint) differs from local proxy port ($port). Please restart your terminal application after updating. Continue?"

    override val hostCliEnableConfirmTitle = "Enable Proxy Mode for CLI"
    override val hostCliEnableConfirmMessage =
        "Enabling proxy mode will configure CLOUD_CODE_URL in your user environment; restart your terminal to apply. Continue?"
    override val hostCliDisableConfirmTitle = "Disable Proxy Mode for CLI"
    override val hostCliDisableConfirmMessage =
        "Disabling proxy mode will restore official direct connection; restart your terminal to apply. Continue?"
    override val hostCliEnabledNotice = "CLI proxy mode enabled; please restart your terminal application"
    override val hostCliDisabledNotice = "CLI proxy mode disabled; please restart your terminal application"
    override val hostCliEnableFailed = "Failed to configure CLI proxy integration"
    override val hostCliDisableFailed = "Failed to disable CLI proxy integration"
    override val hostCliNotInstalled = "agy CLI not detected"

    override fun hostStartProxyFirstNotice(hostName: String) =
        "Please start the local proxy server before integrating with $hostName"

    override fun hostForceResetConfirmTitle(hostName: String) = "Force Reset $hostName to Official Mode"
    override fun hostForceResetConfirmMessage(hostName: String) =
        "This will forcefully clear all proxy settings, environment variables and receipts for $hostName to restore clean official direct mode. The application will be restarted if running. Continue?"

    override fun hostForceResetSuccess(hostName: String) = "$hostName has been reset to official direct mode"
    override fun hostRestartConfirmTitle(hostName: String) = "Confirm Restart $hostName"
    override fun hostRestartConfirmMessage(hostName: String) =
        "Are you sure you want to restart $hostName? This will close running instances and launch a new process. Continue?"

    override fun hostRestartSuccess(hostName: String) = "Restarted $hostName"
    override fun hostRestartFailed(hostName: String) = "Failed to restart $hostName"
    override fun hostLaunchSuccess(hostName: String) = "Launched $hostName"
    override fun hostLaunchFailed(hostName: String) = "Failed to launch $hostName"
    override fun hostLaunchProxyNotRunning(hostName: String) =
        "$hostName is configured for proxy mode; please start the local proxy first"

    override fun hostPathDialogTitle(hostTitle: String) = "Configure $hostTitle Path"
    override val hostPathDialogDesc =
        "When auto-detection fails, enter the custom installation directory (e.g. .app bundle or install folder) or main executable path."
    override val hostPathInputLabel = "Installation Directory or Executable Path"
    override val hostPathStatusValid = "Path detected and exists on filesystem"
    override val hostPathStatusNotFound = "Path does not exist on filesystem; please check the path"
    override val hostPathStatusEmpty =
        "Leaving empty and saving will clear custom configuration and restore system auto-detection."
    override val hostPathResetDefault = "Reset to Default"
    override val hostPathSavedCustom = "Custom path configured; re-scanning host"
    override val hostPathResetNotice = "Reset to default auto-detection path"
    override val hostPathBrowse = "Browse..."
    override val hostPathSuggestedTitle = "Detected candidate paths"
    override val hostPathSelectFile = "Browse"

    override val modelsTitle = "Models"
    override val modelsSubtitle =
        "Orchestrate Google official models & custom BYOK upstream providers with context compression and reasoning budgets"
    override val modelsOfficialTab = "Official"
    override val modelsCustomTab = "Custom Services"
    override val modelsOfficialTitle = "Official Native Models"
    override val modelsOfficialSubtitle = "Manage built-in Antigravity models, disable unwanted models"
    override val modelsCustomTitle = "Custom Model Providers"
    override val modelsAddProvider = "Add Upstream Provider"
    override val modelsEditProvider = "Edit Provider"
    override val modelsDeleteProvider = "Delete Provider"
    override val modelsTestConnection = "Test Connection"
    override val modelsFetchModels = "Fetch Models"
    override val modelsFetchingModels = "Fetching…"
    override val modelsNoProviders = "No providers configured. Click top right to add."
    override val modelsCompressionPolicy = "Compression Policy"
    override val modelsReasoningConfig = "Reasoning Config"
    override val modelsCollapse = "Collapse"
    override val modelsExpand = "Expand"
    override val modelsContext = "Context"
    override val modelsVision = "Multimodal"
    override val modelsTools = "Tools"
    override val modelsReasoning = "Reasoning"
    override val modelsNoModels = "No models"
    override val modelsTesting = "Testing…"
    override val modelsConnectionOk = "Connected"
    override val modelsConnectionFailed = "Connection failed"
    override val modelsRefreshOfficial = "Refresh Official Models"
    override val modelsFetchingOfficial = "Probing language server and fetching official models…"
    override fun modelsOfficialSyncFailed(error: String) = "Official model sync failed: $error"
    override val modelsOfficialSyncing = "Syncing official model catalog..."
    override val modelsOfficialSynced = "Official model catalog synchronized"
    override val modelsOfficialWaitingSync = "Waiting to sync official model catalog"
    override val modelsRawJson = "Raw JSON"
    override val modelsModifiedJson = "Modified JSON"
    override val modelsNoOfficialDetected = "No official models detected"
    override val modelsNoOfficialHint = "Ensure Antigravity IDE or App is opened in Overview, then click Refresh"
    override val modelsCopiedProviderUrl = "Provider endpoint copied to clipboard"
    override val modelsCopiedJson = "JSON data copied to clipboard"
    override fun modelsPassedCount(passed: Int, total: Int) = "$passed/$total passed"
    override fun modelsPassedWithFailed(passed: Int, total: Int, failed: Int) = "$passed/$total passed ($failed failed)"
    override fun modelsRetryFailed(count: Int) = "Retry Failed ($count)"
    override val modelsBatchTest = "Batch Test"
    override val modelsEditConfig = "Edit Config"
    override val modelsNoModelsHint = "No models configured for this provider. Click Edit Config to add or fetch."
    override val modelsDeleteProviderConfirmTitle = "Delete Provider"
    override fun modelsDeleteProviderConfirmMessage(providerName: String, modelCount: Int) =
        "Are you sure you want to delete provider \"$providerName\"? All $modelCount associated models will be removed."

    override val modelsDeleteModelConfirmTitle = "Delete Model"
    override fun modelsDeleteModelConfirmMessage(modelName: String) =
        "Are you sure you want to delete model \"$modelName\"?"

    override val modelsRawJsonTitle = "Official Models Raw JSON Data"
    override val modelsModifiedJsonTitle = "Modified Official Models (Injected to IDE) JSON Data"
    override val modelsJsonData = "JSON Data"
    override fun modelsPolicyCapacityWithPrep(limit: String, prep: String) = "$limit capacity ($prep reserve)"
    override val modelsOfficialDefault = "Official Default"
    override val modelsVisionDesc = "Multimodal Capability"
    override val modelsToolsDesc = "Native Tool / Function Calling"
    override val modelsSpecsDesc = "View Model Specifications"
    override fun modelsTestSuccess(latency: String) = "Test Succeeded ($latency)"
    override val modelsTestFailed = "Test Failed"
    override val modelsEnabledDesc = "Enabled (Click to disable)"
    override val modelsDisabledDesc = "Disabled (Click to enable)"
    override val modelsEditModel = "Edit Model Config"
    override val modelsDeleteModel = "Delete Model"
    override val modelsReasoningLevelLabel = "Reasoning Level"
    override val modelsCompressionPolicyLabel = "Compression Policy"
    override val modelsEditPolicy = "Edit Policy"
    override val modelsCopyJson = "Copy JSON"
    override val modelsNotSet = "Not Set"

    override fun modelsProviderSaved(name: String) = "Saved provider \"$name\""
    override fun modelsProviderSaveFailed(error: String) = "Failed to save provider: $error"
    override fun modelsProviderDeleted(name: String) = "Deleted provider \"$name\""
    override fun modelsProviderDeleteFailed(error: String) = "Failed to delete provider: $error"
    override fun modelsModelDeleted(name: String) = "Deleted model \"$name\""
    override fun modelsModelDeleteFailed(error: String) = "Failed to delete model: $error"
    override fun modelsModelUpdated(name: String) = "Updated model \"$name\" configuration"
    override fun modelsModelUpdateFailed(error: String) = "Failed to update model: $error"
    override fun modelsModelTestSuccess(modelName: String, latencyMs: Long) =
        "$modelName test succeeded (${latencyMs}ms)"

    override fun modelsModelTestFailed(modelName: String, error: String) = "$modelName test failed: $error"
    override fun modelsBatchTestSuccess(passed: Int, total: Int) = "Provider test complete: $passed/$total tests passed"
    override fun modelsBatchTestPartial(passed: Int, total: Int, failed: Int) =
        "Provider test complete: $passed/$total passed, $failed failed"

    override val modelsProviderNotFound = "Associated provider not found for this model"
    override fun modelsVirtualModelNotFound(id: String) = "VirtualModel not found: $id"
    override val providerPresetCustomName = "Custom"
    override val providerPresetCustomDesc = "Manually configure any OpenAI-compatible, Anthropic or Gemini service"

    override val modelReasoningTitle = "Deep Thinking & Reasoning"
    override val modelReasoningDesc =
        "This model supports deep thinking / reasoning chains. During collaborative sessions with the IDE, the model can reason through complex architecture and logic:"

    override fun modelReasoningLevel(level: String) = "Level: $level"
    override val modelReasoningHighDesc = "High budget thinking (Best for complex algorithms & large refactoring)"
    override val modelReasoningMediumDesc = "Standard thinking (Balanced reasoning depth and latency)"
    override val modelReasoningLowDesc = "Lightweight thinking (Fast response with concise reasoning)"
    override val modelReasoningAdaptiveDesc = "Native adaptive deep thinking"
    override val modelVisionTitle = "Multimodal Input Support"
    override val modelVisionDesc =
        "Multimodal capability enables direct analysis of visual screenshots, design assets, diagrams and code references:"
    override val modelVisionImageTitle = "Image Analysis (Vision)"
    override val modelVisionImageDesc = "Upload PNG / JPEG / WEBP designs and UI error screenshots for direct analysis"
    override val modelVisionDocTitle = "Document Understanding"
    override val modelVisionDocDesc = "Native reading of PDF / text specifications to extract code context"
    override val modelToolsFunctionTitle = "Function Calling"
    override val modelToolsFunctionDesc = "Automated execution of IDE tools and terminal command orchestration"
    override val modelSpecsTitle = "Model Specifications & Metadata"
    override val modelSpecsId = "Model Identifier (ID)"
    override val modelSpecsContextWindow = "Total Context Window"
    override val modelSpecsDynamicConfig = "Official Dynamic Config"
    override val modelSpecsMaxOutput = "Max Output Tokens"
    override val modelSpecsDefaultLimit = "Official Default Limit"
    override val modelSpecsRoles = "Assigned Roles"

    override val policyPresetDefault = "Official Default"
    override val policyPresetCustom = "Custom"
    override val policyRecommended = "Recommended"
    override val policyDefaultDesc = "No custom override; follows native Checkpointer policy from official catalog."
    override val policyCustomDesc =
        "Click percentages for quick setup or enter exact token numbers for fine-tuned control."
    override val policyPresetDesc =
        "Choose a context limit tier: fixed presets are ready to use, or switch to custom policy."
    override val policyCompressorModel = "Compression Execution Model"
    override val policyFollowCurrent = "Follow Current Model"
    override val policyOfficialDefault = "Official Default"
    override val policyCheckpoint = "Auto Checkpoint"
    override val policyContextLimit = "Context Capacity"
    override val policyOutputReserve = "Output Reserve"
    override val policyByPercentage = "By Percentage"
    override val policyExactTokens = "Exact Tokens"
    override val policyQuickPreset = "Quick Presets"
    override val policyDistribution = "Context Capacity Distribution"
    override val policyLegendNormal = "Active Conversation Area"
    override val policyLegendArchive = "Auto Checkpoint Area (No deletion)"
    override val policyLegendUnused = "Unallocated Model Capacity"
    override fun policyModelContext(context: String) = "Model Context · $context"
    override val policyLimitMustPositive = "Context capacity must be greater than 0"
    override val policyThresholdMustPositive = "Auto checkpoint must be greater than 0"
    override val policyReserveMustPositive = "Output reserve must be greater than 0"
    override fun policyLimitExceedsContext(limit: String, context: String) =
        "Context capacity ($limit) cannot exceed model context ($context)"

    override fun policyLimitExceedsSafeLimit(limit: String, safeLimit: String, context: String, reserve: String) =
        "Context capacity ($limit) exceeds safe limit ($safeLimit) [Formula: Model Context $context - Output Reserve $reserve]"

    override fun policyThresholdExceedsLimit(threshold: String, limit: String) =
        "Auto checkpoint ($threshold) must be less than context capacity ($limit)"

    override fun policySumExceedsLimit(sum: String, limit: String) =
        "Sum of checkpoint and reserve ($sum) exceeds context capacity ($limit)"

    override val policyFormulaHint =
        "Constraint: Trigger Limit (MaxTokenLimit) ≤ Model Context (ContextWindow) - Max Output (OutputLimit)"
    override val policyFormulaHintDesc =
        "The client strictly requires reserving output headroom for model responses, otherwise conversations fail immediately."

    override val reasoningDialogTitle = "Configure Deep Thinking"
    override val reasoningEnableTitle = "Enable Deep Thinking (Reasoning)"
    override val reasoningEnableSubtitle = "Enable and configure reasoning tiers and thinking budgets for this model"
    override val reasoningAvailableLevels = "Available Reasoning Levels"
    override val reasoningCustomValue = "Custom Reasoning Override"
    override val reasoningOptional = "Optional"
    override val reasoningCustomValueDesc = "Override with custom upstream reasoning strings if required"
    override val reasoningDefaultBudget = "Default Thinking Budget (Optional)"
    override val reasoningDynamicBudgetPlaceholder = "-1 for dynamic budget"
    override val reasoningMinBudgetTitle = "Min Thinking Budget (Optional)"
    override fun reasoningExamplePlaceholder(example: String) = "e.g. $example"
    override val reasoningBudget = "Thinking Budget"
    override val reasoningMinBudget = "Minimum Thinking Budget"
    override fun reasoningMustBeInteger(label: String) = "$label must be an integer"
    override val reasoningGeminiBudgetValidation = "Gemini thinking budget must be -1, 0 or a positive integer"
    override val reasoningMinBudgetMustPositive = "Minimum thinking budget must be greater than 0"
    override val reasoningMinBudgetExceedsBudget = "Minimum thinking budget cannot exceed thinking budget"
    override val reasoningOnlyGeminiSupportsBudget = "Only Gemini protocol supports model-level thinking budget"
    override val reasoningCustomValueInvalid =
        "Custom reasoning value does not conform to protocol or output constraints"

    override fun reasoningLevelInvalid(level: String) =
        "Reasoning level $level does not conform to protocol or output constraints"

    override val reasoningSelectAtLeastOne = "Please select at least one reasoning level or specify a thinking budget"

    override val providerPresetCategoryAll = "All"
    override val providerPresetCategoryAggregator = "Gateways"
    override val providerPresetCategoryRecommended = "Recommended"
    override val providerPresetCategoryOfficial = "Official Vendors"
    override val providerPresetCategoryLocalCustom = "Local / Custom"
    override val providerSearchPlaceholder = "Search providers..."
    override val providerTagOfficial = "Official"
    override val providerTagAggregator = "Gateway"
    override val providerTagLocal = "Local"
    override val providerTagCustom = "Custom"
    override val providerNameLabel = "Upstream Provider Name"
    override val providerNamePlaceholder = "e.g. CLIProxyAPI, Company Gateway, DeepSeek"
    override val providerNameDesc = "Custom display name used to identify the source in routes and model lists"
    override val providerProtocolLabel = "API Protocol"
    override val providerProtocolOpenAIChatDesc = "For /v1/chat/completions; CLIProxyAPI, Sub2API and standard gateways"
    override val providerProtocolAnthropicDesc = "For Anthropic official /v1/messages protocol"
    override val providerProtocolGeminiDesc = "For Google Gemini official generateContent protocol"
    override val providerProtocolOpenAIResponsesDesc = "For OpenAI Responses API native structure protocol"
    override val providerBaseUrlLabel = "API Base URL"
    override val providerBaseUrlPlaceholder = "e.g. https://api.openai.com/v1"
    override val providerBaseUrlDesc = "Entering base URL automatically infers model list and generation endpoints"
    override val providerApiKeyLabel = "API Key"
    override val providerApiKeyPlaceholder = "Enter API Key (leave blank if no auth required)"
    override val providerApiKeyDesc = "Leave blank if upstream service requires no authentication"
    override val providerAdvancedSettings = "Advanced Settings (Custom Endpoint URLs)"
    override val providerAdvancedAutoGenerated = "Auto-generated from Base URL by default"
    override val providerAdvancedCollapse = "Collapse"
    override val providerCustomModelsEndpoint = "Model List Endpoint (Custom)"
    override val providerCustomCompletionsEndpoint = "Completions Endpoint (Custom)"
    override val providerEndpointAutoInferPlaceholder = "Leave blank to auto-infer"
    override val providerStepPreset = "Select Preset"
    override val providerStepConnection = "Connection"
    override val providerStepModels = "Select Models"
    override val providerNextStep = "Next"
    override val providerPrevStep = "Back"
    override val providerSearchModelsPlaceholder = "Search model name or ID..."
    override fun providerFilterAll(count: Int) = "All ($count)"
    override fun providerFilterSelected(count: Int) = "Selected ($count)"
    override fun providerFilterUnselected(count: Int) = "Unselected ($count)"
    override fun providerSelectAll(count: Int) = "Select All ($count)"
    override val providerUnselectAll = "Deselect All"
    override val providerViewModelsResponse = "View Model List Data"
    override fun providerModelsResponseUnavailable(error: String) = "Unable to read model list data: $error"
    override fun providerNoModelsFound(query: String) = "No models matched \"$query\""
    override val providerNoModelsEmpty = "No models in current filter"
    override fun providerTestLatency(latencyMs: Long) = "${latencyMs}ms"
    override val providerTestFailed = "Failed"
    override val providerTestFailureDetailsTitle = "Model Test Failed"
    override val providerTestFailureStatusCode = "HTTP Status Code"
    override val providerTestFailureErrorDetails = "Error Details"
    override val providerTestFailureRetry = "Retry Test"
    override val providerTestFailureCopy = "Copy Error"
    override val providerTestFailureCopied = "Copied to Clipboard"
    override val providerTestFailureClose = "Close"
    override val providerTokenLimitNotSet = "Not Set"
    override fun providerCustomInputTokenTitle(model: String) = "Custom Context Window · $model"
    override fun providerCustomOutputTokenTitle(model: String) = "Custom Max Output · $model"
    override val providerUnprobedCatalog = "Unprobed"
    override val providerTesting = "Testing"
    override val providerTestBtn = "Test"
    override fun providerInputTokenPrefix(label: String) = "Context window: $label"
    override fun providerOutputTokenPrefix(label: String) = "Max output: $label"
    override val providerCustomTokenOption = "Custom value..."
    override val providerClearTokenOption = "Clear (Set to Not Set)"
    override val providerCustomTokenDialogTitle = "Custom Token Limit"
    override val providerCustomTokenPlaceholder = "e.g. 128K, 1M, 200000"
    override val providerCustomTokenHint = "Supports 128K / 1M / numeric formats"
    override fun providerCustomTokenParsed(tokens: String) = "Parsed as: $tokens tokens"
    override val providerFetchFailedCheckUrlKey = "Failed to fetch model catalog. Please check Base URL and API Key"
    override fun providerFetchFailedWithError(error: String) = "Failed to fetch models: $error"
    override val providerDiscardConfirmTitle = "Discard unsaved changes?"
    override val providerDiscardConfirmMessage =
        "The provider configuration has not been saved. Changes will be lost upon exit."
    override val providerSkipFetchManualAdd = "Skip fetch, add models manually"
    override val providerAddNewModel = "Add Model"
    override val providerManualAddModelTitle = "Manually Add Custom Model"
    override val providerModelIdPlaceholder = "Model ID (e.g. gpt-4o, claude-3-7-sonnet)"
    override val providerModelNamePlaceholder = "Display name (optional)"
    override val providerModelVendorPlaceholder = "Vendor (optional, e.g. OpenAI)"
    override val providerModelAlreadyExists = "Model ID already exists in the list"
    override val providerModelIdRequired = "Model ID is required"
    override val providerAddAndSelect = "Add & Select"
    override val providerNoModelsEmptyPrompt = "No models available. Click \"Add Model\" above to add one manually."

    override val activityTitle = "Activity Logs"
    override val activitySubtitle = "Inspect request status, route source and response latency"
    override val activityFilterAll = "All Requests"
    override val activityFilterFailed = "Failed Only"
    override val activityClear = "Clear Logs"
    override val activityEmpty = "No activity recorded"
    override val activityEmptyDesc =
        "When Antigravity routes requests through the proxy, detailed logs will appear here in real-time"
    override val activityNoMatchingLogs = "No matching logs found"
    override val activityNoMatchingDesc = "Try searching with different keywords or clearing active filters"
    override val activityPassthrough = "Official Passthrough"
    override val activityRouted = "Custom Route"
    override val activitySearchPlaceholder = "Search model or provider"
    override val activityRecent = "Recent logs"
    override val activityTotal = "Total requests"
    override val activityFailedTotal = "Failed requests"
    override val activityAverage = "Average latency"
    override val activityCacheHitRate = "Cache Hit Rate"
    override val activityFirstTokenLabel = "First Token"
    override val activityPending = "Processing"
    override val activityProcessing = "In progress..."
    override val activityAllTags = "Log Filter"
    override val activityTagFilterTitle = "Log Filter"
    override val activitySelectAll = "Select All"
    override val activityClearFilter = "Reset"
    override fun activitySelectedTagsCount(count: Int) = "$count selected"
    override val activityTokenInput = "Input"
    override val activityTokenOutput = "Output"
    override val activityTokenCache = "Cache"
    override val activityTokenTotal = "Total"
    override val activityDetailCacheHitRate = "Cache Hit Rate"
    override val activityAutoScroll = "Auto Scroll"
    override val activityInMemory = "In-memory log"
    override val activityHealthy = "Healthy"
    override val activityHasErrors = "Errors found"
    override val activityUnknownProvider = "Unknown Provider"
    override val activityDetailTitle = "Request Activity Details"
    override val activityDetailRouteSection = "Route & Invocation Details"
    override val activityDetailMethod = "HTTP Method"
    override val activityDetailPath = "Full Request Path"
    override val activityDetailDuration = "Total Response Latency"
    override val activityDetailFirstToken = "Time to First Token (TTFT)"
    override val activityDetailTimestamp = "Request Start Time"
    override val activityDetailRouteMode = "Route Mode"
    override val activityDetailPassthroughMode = "Official Direct (Cloud Code Passthrough)"
    override val activityDetailForwardMode = "Custom Forward (BYOK Forward)"
    override val activityDetailTargetModel = "Target Model"
    override val activityDetailRequestedModel = "Requested Model"
    override val activityDetailProvider = "Upstream Provider"
    override val activityDetailTokenSection = "Token Usage Metrics (Unmasked)"
    override val activityDetailPromptTokens = "Prompt Tokens"
    override val activityDetailCompletionTokens = "Completion Tokens"
    override val activityDetailTotalTokens = "Total Tokens"
    override val activityDetailReasoningTokens = "Thinking Tokens"
    override val activityDetailCacheReadTokens = "Cache Read Tokens"
    override val activityDetailCacheWriteTokens = "Cache Write Tokens"
    override val activityDetailErrorSection = "Error Details & Upstream Response"
    override val activityDetailCopyJson = "Copy Full JSON"
    override val activityDetailCopyError = "Copy Error Message"
    override val activityDetailCopiedError = "Error message copied to clipboard"
    override val activityRetryCount = "Retry Count"
    override fun activityRetryBadge(count: Int) = "Retry ×$count"

    override val settingsTitle = "Preferences & Settings"
    override val settingsSubtitle = "Manage language, appearance, proxy port and storage"
    override val settingsGeneral = "General"
    override val settingsNetwork = "Network Proxy"
    override val settingsData = "Data Storage"
    override val settingsAboutSection = "About App"
    override val settingsLanguage = "Language"
    override val settingsLanguageDescription = "Choose the application display language"
    override val settingsTheme = "Appearance Theme"
    override val settingsThemeDescription = "Use the system, light or dark appearance"
    override val settingsThemeSystem = "System Default"
    override val settingsThemeLight = "Light Theme"
    override val settingsThemeDark = "Dark Theme"
    override val settingsThemePalette = "Color Palette"
    override val settingsThemePaletteDescription = "Choose core theme color scheme (Material Design 3)"
    override val paletteIndigo = "Aurora Indigo"
    override val paletteOcean = "Ocean Teal"
    override val paletteEmerald = "Natural Emerald"
    override val paletteViolet = "Mystic Violet"
    override val paletteRose = "Vibrant Coral"
    override val paletteAmber = "Dawn Amber"
    override val settingsPort = "Local Proxy Default Port"
    override val settingsPortDescription = "The local proxy server will restart after saving"
    override val settingsPortInvalid = "Port must be between 1024 and 65535"
    override fun settingsPortUpdated(port: Int) = "Proxy port updated to $port"
    override fun settingsPortRestartFailed(error: String) = "Failed to restart proxy on new port: $error"
    override fun settingsPortUpdateFailed(error: String) = "Failed to update proxy port: $error"
    override val settingsHostPathsTitle = "Host Installation Paths"
    override val settingsHostPathsDesc = "Custom installation or executable paths for Antigravity IDE, App and CLI"
    override val settingsDefaultSwitchTargetTitle = "Default Switch Target"
    override val settingsDefaultSwitchTargetDesc = "Default target applications selected when switching accounts"
    override val settingsDefaultSwitchTargetAll = "All Apps (Recommended)"
    override val settingsDefaultSwitchTargetIdeOnly = "IDE Only"
    override val settingsDefaultSwitchTargetAppCliOnly = "App & CLI Only"
    override val settingsDefaultSwitchTargetRemember = "Remember Last Choice"
    override fun settingsHostPathCustom(title: String) = "$title: Custom"
    override fun settingsHostPathAuto(title: String) = "$title: Auto"
    override val settingsStoragePath = "Config File Location"
    override val settingsStorageDescription = "Inspect or back up persisted providers and compression policies"
    override val settingsOpenDirectory = "Open Directory"
    override val settingsDirectoryOpenError = "Unable to open directory"
    override val settingsUnsupportedPlatform = "Opening folder is not supported on this platform"
    override fun settingsOpenDirFailed(error: String) = "Failed to open config directory: $error"
    override val settingsAbout = "About Antigravity Studio"
    override val settingsAboutDescription =
        "A local model access tool built with Kotlin Multiplatform and Compose Desktop."
    override val settingsVersion =
        "Antigravity Studio v${com.yuzhiqiang.antigravity.update.model.AppVersion.CURRENT} · Kotlin Multiplatform & Compose Desktop"
    override val settingsRepo = "GitHub Repository"
    override val settingsConfigDir = "Config Directory"
    override val settingsOpenConfigDir = "Open data and model configuration files"
    override val settingsDeveloper = "Developer"
    override val settingsFeedback = "Feedback & Issues"
    override val settingsFeedbackDesc = "Submit issues or join community discussions"

    // Update & Version Checker
    override val updateCheck = "Check for Updates"
    override val updateChecking = "Checking for updates..."
    override val updateUpToDate = "You are up to date"
    override val updateAvailableTitle = "Update Available"
    override fun updateAvailableSubtitle(version: String) =
        "Antigravity Studio $version is now available. We recommend updating for the best experience."

    override val updateChangelogTitle = "Release Notes"
    override val updateCurrentVersionLabel = "Current Version"
    override val updateLatestVersionLabel = "Latest Version"
    override val updateDownloadNow = "Download Now"
    override val updateLater = "Remind Me Later"
    override val updateIgnoreThisVersion = "Skip This Version"
    override val updateIgnoredNotice = "This version will be skipped in future startup checks"
    override fun updateCheckFailed(error: String) = "Failed to check for updates: $error"
    override val updateNoChangelog = "No release notes provided."
    override fun updateDownloadProgress(downloaded: String, total: String, percent: Int) =
        "$downloaded / $total ($percent%)"

    override fun updateDownloadSpeed(speed: String) = "$speed/s"
    override val updateDownloading = "Downloading update…"
    override val updateDownloadCompleted = "Download complete. Opening installer…"
    override val updateInstallNow = "Install Now"
    override val updateShowInFolder = "Show in Folder"
    override fun updateDownloadFailed(error: String) = "Download failed: $error"
    override val updateRetryDownload = "Retry Download"
    override val updateOpenInBrowser = "Download in Browser"
    override val updateCancelDownload = "Cancel"
    override val settingsAutoCheckUpdate = "Check for updates on startup"
    override val settingsAutoCheckUpdateDesc =
        "Silently check for new versions on startup and notify when updates are available"
    override val settingsCheckUpdateBtn = "Check Updates"
    override val settingsCheckingUpdate = "Checking..."
    override val settingsLatestVersionBadge = "Latest"
    override val settingsNewVersionBadge = "Update"
    override fun settingsLastChecked(time: String) = "Last checked: $time"
    override val settingsDeveloperMode = "Developer Debug Mode"
    override val settingsDeveloperModeDesc = "Show raw JSON and modified JSON protocol inspection tools"
    override val settingsDeveloperModeEnabled = "Developer debug mode enabled"
    override val settingsDeveloperModeDisabled = "Developer debug mode disabled"
    override val developerModeDialogTitle = "Developer Debug Mode"
    override val developerModeUnlockPrompt = "Enter password to unlock developer debug mode:"
    override val developerModeTurnOn = "Enable"
    override val developerModeTurnOff = "Disable Developer Mode"
    override val developerModeWrongPassword = "Incorrect password, please try again"
    override val developerModeKeepEnabled = "Keep Enabled"
    override val developerModeCancel = "Cancel"

    override val doctorTitle = "Doctor Health Diagnostics"
    override val doctorSubtitle = "Full-stack diagnostics for network, configs, host and proxy"
    override val doctorRunAll = "Run All Checks"
    override val doctorScanning = "Diagnosing..."
    override val doctorPassed = "Passed"
    override val doctorFailed = "Failed"
    override val doctorWarning = "Warning"
    override val doctorDirect = "Direct"
    override val doctorFixSuggestions = "Fix Suggestions"
    override val doctorDialogTitle = "System Health & Diagnostic Suite"
    override val doctorDialogSubtitle = "Check local proxy, upstream connectivity and Antigravity host integration"
    override val doctorBannerGood = "All systems operational and ready"
    override val doctorBannerWarning = "Some configurations need attention"
    override val doctorBannerError = "System issues detected"
    override fun doctorBannerIssueCount(count: Int) = " • $count issue(s) pending"
    override fun doctorBannerStats(total: Int, passed: Int, issues: Int) =
        "Total $total checks • $passed healthy" + if (issues > 0) " • $issues pending" else ""

    override fun doctorCheckedAt(time: String) = "Checked at $time"
    override val doctorCategoryProxy = "Local Proxy Server"
    override val doctorCategoryNetwork = "Official Service Connectivity"
    override val doctorCategoryConfig = "Configuration Integrity"
    override val doctorCategoryProvider = "Model Providers"
    override val doctorCategoryHost = "Antigravity Host Environments"
    override val doctorScanningStatus = "Scanning system environment..."
    override val doctorRealtimeStatus = "Diagnostics generated in real-time"
    override val doctorScanningTitle = "Executing full-stack health diagnostics..."
    override val doctorScanningDesc = "Checking proxy ports, provider handshakes and host integration configs"
    override val doctorFixStartProxy = "Start Proxy"
    override val doctorFixGoConfigure = "Configure"
    override val doctorFixOneClickEnable = "Enable Proxy"
    override val doctorFixUpdateConfig = "Update Config"
    override val doctorFixResetOfficial = "Reset Official"
    override val doctorFixRestartIde = "Restart IDE"
    override val doctorFixRestartApp = "Restart App"
    override val doctorFixPruneModels = "Prune Models"
    override val doctorFixRetry = "Retry"
    override val doctorSuggestionPrefix = "💡 Suggestion: "
    override val doctorAutoFixSuccess = "Auto-fix applied successfully"
    override val doctorAutoFixFailed = "Auto-fix failed; please check manually"

    override val doctorCheckProxyStoppedTitle = "Local proxy server is not running"
    override fun doctorCheckProxyStoppedMsg(port: Int) =
        "The proxy server is stopped and cannot intercept requests (configured port: $port)."

    override val doctorCheckProxyStoppedSugg = "Please start the local proxy server."
    override val doctorCheckProxyOkTitle = "Local proxy server is running"
    override fun doctorCheckProxyOkMsg(port: Int) = "Proxy is ready and listening on http://127.0.0.1:$port."
    override val doctorCheckProxyUnreachableTitle = "Local proxy endpoint unreachable"
    override fun doctorCheckProxyUnreachableMsg(port: Int) =
        "Cannot connect to 127.0.0.1:$port; check port conflicts or permissions."

    override val doctorCheckProxyUnreachableSugg = "Try restarting the proxy server."
    override val doctorCheckNetworkOkTitle = "Official service connectivity"
    override fun doctorCheckNetworkOkMsg(latencyMs: Long) = "Google Cloud Code service is reachable (${latencyMs}ms)."
    override val doctorCheckNetworkFailedTitle = "Failed to connect to official service"
    override fun doctorCheckNetworkFailedMsg(error: String) = "Cannot reach Google official services: $error."
    override val doctorCheckNetworkFailedSugg =
        "Check network and proxy settings; restart Studio if direct connection works."
    override val doctorCheckNoProvidersTitle = "No model providers configured or enabled"
    override val doctorCheckNoProvidersMsg = "No active providers; all custom model requests will be blocked."
    override val doctorCheckNoProvidersSugg = "Go to Models screen to add a provider."
    override fun doctorCheckProviderNoModelsTitle(provider: String) = "Provider \"$provider\" has no models configured"
    override val doctorCheckProviderNoModelsMsg = "This provider is enabled but has no upstream models associated."
    override val doctorCheckProviderNoModelsSugg = "Configure upstream models in Models screen."
    override val doctorCheckIdeMismatchTitle = "Antigravity IDE proxy config mismatch (Needs update)"
    override fun doctorCheckIdeMismatchMsg(current: String, targetPort: Int) =
        "Detected settings.json proxy is \"$current\", which differs from local proxy port \"http://127.0.0.1:$targetPort\"."

    override val doctorCheckIdeMismatchSugg =
        "Click auto-fix to update to current port and restart IDE, or reset to official mode."
    override val doctorCheckIdeRunningSuffix = "(IDE is running)"
    override val doctorCheckIdeOkTitle = "Antigravity IDE proxy integration is active"
    override fun doctorCheckIdeOkMsg(port: Int, runningSuffix: String) =
        "settings.json is properly configured to http://127.0.0.1:$port $runningSuffix."

    override val doctorCheckIdeOfficialTitle = "Antigravity IDE is in official mode (No proxy)"
    override val doctorCheckIdeOfficialMsg =
        "Directly connected to Google official service. Enable proxy integration to use custom models in IDE."
    override val doctorCheckAppMismatchTitle = "Antigravity App proxy environment mismatch (Needs update)"
    override fun doctorCheckAppMismatchMsg(current: String, targetPort: Int) =
        "Detected CLOUD_CODE_URL is \"$current\", which differs from local proxy port \"http://127.0.0.1:$targetPort\"."

    override val doctorCheckAppMismatchSugg =
        "Click auto-fix to update environment variable and restart App, or reset to official mode."
    override val doctorCheckAppRunningSuffix = "(App is running)"
    override val doctorCheckAppOkTitle = "Antigravity App proxy integration is active"
    override fun doctorCheckAppOkMsg(port: Int, runningSuffix: String) =
        "CLOUD_CODE_URL is properly configured to http://127.0.0.1:$port $runningSuffix."

    override val doctorCheckAppOfficialTitle = "Antigravity App is in official mode (No proxy)"
    override val doctorCheckAppOfficialMsg =
        "Directly connected to Google official service. Enable proxy integration to use custom models in App."
    override val doctorCheckCliMismatchTitle = "Antigravity CLI proxy config mismatch (Needs update)"
    override fun doctorCheckCliMismatchMsg(current: String, targetPort: Int) =
        "Detected CLI proxy is \"$current\", which differs from local proxy port \"http://127.0.0.1:$targetPort\"."

    override val doctorCheckCliMismatchSugg = "Click auto-fix to update to current port, or reset to official mode."
    override val doctorCheckCliOkTitle = "Antigravity CLI proxy integration is active"
    override fun doctorCheckCliOkMsg(port: Int) =
        "cloud_code_url in CLI config is properly configured to http://127.0.0.1:$port."

    override val doctorCheckCliOfficialTitle = "Antigravity CLI is in official mode (No proxy)"
    override val doctorCheckCliOfficialMsg = "CLI is currently in official direct mode."
    override fun doctorCheckProviderInvalidModelsTitle(provider: String) = "Provider \"$provider\" has invalid models"
    override fun doctorCheckProviderInvalidModelsMsg(models: String) =
        "Upstream does not provide the following models: $models."

    override val doctorCheckProviderInvalidModelsSugg = "Prune invalid models to avoid request errors."
    override fun doctorCheckProviderOkTitle(provider: String) = "Provider \"$provider\" connected successfully"
    override fun doctorCheckProviderOkMsg(count: Int) =
        "Authentication verified; all $count models are available in upstream catalog."

    override fun doctorCheckProviderUnverifiedTitle(provider: String) =
        "Provider \"$provider\" reachable but catalog unverified"

    override val doctorCheckProviderUnverifiedMsg =
        "Connected to upstream, but endpoint returned no parseable model catalog."
    override val doctorCheckProviderUnverifiedSugg =
        "Verify models endpoint configuration and check model IDs manually."

    override val commonSave = "Save"
    override val commonCancel = "Cancel"
    override val commonConfirm = "Confirm"
    override val commonDelete = "Delete"
    override val commonEdit = "Edit"
    override val commonClose = "Close"
    override val commonSuccess = "Success"
    override val commonError = "Error"
    override val commonCopied = "Copied to clipboard"
    override val commonGotIt = "Got it"
    override val commonRefresh = "Refresh"
    override val commonRetry = "Retry"
    override val commonSearch = "Search..."
    override val commonClear = "Clear"
    override val commonSelectAll = "Select All"
    override val commonUnselectAll = "Deselect All"
    override val commonNotSet = "Not Set"
    override val commonUnknown = "Unknown"
    override val commonAndMore = "etc."
    override val commonOptional = "Optional"
    override val commonUnsaved = "Unsaved"
    override val trayShowMainWindow = "Show Main Window"
    override val trayQuitApplication = "Quit"

    // Account Switch Dialog & Process
    override val accountsSwitchDialogTitle = "Switch Account"
    override val accountsSwitchSelectTargetTitle = "Select Target Applications"
    override val accountsSwitchStatusIdeNotInstalled = "Not installed · Antigravity IDE not detected"
    override val accountsSwitchStatusIdeRunning = "Running · Will safely exit and restart"
    override val accountsSwitchStatusIdeStopped = "Stopped · Will inject credentials and launch"
    override val accountsSwitchSharedTitleCli = "Antigravity CLI (Shared Credentials)"
    override val accountsSwitchSharedTitleSystem = "System Shared Credentials (CLI / Local)"
    override val accountsSwitchStatusAppRunning = "App is running · Will safely exit and restart"
    override val accountsSwitchStatusAppStopped = "App is stopped · Will inject credentials and launch"
    override val accountsSwitchStatusCliOnly = "App not installed · Syncs ~/.gemini/ credentials for CLI"
    override val accountsSwitchStatusNone =
        "No client detected · Only updates Studio active account and shared credentials"
    override val accountsSwitchRememberChoice = "Remember selection"
    override val accountsSwitching = "Switching..."
    override val accountsSwitchConfirmRestart = "Confirm & Restart"
    override val accountsSwitchConfirmLaunch = "Confirm & Launch"
    override val accountsSwitchConfirm = "Confirm Switch"
    override val accountsSwitchTargetIde = "Antigravity IDE"
    override val accountsSwitchTargetAppCli = "Antigravity App & CLI"

    // Smart Switch Dialog & Strategy
    override val smartSwitchTitle = "Smart Account Switch"
    override val smartSwitchSubtitle =
        "Automatically switch to the best fallback account on quota exhaustion or 429 errors"
    override val smartSwitchEnableTitle = "Enable Smart Account Switch"
    override val smartSwitchEnableDesc = "Automatically switch account upon 429 rate limit or insufficient quota"
    override val smartSwitchThresholdLabel = "Switch Trigger Quota Threshold"
    override val smartSwitchStrategyLabel = "Fallback Account Strategy"
    override val smartSwitchStrategyHighestQuota = "Highest Quota First (Recommended)"
    override val smartSwitchStrategyRoundRobin = "Round Robin"
    override val smartSwitchCooldownLabel = "Minimum Switch Cooldown"
    override fun smartSwitchSeconds(seconds: Int) = "$seconds s"
    override val smartSwitchProtectGenerationTitle = "Active Generation Protection"
    override val smartSwitchProtectGenerationDesc = "Pause auto-switch during streaming responses or agent runs"
    override val smartSwitchInterruptTip =
        "Protection note: Auto-switch is deferred during generation to avoid stream interruptions."
    override val smartSwitchReasonDisabled = "Smart switch is disabled"
    override fun smartSwitchReasonCooldown(remainingSec: Long) = "In cooldown period (${remainingSec}s remaining)"
    override val smartSwitchReasonWorkflowLocked = "Active workflow is protected"
    override val smartSwitchReasonNoBackupAccounts = "No backup accounts available"
    override val smartSwitchReasonNoEligibleCandidate = "No candidate account with sufficient quota found"
    override val smartSwitchTriggerReason429 = "Hit 429 Quota Exceeded"
    override val smartSwitchTriggerReasonLowQuota = "Quota fell below threshold"
    override fun smartSwitchReasonTaskRunning(trigger: String) = "$trigger, but another switch task is in progress"
    override fun smartSwitchReasonSuggestSwitch(trigger: String, email: String) =
        "$trigger. Suggested switch to $email; please restart app to apply."

    override val hotSwitchTaskAlreadyRunning = "Another account switch task is already running, please try again later"
    override val hotSwitchIdeNotApplied = "IDE account is not yet active"
    override val hotSwitchNotAllTargetsApplied = "Account not yet active in all target applications"

    // Quota Auto-Refresh Dialog
    override val quotaRefreshTitle = "Quota Auto-Refresh Frequency"
    override val quotaRefreshSubtitle = "Configure background sync frequency for multiple accounts"
    override val quotaRefreshActiveIntervalTitle = "Active Account Refresh Interval"
    override val quotaRefreshBackgroundIntervalTitle = "Background Accounts Refresh Interval"
    override val quotaRefreshCustomOption = "Custom…"
    override val quotaRefreshPlaceholderActive = "e.g. 45"
    override val quotaRefreshPlaceholderBackground = "e.g. 15"
    override val quotaRefreshActiveHint =
        "Tip: Active account refresh interval directly impacts quota freshness and auto-switch timing."
    override val quotaRefreshDefaultSummary = "Default: Active account 1 min, background accounts 10 min"
    override val quotaRefreshResetDefault = "Reset to Default"
    override val quotaRefreshUnitSecond = "sec"
    override val quotaRefreshUnitMinute = "min"
    override val quotaRefreshUnitHour = "hr"
    override fun quotaRefreshPresetRecommended(label: String) = "$label (Recommended)"
    override val quotaRefreshInputInvalid = "Please enter a valid refresh interval"
    override fun quotaRefreshMinActiveSeconds(sec: Int) = "Minimum interval is $sec seconds"
    override fun quotaRefreshMinBackgroundMinutes(min: Int) = "Minimum interval is $min minutes"
    override fun quotaRefreshMaxHours(hr: Int) = "Maximum interval is $hr hour(s)"

    // Account Cards & Screen
    override fun accountsLastSyncTime(time: String) = "Quota last synced: $time"
    override val accountsSyncToOtherHost = "Sync to other clients"
    override val accountsSetAsActiveTooltip = "Set as current active account"
    override val accountsRefreshThisTooltip = "Refresh quota for this account"
    override val accountsRefreshingTooltip = "Refreshing quota..."
    override val accountsDeleteThisTooltip = "Delete this account"
    override fun accountsModelFamily(label: String) = "$label Models"
    override val accountsQuotaFiveHour = "5-Hour Quota"
    override val accountsQuotaWeekly = "Weekly Quota"
    override val accountsFetchingQuota = "Fetching quota data..."
    override val accountsNoQuotaData = "No Data"
    override val accountsQuotaResetSoon = "Resetting soon"
    override val accountsQuotaFull = "● 100% Available"
    override val accountsQuotaResetInSuffix = " left"

    // Time & Countdown formatting
    override fun formatCountdownDaysHours(days: Long, hours: Long) = "${days}d ${hours}h"
    override fun formatCountdownDays(days: Long) = "${days}d"
    override fun formatCountdownHoursMinutes(hours: Long, minutes: Long) = "${hours}h ${minutes}m"
    override fun formatCountdownHours(hours: Long) = "${hours}h"
    override fun formatCountdownMinutes(minutes: Long) = "${minutes}m"
    override val formatCountdownLessThanMinute = "< 1m"

    // Quota natural language descriptions
    override val quotaDescFiveHourFull = "Your 5-hour quota is currently fully available."
    override val quotaDescWeeklyFull = "Your weekly quota is currently fully available."
    override val quotaDescGeneralFull = "Your quota is currently fully available."
    override fun quotaDescFiveHourResetting(timeStr: String) = "Partially consumed 5-hour quota, resets in $timeStr."
    override fun quotaDescWeeklyResetting(timeStr: String) = "Partially consumed weekly quota, resets in $timeStr."
    override fun quotaDescGeneralResetting(timeStr: String) = "Quota will fully reset in $timeStr."

    // Quota Window
    override val quotaWindowFiveHour = "5-Hour Quota"
    override val quotaWindowWeekly = "Weekly Quota"
    override val quotaWindowDaily = "Daily Quota"
    override val quotaWindowGeneral = "Period Quota"
    override val accountsSearchPlaceholder = "Search by email..."
    override val accountsSortByQuotaDesc = "Sort by remaining quota (High to Low)"
    override val accountsSortByQuotaDescActive = "Currently sorted by quota (Click to switch to default)"
    override val accountsSortChipLabel = "Sort by Quota"
    override val accountsAddAccountTooltip = "Add single Refresh Token or import in bulk"
    override val accountsRefreshAllTooltip = "Concurrently refresh quotas for all accounts"
    override fun accountsAutoRefreshTooltip(activeSec: Int, bgMin: Int) =
        "Configure auto-refresh (Current: active ${activeSec}s / background ${bgMin}m)"

    override val accountsPrivacyHideTooltip = "Enable privacy mode to mask email"
    override val accountsPrivacyShowTooltip = "Disable privacy mode to show full email"
    override val accountsExportTooltip = "Export credentials (clipboard or JSON file)"
    override val accountsExportCopyToClipboard = "Copy to Clipboard"
    override val accountsExportSaveJson = "Save as JSON File..."
    override fun accountsExportCopiedNotice(count: Int) = "Copied $count account credentials to clipboard"
    override fun accountsExportSuccessNotice(count: Int, filename: String) = "Exported $count credentials to $filename"
    override fun accountsExportFailedNotice(error: String) = "Failed to export file: $error"
    override val accountsExportDialogTitle = "Save Account Credentials"
    override val accountsSmartSwitchTooltip = "Configure smart auto-switch for low quota or 429 errors"
    override fun accountsSearchNoMatch(query: String) = "No accounts found matching '$query'"
    override val accountsDeleteConfirmTitle = "Delete Account"
    override fun accountsDeleteConfirmMsg(email: String) =
        "Are you sure you want to remove '$email'? Auto-refresh and monitoring will stop."

    override val accountsDeleteConfirmBtn = "Delete"

    // Overview Screen & Hero Card
    override val overviewTodayRequests = "Today's Requests"
    override fun overviewRequestsUnit(count: Long) = "$count requests"
    override val overviewServiceUptime = "Service Uptime"
    override val overviewAvgLatency = "Avg Latency"
    override val overviewRouteUpstreamStatus = "Route Upstream"
    override val overviewOfficialDirect = "Official Direct"
    override fun overviewCustomUpstreamSummary(providerCount: Int, modelCount: Int) =
        "$providerCount providers · $modelCount models"

    override fun overviewSourceInUse(sources: String) = "$sources in use"
    override val overviewActiveAccountBadge = "Active Account"
    override val overviewSyncingQuotas = "Syncing quota data..."

    // Notices & ViewModel Messages
    override val noticeAuthLinkCopied = "Authorization link copied to clipboard"
    override val noticeAuthLinkCopiedBrowser = "Auth link copied, please open in browser"
    override val noticeSwitchAlreadyRunning = "Another account switch task is already running, please try again later"
    override fun noticeSwitchResult(summary: String) = "Switch result: $summary"
    override fun noticeSwitchFailed(error: String) = "Account switch failed: $error"
    override fun noticeAccountNotFound(idOrEmail: String) = "Account not found: $idOrEmail"
    override val noticeSmartSwitchEnabled = "Smart auto-switch enabled"
    override val noticeSmartSwitchDisabled = "Smart auto-switch disabled"
    override val noticeQuotaAutoRefreshEnabled = "Quota auto-refresh config updated"
    override val noticeQuotaAutoRefreshDisabled = "Quota auto-refresh disabled"
    override val noticeAccountRemoved = "Account removed"
    override val noticeTokenRefreshed = "Account credentials refreshed successfully"
    override fun noticeTokenRefreshFailed(error: String) = "Failed to refresh credentials: $error"
    override val noticeRemarkUpdated = "Account note updated"
    override fun noticeCleanAccountsSuccess(count: Int) = "Cleaned up $count invalid/expired accounts"
    override fun noticeCleanAccountsFailed(error: String) = "Failed to clean accounts: $error"
    override fun noticeBatchImportSuccess(count: Int) = "Successfully imported $count accounts"
    override fun noticeBatchImportPartial(successCount: Int, failedCount: Int) =
        "Batch import completed: $successCount succeeded, $failedCount skipped"

    override fun noticeBatchImportFailedAll(failedCount: Int) =
        "Batch import failed: all $failedCount tokens are invalid"

    override val noticeQuotasUpdatedAll = "All account quotas updated"
    override fun noticeQuotasUpdateFailedAll(error: String) = "Quota refresh error: $error"
    override val noticeQuotaRefreshedSingle = "Account quota refreshed"
    override fun noticeQuotaRefreshFailedSingle(error: String) = "Failed to fetch quota: $error"
    override fun switchStatusNotAvailable(target: String) = "$target currently unavailable"
    override fun switchStatusConfigured(target: String) = "$target configured"
    override fun switchStatusConfirmed(target: String) = "$target active"
    override fun switchStatusPendingRestart(target: String) = "$target pending restart"
    override fun switchStatusFailed(target: String) = "$target verification failed"

    // Add Account Dialog & Import
    override val accountsAddTabOAuth = "Google Sign In (OAuth)"
    override val accountsAddTabTokenImport = "Token / JSON Import"
    override val accountsAddSelectJsonFileTitle = "Select Accounts JSON Backup File"
    override val accountsAddInvalidAuthCode = "Invalid auth code, please verify full URL"
    override val accountsAddReopenBrowser = "Reopen in Browser"
    override val accountsAddOpenBrowser = "Open in Browser"
    override val accountsAddCopyAuthUrl = "Copy Auth Link"
    override val accountsAddCancelAuth = "Cancel Auth"
    override val accountsAddFallbackManualHint = "If auto-callback is blocked, paste the full URL from browser below:"
    override val accountsAddFallbackManualPlaceholder = "http://127.0.0.1:41321/... or Auth Code"
    override val accountsAddSubmit = "Submit"
    override val accountsAddTokenBatchDesc =
        "Supports pasting single/multi-line Refresh Tokens, Cockpit exported JSON arrays, or backup files."
    override val accountsAddImportJsonFile = "Import JSON File"
    override val accountsAddPasteClipboard = "Paste from Clipboard"
    override val accountsAddTokenPlaceholder = "Paste 1//0g... tokens (Supports multi-line batch or JSON)"
    override fun accountsAddRecognizedCount(count: Int, preview: String) =
        "Recognized $count valid account credentials $preview"

    override val accountsAddUnrecognizedTokens = "Unable to recognize valid Refresh Token or JSON data"
    override val accountsAddImporting = "Validating and importing accounts..."
    override fun accountsAddConfirmImport(count: Int) = when {
        count > 1 -> "Import $count Accounts"
        count == 1 -> "Import 1 Account"
        else -> "Confirm Import"
    }

    // Other UI Components & Relative Times
    override val settingsAccountAndAppCardTitle = "Accounts & Applications"
    override fun accountsCountSummary(count: Int) = "$count accounts"
    override val accountsFiveHourLabel = "5h"
    override val accountsWeeklyLabel = "Weekly"
    override fun accountsResetInCountdown(countdown: String) = "$countdown left"
    override val timeNeverRefreshed = "Never synced"
    override val timeJustNow = "Just now"
    override fun timeMinutesAgo(min: Long) = "Synced $min min ago"
    override fun timeHoursAgo(hours: Long) = "Synced $hours hr ago"
    override fun timeDaysAgo(days: Long) = "Synced $days d ago"

    override fun proxyStarted(port: Int) = "Local proxy started ($port)"
    override fun proxyStartFailed(error: String) = "Failed to start local proxy: $error"
    override val proxyStopped = "Local proxy stopped"
    override fun proxyRestarted(port: Int) = "Local proxy restarted ($port)"
    override fun proxyRestartFailed(error: String) = "Failed to restart local proxy: $error"
    override fun proxyTestSuccess(latencyMs: Long) = "Proxy connection test succeeded (${latencyMs}ms)"
    override fun proxyTestFailed(error: String) = "Proxy connection test failed: $error"
}
