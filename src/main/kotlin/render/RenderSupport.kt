package render

import org.openrndr.ffmpeg.ScreenRecorder
import java.io.File

/**
 * Website media renderers
 *
 * Each RenderXX program in this package is a non-interactive variant of the
 * matching sketch in src/main/kotlin/genuary. Instead of reacting to the mouse
 * and keyboard it plays a scripted version of the interaction from a fixed
 * random seed, records a fixed number of frames and exits.
 *
 * Raw output lands in website/media-raw/ (git-ignored). Run everything with:
 *
 *   ./gradlew renderSiteMedia     # render all raw clips (needs a display; use xvfb-run on a headless box)
 *   ./gradlew optimizeSiteMedia   # compress to website/static/ loops + stills (needs ffmpeg on PATH)
 */
const val SITE_FPS = 60

fun siteRecorder(slug: String, frames: Long): ScreenRecorder {
    val out = File("website/media-raw/$slug.mp4")
    out.parentFile.mkdirs()
    if (out.exists()) out.delete()
    return ScreenRecorder().apply {
        outputFile = out.path
        frameRate = SITE_FPS
        maximumFrames = frames
        quitAfterMaximum = true
        outputToVideo = true
    }
}
