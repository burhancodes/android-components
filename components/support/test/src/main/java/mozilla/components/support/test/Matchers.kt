/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.support.test

import org.mockito.Mockito

/**
 * Mockito matcher that matches <strong>anything</strong>, including nulls and varargs.
 *
 * (The version from Mockito doesn't work correctly with Kotlin code.)
 */
fun <T> any(): T {
    Mockito.any<T>()
    return uninitialized()
}

/**
 * Mockito matcher that matches if the argument is the same as the provided value.
 *
 * (The version from Mockito doesn't work correctly with Kotlin code.)
 */
fun <T> eq(value: T): T {
    return Mockito.eq(value) ?: value
}

@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T
