package com.rectobook.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.rectobook.app.ui.theme.IndustryBackground
import com.rectobook.app.ui.theme.IndustryDivider
import com.rectobook.app.ui.theme.SectionLabel
import com.rectobook.app.ui.theme.onSurfaceAlpha

/**
 * What the settings' About row opens: the app's name and version, what it is,
 * the licence it ships under, where its source is, where to report a problem,
 * and what it is built on.
 *
 * A screen rather than a dialog, as the design draws it - it is where anything
 * that is true of the app rather than of a document ends up.
 *
 * The licence and the source link are not decoration. DjVuLibre is GPL and
 * compiled in, so a build handed to anyone has to say so and offer its source,
 * and this is the one place in the app that a reader can find both.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val open = { url: String ->
        // A device with no browser at all is rare but real - a kiosk, a
        // work profile - and a link that cannot go anywhere should do nothing
        // rather than take the app down.
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        Unit
    }

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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 28.dp, bottom = 28.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
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
                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            AboutSection(R.string.about_licence_title, Modifier.padding(top = 32.dp)) {
                BodyText(stringResource(R.string.about_licence_body))
                LinkRow(R.string.about_licence_link, Modifier.padding(top = 12.dp)) { open(LicenceUrl) }
            }

            AboutSection(R.string.about_source_title, Modifier.padding(top = 24.dp)) {
                LinkRow(R.string.about_source_link) { open(SourceUrl) }
            }

            AboutSection(R.string.about_feedback_title, Modifier.padding(top = 24.dp)) {
                LinkRow(R.string.about_feedback_link) { open(IssuesUrl) }
            }

            AboutSection(R.string.about_credits_title, Modifier.padding(top = 24.dp)) {
                BodyText(stringResource(R.string.about_credit_djvulibre))
                BodyText(stringResource(R.string.about_credit_pdfium), Modifier.padding(top = 6.dp))
                BodyText(stringResource(R.string.about_credit_fira), Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** A section of the page under the design's tracked capitals label. */
@Composable
private fun AboutSection(
    title: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = upperStringResource(title),
            style = SectionLabel,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        )
        content()
    }
}

@Composable
private fun BodyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = onSurfaceAlpha(0.8f),
        modifier = modifier,
    )
}

/**
 * A way out of the app, drawn as the settings' About row is - the design's
 * `.btn` run the width of the screen - so it reads as something to press
 * rather than as text that happens to be blue.
 */
@Composable
private fun LinkRow(label: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, IndustryDivider)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = upperStringResource(label),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.04.em),
            modifier = Modifier.weight(1f),
        )
        StrokeIcon(ForwardIcon, size = 17.dp)
    }
}

/** Read from the manifest so it cannot drift from what was installed. */
private const val AppVersion = BuildConfig.VERSION_NAME

private const val SourceUrl = "https://github.com/andrei-appdev/rectobook"
private const val IssuesUrl = "$SourceUrl/issues"

/** Version 2 by name: "or any later version" is the app's choice, but 2 is what it ships with. */
private const val LicenceUrl = "https://www.gnu.org/licenses/old-licenses/gpl-2.0.html"
