/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.browser.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import mozilla.components.compose.base.theme.AcornTheme

private val ROUNDED_CORNER_SHAPE = RoundedCornerShape(8.dp)

/**
 * Sub-component of the [BrowserToolbar] responsible for displaying the URL and related
 * controls ("display mode").
 *
 * @param url The URL to be displayed.
 * @param colors The color scheme to use in the browser display toolbar.
 * @param onUrlClicked Will be called when the user clicks on the URL.
 * @param onMenuClicked Will be called when the user clicks on the menu button.
 * @param browserActions Additional browser actions to be displayed on the right side of the toolbar
 * (outside of the URL bounding box) in display mode. Also see:
 * [MDN docs](https://developer.mozilla.org/en-US/Add-ons/WebExtensions/user_interface/Browser_action)
 */
@Composable
fun BrowserDisplayToolbar(
    url: String,
    colors: BrowserDisplayToolbarColors,
    onUrlClicked: () -> Unit = {},
    onMenuClicked: () -> Unit = {},
    browserActions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .background(color = colors.background)
            .padding(all = 8.dp)
            .background(
                color = colors.urlBackground,
                shape = ROUNDED_CORNER_SHAPE,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            url,
            color = colors.text,
            modifier = Modifier
                .clickable { onUrlClicked() }
                .padding(8.dp)
                .weight(1f)
                .align(Alignment.CenterVertically),
            maxLines = 1,
        )

        browserActions()

        Button(onClick = { onMenuClicked() }) {
            Text(":")
        }
    }
}

@PreviewLightDark
@Composable
private fun BrowserDisplayToolbarPreview() {
    AcornTheme {
        BrowserDisplayToolbar(
            url = "http://www.mozilla.org",
            colors = BrowserDisplayToolbarColors(
                background = AcornTheme.colors.layer1,
                urlBackground = AcornTheme.colors.layer3,
                text = AcornTheme.colors.textPrimary,
            ),
        )
    }
}
