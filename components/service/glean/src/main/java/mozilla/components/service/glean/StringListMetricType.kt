/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.service.glean

import android.support.annotation.VisibleForTesting
import kotlinx.coroutines.launch
import mozilla.components.service.glean.storages.StringListsStorageEngine
import mozilla.components.support.base.log.logger.Logger

/**
 * This implements the developer facing API for recording string list metrics.
 *
 * Instances of this class type are automatically generated by the parsers at build time,
 * allowing developers to record values that were previously registered in the metrics.yaml file.
 *
 * The string list API exposes the [add] and [set] methods, which take care of validating the input
 * data and making sure that limits are enforced.
 */
data class StringListMetricType(
    override val disabled: Boolean,
    override val category: String,
    override val lifetime: Lifetime,
    override val name: String,
    override val sendInPings: List<String>
) : CommonMetricData {

    override val defaultStorageDestinations: List<String> = listOf("metrics")

    private val logger = Logger("glean/StringListMetricType")

    companion object {
        // Maximum length of any passed value string, in characters.
        const val MAX_STRING_LENGTH = 50
    }

    /**
     * Appends a string value to one or more string list metric stores.  If the string exceeds the
     * maximum string length, defined as [MAX_STRING_LENGTH], it will be truncated.
     *
     * If adding the string to the lists would exceed the maximum value defined as
     * [StringListsStorageEngine.MAX_LIST_LENGTH_VALUE], then the storage engine will drop the new
     * value and it will not be added to the list.
     *
     * @param value This is a user defined string value. The maximum length of
     *              this string is [MAX_STRING_LENGTH].
     */
    fun add(value: String) {
        // TODO report errors through other special metrics handled by the SDK. See bug 1499761.

        if (!shouldRecord(logger)) {
            return
        }

        val truncatedValue = value.let {
            if (it.length > MAX_STRING_LENGTH) {
                logger.warn("$category.$name - string too long ${it.length} > $MAX_STRING_LENGTH")
                return@let it.substring(0, MAX_STRING_LENGTH)
            }
            it
        }

        Dispatchers.API.launch {
            // Delegate storing the string to the storage engine.
            StringListsStorageEngine.add(
                metricData = this@StringListMetricType,
                value = truncatedValue
            )
        }
    }

    /**
     * Sets a string list to one or more metric stores.
     *
     * @param value This is a user defined string list. The maximum length of each string in the
     *              list is defined by [MAX_STRING_LENGTH], while the maximum length of the list itself is
     *              defined by [StringListsStorageEngine.MAX_LIST_LENGTH_VALUE].  If a longer list is passed
     *              into this function, then the additional values will be dropped from the list and the list,
     *              up to the [StringListsStorageEngine.MAX_LIST_LENGTH_VALUE], will still be recorded.
     */
    fun set(value: List<String>) {
        // TODO report errors through other special metrics handled by the SDK. See bug 1499761.

        if (!shouldRecord(logger)) {
            return
        }

        val stringList = value.map {
            if (it.length > MAX_STRING_LENGTH) {
                logger.warn("$category.$name - string too long ${it.length} > $MAX_STRING_LENGTH")
            }
            it.take(MAX_STRING_LENGTH)
        }

        Dispatchers.API.launch {
            // Delegate storing the string list to the storage engine.
            StringListsStorageEngine.set(
                metricData = this@StringListMetricType,
                value = stringList
            )
        }
    }

    /**
     * Tests whether a value is stored for the metric for testing purposes only
     *
     * @param pingName represents the name of the ping to retrieve the metric for.  Defaults
     *                 to the either the first value in [defaultStorageDestinations] or the first
     *                 value in [sendInPings]
     * @return true if metric value exists, otherwise false
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun testHasValue(pingName: String = getStorageNames().first()): Boolean {
        return StringListsStorageEngine.getSnapshot(pingName, false)?.get(identifier) != null
    }

    /**
     * Returns the stored value for testing purposes only
     *
     * @param pingName represents the name of the ping to retrieve the metric for.  Defaults
     *                 to the either the first value in [defaultStorageDestinations] or the first
     *                 value in [sendInPings]
     * @return value of the stored metric
     * @throws [NullPointerException] if no value is stored
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun testGetValue(pingName: String = getStorageNames().first()): List<String> {
        return StringListsStorageEngine.getSnapshot(pingName, false)!![identifier]!!
    }
}
