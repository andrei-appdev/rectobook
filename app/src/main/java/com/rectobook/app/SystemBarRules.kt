package com.rectobook.app

/**
 * When a system bar is on screen while a document is open.
 *
 * [WITH_BARS] ties it to the reader's own top and bottom bars, so one tap on
 * the page clears everything off it and another brings it all back.
 */
enum class BarVisibility { ALWAYS, WITH_BARS, NEVER }

/**
 * What the reader does with the status bar and the navigation bar, in each
 * orientation.
 *
 * All four were decided in code until now, and the defaults here are what that
 * code did: the clock stays in portrait, where the page is short of width and
 * hiding a strip above it buys nothing, and goes in landscape, where the page
 * is short of height and the strip costs a visible slice of it; the buttons go
 * with the chrome either way, being buttons nobody is using while reading.
 *
 * They are four settings rather than one because the two bars are not alike -
 * one carries the clock, the other the way out of the app - and because a
 * phone held one way up is not the same reading surface as the other.
 */
data class SystemBarRules(
    val statusPortrait: BarVisibility = BarVisibility.ALWAYS,
    val statusLandscape: BarVisibility = BarVisibility.WITH_BARS,
    val navigationPortrait: BarVisibility = BarVisibility.WITH_BARS,
    val navigationLandscape: BarVisibility = BarVisibility.WITH_BARS,
) {
    fun status(portrait: Boolean): BarVisibility =
        if (portrait) statusPortrait else statusLandscape

    fun navigation(portrait: Boolean): BarVisibility =
        if (portrait) navigationPortrait else navigationLandscape
}
