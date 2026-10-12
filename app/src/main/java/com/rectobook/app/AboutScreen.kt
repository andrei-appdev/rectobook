package com.rectobook.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.rectobook.app.ui.theme.IndustryBackground
import com.rectobook.app.ui.theme.onSurfaceAlpha

/**
 * What the library's info mark opens: the app's name and version, and nothing
 * else yet.
 *
 * A screen rather than a dialog, as the design draws it - it is where anything
 * that is true of the app rather than of a document will end up.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IndustryBackground)
            .clearOfSystemBars(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconAction(BackIcon, stringResource(R.string.reader_back_to_library), onBack)
            Text(
                text = stringResource(R.string.about_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Hairline()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = upperStringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = upperStringResource(R.string.about_version, AppVersion),
                style = MaterialTheme.typography.bodySmall,
                letterSpacing = 0.1.em,
                color = onSurfaceAlpha(0.45f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Read from the manifest so it cannot drift from what was installed. */
private const val AppVersion = BuildConfig.VERSION_NAME
