package com.pbt.nabratts
object ArPhonemizer {
    private val buckwToArabicMap = mapOf(
        "b" to "\u0628", "*" to "\u0630", "T" to "\u0637", "m" to "\u0645",
        "t" to "\u062a", "r" to "\u0631", "Z" to "\u0638", "n" to "\u0646",
        "^" to "\u062b", "z" to "\u0632", "E" to "\u0639", "h" to "\u0647",
        "j" to "\u062c", "s" to "\u0633", "g" to "\u063a", "H" to "\u062d",
        "q" to "\u0642", "f" to "\u0641", "x" to "\u062e", "S" to "\u0635",
        "\$" to "\u0634", "d" to "\u062f", "D" to "\u0636", "k" to "\u0643",
        ">" to "\u0623", "'" to "\u0621", "}" to "\u0626", "&" to "\u0624",
        "<" to "\u0625", "|" to "\u0622", "A" to "\u0627", "Y" to "\u0649",
        "p" to "\u0629", "y" to "\u064a", "l" to "\u0644", "w" to "\u0648",
        "F" to "\u064b", "N" to "\u064c", "K" to "\u064d", "a" to "\u064e",
        "u" to "\u064f", "i" to "\u0650", "~" to "\u0651", "o" to "\u0652",
        "{" to "\u0671"
    )
    private val arabicToBuckwMap = buckwToArabicMap.entries.associate { (k, v) -> v to k }
    fun arabToBuckw(arab: String): String {
        return arab.map { arabicToBuckwMap[it.toString()] ?: it.toString() }.joinToString("")
    }
    private val unambiguousConsonantMap = mapOf(
        "b" to "b", "*" to "*", "T" to "T", "m" to "m",
        "t" to "t", "r" to "r", "Z" to "Z", "n" to "n",
        "^" to "^", "z" to "z", "E" to "E", "h" to "h",
        "j" to "j", "s" to "s", "g" to "g", "H" to "H",
        "q" to "q", "f" to "f", "x" to "x", "S" to "S",
        "\$" to "\$", "d" to "d", "D" to "D", "k" to "k",
        ">" to "<", "'" to "<", "}" to "<", "&" to "<",
        "<" to "<"
    )
    private const val diacritics = "oauiFNK~"
    private const val diacriticsWithoutShadda = "oauiFNK"
    private const val emphatics = "DSTZgxq"
    private const val forwardEmphatics = "gx"
    private const val consonants = "><}&'bt^jHxd*rzs\$SDTZEgfqklmnh|"
    private const val punctuation = ".:,!?"
    private val preprocessingReplacements = mapOf(
        "AF" to "F",
        "\u0640" to "",
        "o" to "",
        "aA" to "A",
        "aY" to "Y",
        " A" to " ",
        "F" to "an",
        "N" to "un",
        "K" to "in",
        "|" to ">A",
        "i~" to "~i",
        "a~" to "~a",
        "u~" to "~u",
        "Ai" to "<i",
        "Aa" to ">a",
        "Au" to ">u"
    )
    private fun preprocessUtterance(utterance: String): List<String> {
        var result = utterance
        for ((key, value) in preprocessingReplacements) {
            result = result.replace(key, value)
        }
        result = Regex("^>([^auAw])").replace(result) { ">a${it.groupValues[1]}" }
        result = Regex(" >([^auAw ])").replace(result) { " >a${it.groupValues[1]}" }
        result = Regex("<([^i])").replace(result) { "<i${it.groupValues[1]}" }
        result = Regex("(\\S)([.?,!])").replace(result) { "${it.groupValues[1]} ${it.groupValues[2]}" }
        return result.split(" ").filter { it.isNotEmpty() }
    }
    private val fixedWords = mapOf(
        "h*A" to listOf("hA*A", "hA*a"),
        "h*h" to listOf("hA*ihi", "hA*ih"),
        "h*An" to listOf("hA*Ani", "hA*An"),
        "h&lA'" to listOf("hA<ulA<i", "hA<ulA<"),
        "*lk" to listOf("*Alika", "*Alik"),
        "k*lk" to listOf("ka*Alika", "ka*Alik"),
        "*lkm" to listOf("*Alikum"),
        ">wl}k" to listOf("<ulA<ika", "<ulA<ik"),
        "Th" to listOf("TAha"),
        "lkn" to listOf("lAkinna", "lAkin"),
        "lknh" to listOf("lAkinnahu"),
        "lknhm" to listOf("lAkinnahum"),
        "lknk" to listOf("lAkinnaka", "lAkinnaki"),
        "lknkm" to listOf("lAkinnakum"),
        "lknkmA" to listOf("lAkinnakumA"),
        "lknnA" to listOf("lAkinnanA"),
        "AlrHmn" to listOf("rraHmAni", "rraHmAn"),
        "Allh" to listOf("llAhi", "llAh", "llAhu", "llAha", "llAh", "llA"),
        "h*yn" to listOf("hA*ayni", "hA*ayn"),
        "nt" to listOf("nit"),
        "fydyw" to listOf("vidyU"),
        "lndn" to listOf("landun")
    )
    private fun isFixedWord(word: String): String? {
        if (word.isEmpty()) return null
        val lastLetter = word.last().toString()
        val wordConsonants = word.replace(Regex("[^h*Ahn\\'>wl}kmyTtfd]"), "")
        val pronunciations = fixedWords[wordConsonants] ?: return null
        if (pronunciations.size > 1) {
            for (pronunciation in pronunciations) {
                if (pronunciation.endsWith(lastLetter)) {
                    return pronunciation
                }
            }
        } else {
            return pronunciations[0]
        }
        return null
    }
    private fun processWord(inputWord: String): String {
        if (punctuation.contains(inputWord)) return inputWord
        val fixedRes = isFixedWord(inputWord)
        if (fixedRes != null) return fixedRes
        var emphaticContext = false
        val word = "bb${inputWord}ee"
        var phones = ""
        for (index in 2 until word.length - 2) {
            val letter_2 = word[index - 2].toString()
            val letter_1 = word[index - 1].toString()
            val letter = word[index].toString()
            val letter1 = word[index + 1].toString()
            val letter2 = word[index + 2].toString()
            if (consonants.contains(letter) && !emphatics.contains(letter)) {
                emphaticContext = false
            }
            if (emphatics.contains(letter)) {
                emphaticContext = true
            }
            if (emphatics.contains(letter1) && !forwardEmphatics.contains(letter1)) {
                emphaticContext = true
            }
            if (unambiguousConsonantMap.containsKey(letter)) {
                phones += unambiguousConsonantMap[letter]
            }
            else if (letter == "l") {
                if (!diacritics.contains(letter1) && !"AYwyaui".contains(letter1) && letter2 == "~") {
                    phones += ""
                } else {
                    phones += "l"
                }
            }
            else if (letter == "~" && !"wy".contains(letter_1) && phones.isNotEmpty()) {
                phones += phones.last()
            }
            else if (letter == "|") {
                phones += if (emphaticContext) "A" else "<"
            }
            else if (letter == "p") {
                phones += if (diacritics.contains(letter1)) "t" else ""
            }
            else if ("AYwyaui".contains(letter)) {
                if ("wy".contains(letter)) {
                    if (("${diacriticsWithoutShadda}AY".contains(letter1)) ||
                        ("wy".contains(letter1) && !"${diacritics}Awy".contains(letter2)) ||
                        (diacriticsWithoutShadda.contains(letter_1) && "${consonants}e".contains(letter1))) {
                        if (letter == "w" && letter_1 == "u" && !"aiAY".contains(letter1)) {
                            phones += "U"
                        } else if (letter == "y" && letter_1 == "i" && !"auAY".contains(letter1)) {
                            phones += "I"
                        } else if (letter == "w" && letter1 == "A" && letter2 == "e") {
                            phones += "w"
                        } else {
                            phones += letter
                        }
                    } else if (letter1 == "~") {
                        if (letter_1 == "a" ||
                            (letter == "w" && "iy".contains(letter_1)) ||
                            (letter == "y" && "wu".contains(letter_1))) {
                            phones += "$letter$letter"
                        } else {
                            phones += if (letter == "w") "U$letter" else "I$letter"
                        }
                    } else if ("${consonants}ui".contains(letter_1) && letter1 == "e") {
                        phones += if (letter == "w") "U" else "I"
                    } else {
                        phones += if (letter == "w") "U" else "I"
                    }
                } else if ("ui".contains(letter)) {
                    phones += letter
                } else {
                    if (letter == "A" && "wk".contains(letter_1) && letter_2 == "b") {
                        phones += "a"
                    } else if (letter == "A" && "ui".contains(letter_1)) {
                        continue
                    } else if (letter == "A" && letter_1 == "w" && letter1 == "e") {
                        phones += "A"
                    } else if ("AY".contains(letter) && letter1 == "e") {
                        phones += "A"
                    } else {
                        if (letter == "a") {
                            phones += "a"
                        } else {
                            phones += "A"
                        }
                    }
                }
            }
        }
        return phones
    }
    private fun postprocessUtterance(phonemes: String): String {
        return phonemes
            .replace("aA", "A")
            .replace("iI", "I")
            .replace("uU", "U")
            .replace("aa", "A")
    }
    fun phonemize(text: String): String {
        val buckw = arabToBuckw(text)
        val words = preprocessUtterance(buckw)
        val phonemes = words.map { processWord(it) }
        return postprocessUtterance(phonemes.joinToString(" "))
    }
}
object ArTokenizer {
    private val tokenToId = mapOf(
        "_pad_" to 0, "_eos_" to 1, "_sil_" to 2, "#" to 3, " " to 4,
        "." to 5, "," to 6, "?" to 7, "!" to 8, "<" to 9,
        "b" to 10, "t" to 11, "^" to 12, "j" to 13, "H" to 14,
        "x" to 15, "d" to 16, "*" to 17, "r" to 18, "z" to 19,
        "s" to 20, "\$" to 21, "S" to 22, "D" to 23, "T" to 24,
        "Z" to 25, "E" to 26, "g" to 27, "f" to 28, "q" to 29,
        "k" to 30, "l" to 31, "m" to 32, "n" to 33, "h" to 34,
        "w" to 35, "y" to 36, "v" to 37, "a" to 38, "u" to 39,
        "i" to 40, "A" to 41, "U" to 42, "I" to 43
    )
    fun tokenizerPhon(arabic: String): LongArray {
        val phonemes = ArPhonemizer.phonemize(arabic).replace(Regex("(.)\\1")) { "${it.groupValues[1]}#" }
        val tokens = phonemes.map { it.toString() }.toMutableList()
        if (tokens.isNotEmpty() && tokens.last() != ".") {
            tokens.add(" ")
            tokens.add(".")
        }
        tokens.add("_eos_")
        return tokens.map { (tokenToId[it] ?: 0).toLong() }.toLongArray()
    }
}
