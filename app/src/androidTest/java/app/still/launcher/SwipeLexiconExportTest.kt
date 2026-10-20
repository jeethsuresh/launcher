package app.still.launcher

import androidx.test.platform.app.InstrumentationRegistry
import helium314.keyboard.latin.DictionaryFactory
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.zip.GZIPOutputStream

/** Maintainer-only export from the pinned, bundled HeliBoard binary dictionaries. */
class SwipeLexiconExportTest {
    @Test fun exportPinnedEnglishLexicons() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("exportLexicon") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (locale in listOf(Locale.US, Locale.UK)) {
            val dictionary = DictionaryFactory.createMainDictionaryCollection(context, locale, false)
            try {
                val words = dictionary.nativeGestureWords.sortedByDescending { it.frequency }
                    .distinctBy { it.text.lowercase(Locale.ROOT) }.take(60_000)
                val file = File(context.cacheDir, "swipe-${locale.toLanguageTag()}.tsv.gz")
                GZIPOutputStream(file.outputStream()).bufferedWriter().use { writer ->
                    for (word in words) if ('\t' !in word.text && '\n' !in word.text)
                        writer.appendLine("${word.frequency}\t${word.text}")
                }
                android.util.Log.i("StillLexiconExport", "${locale.toLanguageTag()}: ${words.size} words, ${file.length()} bytes")
            } finally { dictionary.close() }
        }
    }
}
