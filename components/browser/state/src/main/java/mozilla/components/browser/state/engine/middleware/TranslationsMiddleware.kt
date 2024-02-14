/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.state.engine.middleware

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.InitAction
import mozilla.components.browser.state.action.TranslationsAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.translate.LanguageSetting
import mozilla.components.concept.engine.translate.TranslationError
import mozilla.components.concept.engine.translate.TranslationOperation
import mozilla.components.concept.engine.translate.TranslationPageSettingOperation
import mozilla.components.concept.engine.translate.TranslationPageSettings
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.MiddlewareContext
import mozilla.components.support.base.log.logger.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * This middleware is for use with managing any states or resources required for translating a
 * webpage.
 */
class TranslationsMiddleware(
    private val engine: Engine,
    private val scope: CoroutineScope,
) : Middleware<BrowserState, BrowserAction> {
    private val logger = Logger("TranslationsMiddleware")

    override fun invoke(
        context: MiddlewareContext<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        // Pre process actions
        when (action) {
            is InitAction ->
                scope.launch {
                    requestEngineSupport(context)
                }

            is TranslationsAction.OperationRequestedAction -> {
                when (action.operation) {
                    TranslationOperation.FETCH_SUPPORTED_LANGUAGES -> {
                        scope.launch {
                            requestSupportedLanguages(context, action.tabId)
                        }
                    }
                    TranslationOperation.FETCH_PAGE_SETTINGS -> {
                        scope.launch {
                            requestPageSettings(context, action.tabId)
                        }
                    }
                    TranslationOperation.FETCH_NEVER_TRANSLATE_SITES -> {
                        scope.launch {
                            getNeverTranslateSites(context, action.tabId)
                        }
                    }
                    TranslationOperation.TRANSLATE,
                    TranslationOperation.RESTORE,
                    -> Unit
                }
            }
            is TranslationsAction.RemoveNeverTranslateSiteAction -> {
                scope.launch {
                    removeNeverTranslateSite(context, action.tabId, action.origin)
                }
            }

            is TranslationsAction.UpdatePageSettingAction -> {
                when (action.operation) {
                    TranslationPageSettingOperation.UPDATE_ALWAYS_OFFER_POPUP ->
                        scope.launch {
                            updateAlwaysOfferPopupPageSetting(
                                setting = action.setting,
                            )
                        }

                    TranslationPageSettingOperation.UPDATE_ALWAYS_TRANSLATE_LANGUAGE ->
                        scope.launch {
                            updateLanguagePageSetting(
                                context = context,
                                tabId = action.tabId,
                                setting = action.setting,
                                settingType = LanguageSetting.ALWAYS,
                            )
                        }

                    TranslationPageSettingOperation.UPDATE_NEVER_TRANSLATE_LANGUAGE ->
                        scope.launch {
                            updateLanguagePageSetting(
                                context = context,
                                tabId = action.tabId,
                                setting = action.setting,
                                settingType = LanguageSetting.NEVER,
                            )
                        }

                    TranslationPageSettingOperation.UPDATE_NEVER_TRANSLATE_SITE ->
                        scope.launch {
                            updateNeverTranslateSitePageSetting(
                                context = context,
                                tabId = action.tabId,
                                setting = action.setting,
                            )
                        }
                }
            }

            else -> {
                // no-op
            }
        }

        // Continue to post process actions
        next(action)
    }

    /**
     * Checks if the translations engine supports the device architecture and updates the state.
     *
     * @param context Context to use to dispatch to the store.
     */
    private fun requestEngineSupport(
        context: MiddlewareContext<BrowserState, BrowserAction>,
    ) {
        engine.isTranslationsEngineSupported(
            onSuccess = { isEngineSupported ->
                context.store.dispatch(
                    TranslationsAction.SetEngineSupportedAction(
                        isEngineSupported = isEngineSupported,
                    ),
                )
                logger.info("Success requesting engine support.")
            },

            onError = { error ->
                context.store.dispatch(
                    TranslationsAction.EngineExceptionAction(
                        error = TranslationError.UnknownEngineSupportError(error),
                    ),
                )
                logger.error("Error requesting engine support: ", error)
            },
        )
    }

    /**
     * Retrieves the list of supported languages using [scope] and dispatches the result to the
     * store via [TranslationsAction.SetSupportedLanguagesAction] or else dispatches the failure
     * [TranslationsAction.TranslateExceptionAction].
     *
     * @param context Context to use to dispatch to the store.
     * @param tabId Tab ID associated with the request.
     */
    private fun requestSupportedLanguages(
        context: MiddlewareContext<BrowserState, BrowserAction>,
        tabId: String,
    ) {
        engine.getSupportedTranslationLanguages(

            onSuccess = {
                context.store.dispatch(
                    TranslationsAction.SetSupportedLanguagesAction(
                        tabId = tabId,
                        supportedLanguages = it,
                    ),
                )
                logger.info("Success requesting supported languages.")
            },

            onError = {
                context.store.dispatch(
                    TranslationsAction.TranslateExceptionAction(
                        tabId = tabId,
                        operation = TranslationOperation.FETCH_SUPPORTED_LANGUAGES,
                        translationError = TranslationError.CouldNotLoadLanguagesError(it),
                    ),
                )
                logger.error("Error requesting supported languages: ", it)
            },
        )
    }

    /**
     * Retrieves the list of never translate sites using [scope] and dispatches the result to the
     * store via [TranslationsAction.SetNeverTranslateSitesAction] or else dispatches the failure
     * [TranslationsAction.TranslateExceptionAction].
     *
     * @param context Context to use to dispatch to the store.
     * @param tabId Tab ID associated with the request.
     */
    private fun getNeverTranslateSites(
        context: MiddlewareContext<BrowserState, BrowserAction>,
        tabId: String,
    ) {
        engine.getNeverTranslateSiteList(
            onSuccess = {
                context.store.dispatch(
                    TranslationsAction.SetNeverTranslateSitesAction(
                        tabId = tabId,
                        neverTranslateSites = it,
                    ),
                )
                logger.info("Success requesting never translate sites.")
            },

            onError = {
                context.store.dispatch(
                    TranslationsAction.TranslateExceptionAction(
                        tabId = tabId,
                        operation = TranslationOperation.FETCH_NEVER_TRANSLATE_SITES,
                        translationError = TranslationError.CouldNotLoadNeverTranslateSites(it),
                    ),
                )
                logger.error("Error requesting never translate sites: ", it)
            },
        )
    }

    /**
     * Removes the site from the list of never translate sites using [scope] and dispatches the result to the
     * store via [TranslationsAction.SetNeverTranslateSitesAction] or else dispatches the failure
     * [TranslationsAction.TranslateExceptionAction].
     *
     * @param context Context to use to dispatch to the store.
     * @param tabId Tab ID associated with the request.
     * @param origin A site origin URI that will have the specified never translate permission set.
     */
    private fun removeNeverTranslateSite(
        context: MiddlewareContext<BrowserState, BrowserAction>,
        tabId: String,
        origin: String,
    ) {
        engine.setNeverTranslateSpecifiedSite(
            origin = origin,
            setting = false,
            onSuccess = {
                logger.info("Success requesting never translate sites.")

                // Fetch page settings to ensure the state matches the engine.
                context.store.dispatch(
                    TranslationsAction.OperationRequestedAction(
                        tabId = tabId,
                        operation = TranslationOperation.FETCH_PAGE_SETTINGS,
                    ),
                )
            },
            onError = {
                logger.error("Error removing site from never translate list: ", it)

                // Fetch never translate sites to ensure the state matches the engine.
                context.store.dispatch(
                    TranslationsAction.OperationRequestedAction(
                        tabId = tabId,
                        operation = TranslationOperation.FETCH_NEVER_TRANSLATE_SITES,
                    ),
                )
            },
        )
    }

    /**
     * Retrieves the page settings using [scope] and dispatches the result to the
     * store via [TranslationsAction.SetPageSettingsAction] or else dispatches the failure
     * [TranslationsAction.TranslateExceptionAction].
     *
     * @param context Context to use to dispatch to the store.
     * @param tabId Tab ID associated with the request.
     */
    private suspend fun requestPageSettings(
        context: MiddlewareContext<BrowserState, BrowserAction>,
        tabId: String,
    ) {
        // Always offer setting
        val alwaysOfferPopup: Boolean = engine.getTranslationsOfferPopup()

        // Page language settings
        val pageLanguage = context.store.state.findTab(tabId)
            ?.translationsState?.translationEngineState?.detectedLanguages?.documentLangTag
        val setting = pageLanguage?.let { getLanguageSetting(it) }
        val alwaysTranslateLanguage = setting?.toBoolean(LanguageSetting.ALWAYS)
        val neverTranslateLanguage = setting?.toBoolean(LanguageSetting.NEVER)

        // Never translate site
        val engineSession = context.store.state.findTab(tabId)
            ?.engineState?.engineSession
        val neverTranslateSite = engineSession?.let { getNeverTranslateSiteSetting(it) }

        if (
            alwaysTranslateLanguage != null &&
            neverTranslateLanguage != null &&
            neverTranslateSite != null
        ) {
            context.store.dispatch(
                TranslationsAction.SetPageSettingsAction(
                    tabId = tabId,
                    pageSettings = TranslationPageSettings(
                        alwaysOfferPopup = alwaysOfferPopup,
                        alwaysTranslateLanguage = alwaysTranslateLanguage,
                        neverTranslateLanguage = neverTranslateLanguage,
                        neverTranslateSite = neverTranslateSite,
                    ),
                ),
            )
        } else {
            // Any null values indicate something went wrong, alert an error occurred
            context.store.dispatch(
                TranslationsAction.TranslateExceptionAction(
                    tabId = tabId,
                    operation = TranslationOperation.FETCH_PAGE_SETTINGS,
                    translationError = TranslationError.CouldNotLoadPageSettingsError(null),
                ),
            )
        }
    }

    /**
     * Fetches the always or never language setting synchronously from the engine. Will
     * return null if an error occurs.
     *
     * @param pageLanguage Page language to check the translation preferences for.
     * @return The page translate language setting or null.
     */
    private suspend fun getLanguageSetting(pageLanguage: String): LanguageSetting? {
        return suspendCoroutine { continuation ->
            engine.getLanguageSetting(
                languageCode = pageLanguage,

                onSuccess = { setting ->
                    continuation.resume(setting)
                },

                onError = {
                    continuation.resume(null)
                },
            )
        }
    }

    /**
     * Fetches the never translate site setting synchronously from the [EngineSession]. Will
     * return null if an error occurs.
     *
     * @param engineSession With page context on how to complete this operation.
     * @return The never translate site setting from the [EngineSession] or null.
     */
    private suspend fun getNeverTranslateSiteSetting(engineSession: EngineSession): Boolean? {
        return suspendCoroutine { continuation ->
            engineSession.getNeverTranslateSiteSetting(
                onResult = { setting ->
                    continuation.resume(setting)
                },
                onException = {
                    continuation.resume(null)
                },
            )
        }
    }

    /**
     * Updates the always offer popup setting with the [Engine].
     *
     * @param setting The value of the always offer setting to update.
     */
    private fun updateAlwaysOfferPopupPageSetting(
        setting: Boolean,
    ) {
        logger.info("Setting the always offer translations popup preference.")
        engine.setTranslationsOfferPopup(setting)
    }

    /**
     * Updates the language settings with the [Engine].
     *
     * If an error occurs, then the method will request the page settings be re-fetched and set on
     * the browser store.
     *
     * @param context The context used to request the page settings.
     * @param tabId Tab ID associated with the request.
     * @param setting The value of the always offer setting to update.
     * @param settingType If the boolean to update is from the
     * [LanguageSetting.ALWAYS] or [LanguageSetting.NEVER] perspective.
     */
    private fun updateLanguagePageSetting(
        context: MiddlewareContext<BrowserState, BrowserAction>,
        tabId: String,
        setting: Boolean,
        settingType: LanguageSetting,
    ) {
        logger.info("Preparing to update the translations language preference.")

        val pageLanguage = context.store.state.findTab(tabId)
            ?.translationsState?.translationEngineState?.detectedLanguages?.documentLangTag
        val convertedSetting = settingType.toLanguageSetting(setting)

        if (pageLanguage == null || convertedSetting == null) {
            logger.info("An issue occurred while preparing to update the language setting.")

            // Fetch page settings to ensure the state matches the engine.
            context.store.dispatch(
                TranslationsAction.OperationRequestedAction(
                    tabId = tabId,
                    operation = TranslationOperation.FETCH_PAGE_SETTINGS,
                ),
            )
        } else {
            updateLanguageSetting(context, tabId, pageLanguage, convertedSetting)
        }
    }

    /**
     * Updates the language settings with the [Engine].
     *
     * If an error occurs, then the method will request the page settings be re-fetched and set on
     * the browser store.
     *
     * @param context The context used to request the page settings.
     * @param tabId Tab ID associated with the request.
     * @param languageCode The BCP-47 language to update.
     * @param setting The new language setting for the [languageCode].
     */
    private fun updateLanguageSetting(
        context: MiddlewareContext<BrowserState, BrowserAction>,
        tabId: String,
        languageCode: String,
        setting: LanguageSetting,
    ) {
        logger.info("Setting the translations language preference.")

        engine.setLanguageSetting(
            languageCode = languageCode,
            languageSetting = setting,

            onSuccess = {
                logger.info("Successfully updated the language preference.")
            },

            onError = {
                logger.error("Could not update the language preference.", it)

                // Fetch page settings to ensure the state matches the engine.
                context.store.dispatch(
                    TranslationsAction.OperationRequestedAction(
                        tabId = tabId,
                        operation = TranslationOperation.FETCH_PAGE_SETTINGS,
                    ),
                )
            },
        )
    }

    /**
     * Updates the never translate site settings with the [EngineSession] and ensures the global
     * list of never translate sites remains in sync.
     *
     * If an error occurs, then the method will request the page settings be re-fetched and set on
     * the browser store.
     *
     * Note: This method should be used when on the same page as the requested change.
     *
     * @param context The context used to request the page settings.
     * @param tabId Tab ID associated with the request.
     * @param setting The value of the site setting to update.
     */
    private fun updateNeverTranslateSitePageSetting(
        context: MiddlewareContext<BrowserState, BrowserAction>,
        tabId: String,
        setting: Boolean,
    ) {
        val engineSession = context.store.state.findTab(tabId)
            ?.engineState?.engineSession

        if (engineSession == null) {
            logger.error("Did not receive an engine session to set the never translate site preference.")
        } else {
            engineSession.setNeverTranslateSiteSetting(
                setting = setting,
                onResult = {
                    logger.info("Successfully updated the never translate site preference.")

                    // Ensure the global sites store is in-sync with the page settings.
                    context.store.dispatch(
                        TranslationsAction.OperationRequestedAction(
                            tabId = tabId,
                            operation = TranslationOperation.FETCH_NEVER_TRANSLATE_SITES,
                        ),
                    )
                },
                onException = {
                    logger.error("Could not update the never translate site preference.", it)

                    // Fetch page settings to ensure the state matches the engine.
                    context.store.dispatch(
                        TranslationsAction.OperationRequestedAction(
                            tabId = tabId,
                            operation = TranslationOperation.FETCH_PAGE_SETTINGS,
                        ),
                    )
                },
            )
        }
    }
}
