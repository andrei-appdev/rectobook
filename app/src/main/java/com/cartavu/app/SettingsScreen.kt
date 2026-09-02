package com.cartavu.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.cartavu.app.ui.theme.IndustryBackground
import com.cartavu.app.ui.theme.IndustryDivider
import com.cartavu.app.ui.theme.IndustryNeutral200
import com.cartavu.app.ui.theme.SectionLabel
import com.cartavu.app.ui.theme.TabularNums
import kotlin.math.roundToInt

/**
 * The app's own settings: how a page is drawn, and the way through to About.
 *
 * Everything here is a property of the reader rather than of a document -
 * someone reading faded scans is usually reading a shelf of them - which is why
 * it is reached from the library rather than from a document.
 *
 * The tone controls used to be a sheet over the page they adjusted, judged
 * against the scan itself. Off the reader there is no page to judge against, so
 * the screen draws one: the same sheet a library card carries, in the tone being
 * set, moving as the controls move.
 */
@Composable
fun SettingsScreen(
    mode: PageColorMode,
    contrast: Float,
    onModeChange: (PageColorMode) -> Unit,
    onContrastChange: (Float) -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit,
) {
    val tone = remember(mode, contrast) { PageTone(mode, contrast) }

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
            IconAction(BackIcon, stringResource(R.string.settings_close), onBack)
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Hairline()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 28.dp),
        ) {
            ToneSettings(
                mode = mode,
                contrast = contrast,
                onModeChange = onModeChange,
                onContrastChange = onContrastChange,
            )

            SampleSheet(tone = tone, modifier = Modifier.padding(top = 14.dp))

            AboutRow(onClick = onAbout, modifier = Modifier.padding(top = 28.dp))
        }
    }
}

/**
 * The two settings themselves: which way a page is toned, and how far its
 * levels are stretched.
 *
 * Written once and shown twice - here, with a sample sheet drawn to judge them
 * against, and in the reader's own panel, where the page being read is what
 * they are judged against. Neither copy can drift from the other.
 */
@Composable
fun ToneSettings(
    mode: PageColorMode,
    contrast: Float,
    onModeChange: (PageColorMode) -> Unit,
    onContrastChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = upperStringResource(R.string.settings_color_mode),
            style = SectionLabel,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        )
        SegmentedChoice(
            options = PageColorMode.entries,
            selected = mode,
            label = { upperStringResource(it.labelResource) },
            onSelect = onModeChange,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = upperStringResource(R.string.settings_contrast),
                style = SectionLabel,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (contrast <= 0f) {
                    stringResource(R.string.contrast_as_scanned)
                } else {
                    stringResource(R.string.contrast_amount, (contrast * 100).roundToInt())
                },
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 0.04.em,
                    fontFeatureSettings = TabularNums,
                ),
                textAlign = TextAlign.End,
            )
        }
        ValueSlider(
            value = contrast,
            label = stringResource(R.string.settings_contrast),
            onValueChange = onContrastChange,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

/**
 * The sample sheet, and how much larger than a library card's it is drawn. Big
 * enough to judge a tone by, and still the proportions of a page.
 */
private const val SAMPLE_SCALE = 1.9f
private val SampleWidth = ThumbnailWidth * SAMPLE_SCALE
private val SampleHeight = ThumbnailHeight * SAMPLE_SCALE

/** How deep the desk the sample lies on is drawn. */
private val SampleDesk = 24.dp

/**
 * A page in the tone being set: the library card's drawn sheet, with its paper,
 * its edge and its two inks all put through the same filter the reader would
 * draw a real page through, and laid on the desk the reader would lay it on.
 *
 * The reader's arrangement in miniature rather than a swatch of colour, because
 * a tone is judged as a page - what matters is whether the ink reads on the
 * paper and whether the sheet reads on the desk, and neither question can be
 * asked of a rectangle.
 *
 * The inks are the greys a 60% and 32% black over white actually come to, so
 * the contrast stretch has midtones to move: a sheet of pure black on pure
 * white would sit still while the slider travelled.
 */
@Composable
private fun SampleSheet(tone: PageTone, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SampleHeight + SampleDesk * 2)
            .background(tone.desk ?: IndustryNeutral200),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(SampleWidth)
                .height(SampleHeight)
                .background(tone.paper)
                .border(1.dp, tone.ink.copy(alpha = 0.14f)),
        ) {
            PageGlyph(
                headingInk = tone.grey(0.4f),
                bodyInk = tone.grey(0.68f),
                scale = SAMPLE_SCALE,
            )
        }
    }
}

/** The way through to About: the design's `.btn`, run the width of the screen. */
@Composable
private fun AboutRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, IndustryDivider)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StrokeIcon(InfoIcon, size = 17.dp)
        Text(
            text = upperStringResource(R.string.settings_about),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.04.em),
        )
        Spacer(modifier = Modifier.weight(1f))
        StrokeIcon(ForwardIcon, size = 17.dp)
    }
}

private val PageColorMode.labelResource: Int
    get() = when (this) {
        PageColorMode.NORMAL -> R.string.color_mode_normal
        PageColorMode.INVERTED -> R.string.color_mode_inverted
        PageColorMode.SEPIA -> R.string.color_mode_sepia
    }
