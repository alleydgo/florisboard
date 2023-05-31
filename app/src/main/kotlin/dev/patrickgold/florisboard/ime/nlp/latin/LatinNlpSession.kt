/*
 * Copyright (C) 2023 Patrick Goldinger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.nlp.latin

import android.view.textservice.SuggestionsInfo
import dev.patrickgold.florisboard.ime.keyboard.KeyProximityChecker
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionRequestFlags
import dev.patrickgold.florisboard.lib.io.FsFile
import dev.patrickgold.florisboard.lib.kotlin.tryOrNull
import dev.patrickgold.florisboard.native.NativeInstanceWrapper
import dev.patrickgold.florisboard.native.NativePtr
import dev.patrickgold.florisboard.native.NativeStr
import dev.patrickgold.florisboard.native.NativeList
import dev.patrickgold.florisboard.native.toJavaString
import dev.patrickgold.florisboard.native.toNativeList
import dev.patrickgold.florisboard.native.toNativeStr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class LatinNlpSessionConfig(
    val primaryLocale: String,
    val secondaryLocales: List<String>,
    @SerialName("baseDictionaries")
    val baseDictionaryPaths: List<String>,
    @SerialName("userDictionary")
    val userDictionaryPath: String,
    val predictionWeights: LatinPredictionWeights,
    val keyProximityChecker: KeyProximityChecker,
)

@JvmInline
value class LatinNlpSession(private val _nativePtr: NativePtr = nativeInit()) : NativeInstanceWrapper {
    suspend fun loadFromConfigFile(configFile: FsFile) {
        withContext(Dispatchers.IO) {
            nativeLoadFromConfigFile(_nativePtr, configFile.absolutePath.toNativeStr())
        }
    }

    suspend fun spell(
        word: String,
        prevWords: List<String>,
        flags: SuggestionRequestFlags,
    ): SpellingResult {
        return withContext(Dispatchers.IO) {
            val nativeSpellingResultStr = nativeSpell(
                nativePtr = _nativePtr,
                word = word.toNativeStr(),
                prevWords = prevWords.toNativeList(),
                flags = flags.toInt(),
            ).toJavaString()
            val nativeSpellingResult = Json.decodeFromString<NativeSpellingResult>(nativeSpellingResultStr)
            return@withContext tryOrNull {
                SpellingResult(
                    SuggestionsInfo(
                        nativeSpellingResult.suggestionAttributes,
                        nativeSpellingResult.suggestions.toTypedArray(),
                    )
                )
            } ?: SpellingResult.unspecified()
        }
    }

    override fun nativePtr(): NativePtr {
        return _nativePtr
    }

    override fun dispose() {
        nativeDispose(_nativePtr)
    }

    @Serializable
    private data class NativeSpellingResult(
        val suggestionAttributes: Int,
        val suggestions: List<String>,
    )

    companion object CXX {
        external fun nativeInit(): NativePtr
        external fun nativeDispose(nativePtr: NativePtr)

        external fun nativeLoadFromConfigFile(nativePtr: NativePtr, configPath: NativeStr)
        external fun nativeSpell(
            nativePtr: NativePtr,
            word: NativeStr,
            prevWords: NativeList,
            flags: Int,
        ): NativeStr
        //external fun nativeSuggest(word: NativeStr, prevWords: List<NativeStr>, flags: Int)
        //external fun nativeTrain(sentence: List<NativeStr>, maxPrevWords: Int)
    }
}
