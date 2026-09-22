package com.pbt.nabratts

import java.math.BigInteger
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

object ArabicTextNormalizer {

    private val CHAR_NAMES: Map<String, String> get() = DictionaryManager.getCharNames()
    private val ABBREVIATIONS: Map<String, String> get() = DictionaryManager.getAbbreviations()
    private val CURRENCIES: Map<String, String> get() = DictionaryManager.getCurrencies()
    private val SYMBOLS: Map<String, String> get() = DictionaryManager.getSymbols()
    private val DIACRITIC_NAMES: Map<String, String> get() = DictionaryManager.getDiacriticNames()

    private val UNITS = arrayOf(
        "", "وَاحِد", "اثْنَان", "ثَلاَثَة", "أَرْبَعَة", "خَمْسَة", "سِتَّة", "سَبْعَة", "ثَمَانِيَة", "تِسْعَة", "عَشَرَة",
        "أَحَدَ عَشَرَ", "اثْنَا عَشَرَ", "ثَلاَثَةَ عَشَرَ", "أَرْبَعَةَ عَشَرَ", "خَمْسَةَ عَشَرَ", "سِتَّةَ عَشَرَ", "سَبْعَةَ عَشَرَ", "ثَمَانِيَةَ عَشَرَ", "تِسْعَةَ عَشَرَ"
    )

    private val TENS = arrayOf(
        "", "", "عِشْرُونَ", "ثَلاَثُونَ", "أَرْبَعُونَ", "خَمْسُونَ", "سِتُّونَ", "سَبْعُونَ", "ثَمَانُونَ", "تِسْعُونَ"
    )

    private val HUNDREDS = arrayOf(
        "", "مِئَة", "مِئَتَان", "ثَلاَثُمِئَة", "أَرْبَعُمِئَة", "خَمْسُمِئَة", "سِتُّمِئَة", "سَبْعُمِئَة", "ثَمَانُمِئَة", "تِسْعُمِئَة"
    )

    private val MONTHS = arrayOf(
        "", "يَنَايِر", "فَبْرَايِر", "مَارِس", "أَبْرِيل", "مَايُو", "يُونِيُو",
        "يُولِيُو", "أَغُسْطُس", "سِبْتَمْبَر", "أُكْتُوبَر", "نُوفَمْبَر", "دِيسَمْبَر"
    )

    private val ORDINAL_UNITS_F = arrayOf(
        "", "الأُولَى", "الثَّانِيَة", "الثَّالِثَة", "الرَّابِعَة", "الخَامِسَة",
        "السَّادِسَة", "السَّابِعَة", "الثَّامِنَة", "التَّاسِعَة", "العَاشِرَة"
    )

    private val ORDINAL_TENS = arrayOf(
        "", "", "العِشْرُونَ", "الثَّلاَثُونَ"
    )

    private const val SHADDA = '\u0651'
    private val DIACRITIC_ONLY_RE =
        Pattern.compile("^[\\u064B-\\u065F\\u0670\\u0640\\u06D6-\\u06ED]+$")

    @JvmStatic
    fun isPreDiacritizedSpelling(text: String?): Boolean {
        if (text == null) return false
        return CHAR_NAMES.containsValue(text) || DIACRITIC_NAMES.containsValue(text) || SYMBOLS.containsValue(text)
    }

    private fun nameLoneDiacritics(marks: String): String? {
        val core = marks.replace("\u0640", "")
        if (core.isEmpty()) {
            if (DIACRITIC_NAMES.containsKey("\u0640")) return DIACRITIC_NAMES["\u0640"]
            if (CHAR_NAMES.containsKey("ـ")) return CHAR_NAMES["ـ"]
            return "كَشِيدَةٌ"
        }
        if (core.length == 1) {
            val c = core[0].toString()
            if (DIACRITIC_NAMES.containsKey(c)) return DIACRITIC_NAMES[c]
            if (CHAR_NAMES.containsKey(c)) return CHAR_NAMES[c]
        }
        if (core.length == 2) {
            val c1 = core[0]
            val c2 = core[1]
            if (c1 == SHADDA || c2 == SHADDA) {
                val vowel = if (c1 == SHADDA) c2 else c1
                when (vowel) {
                    '\u064E' -> return "شَدَّةٌ مَفْتُوحَةٌ"
                    '\u064F' -> return "شَدَّةٌ مَضْمُومَةٌ"
                    '\u0650' -> return "شَدَّةٌ مَكْسُورَةٌ"
                    '\u064B' -> return "شَدَّةٌ مَعَ تَنْوِينِ فَتْحٍ"
                    '\u064C' -> return "شَدَّةٌ مَعَ تَنْوِينِ ضَمٍّ"
                    '\u064D' -> return "شَدَّةٌ مَعَ تَنْوِينِ كَسْرٍ"
                }
            }
        }
        val sb = StringBuilder()
        for (i in core.indices) {
            val c = core[i].toString()
            var nm = DIACRITIC_NAMES[c]
            if (nm == null) nm = CHAR_NAMES[c]
            if (nm == null) return null
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append(nm)
        }
        return sb.toString()
    }

    @JvmStatic
    fun getSingleCharName(text: String?): String? {
        if (text == null) return null

        if (text == " " || text == "\u00A0" || (text.isNotEmpty() && text.trim().isEmpty())) {
            if (text.contains("\n")) return "سَطْرٌ جَدِيدٌ"
            if (text.contains("\t")) return "مَسَافَةٌ بَادِئَةٌ"
            return "مَسَافَةٌ"
        }

        val t = foldDigits(text)?.trim() ?: return null
        if (t.isEmpty()) return null

        if (CHAR_NAMES.containsKey(t)) return CHAR_NAMES[t]
        if (SYMBOLS.containsKey(t)) return SYMBOLS[t]
        if (DIACRITIC_NAMES.containsKey(t)) return DIACRITIC_NAMES[t]

        if (t == "..." || t == "..") return "عَلَامَةُ حَذْفٍ"
        if (t == "--" || t == "——") return "شَرْطَةٌ طَوِيلَةٌ"

        if (DIACRITIC_ONLY_RE.matcher(t).matches()) {
            val lone = nameLoneDiacritics(t)
            if (lone != null) return lone
        }

        val fromEmojiDic = DictionaryManager.getEmojiOrSymbolName(t)
        if (!fromEmojiDic.isNullOrEmpty()) return fromEmojiDic

        val bareTrimmed = t.replace(Regex("[\\u064B-\\u0652\\u0670]"), "")
        if (bareTrimmed.isNotEmpty() && CHAR_NAMES.containsKey(bareTrimmed)) {
            return CHAR_NAMES[bareTrimmed]
        }
        return null
    }

    private val PF_MAP = HashMap<Char, String>().apply {
        put('\uFE70', "ً"); put('\uFE71', "ً")
        put('\uFE72', "ٌ"); put('\uFE73', "ٌ")
        put('\uFE74', "ٍ"); put('\uFE75', "ٍ")
        put('\uFE76', "َ"); put('\uFE77', "َ")
        put('\uFE78', "ُ"); put('\uFE79', "ُ")
        put('\uFE7A', "ِ"); put('\uFE7B', "ِ")
        put('\uFE7C', "ّ"); put('\uFE7D', "ّ")
        put('\uFE7E', "ْ"); put('\uFE7F', "ْ")
        put('\uFE80', "ء")
        put('\uFE81', "آ"); put('\uFE82', "آ")
        put('\uFE83', "أ"); put('\uFE84', "أ")
        put('\uFE85', "ؤ"); put('\uFE86', "ؤ")
        put('\uFE87', "إ"); put('\uFE88', "إ")
        put('\uFE89', "ئ"); put('\uFE8A', "ئ"); put('\uFE8B', "ئ"); put('\uFE8C', "ئ")
        put('\uFE8D', "ا"); put('\uFE8E', "ا")
        put('\uFE8F', "ب"); put('\uFE90', "ب"); put('\uFE91', "ب"); put('\uFE92', "ب")
        put('\uFE93', "ة"); put('\uFE94', "ة")
        put('\uFE95', "ت"); put('\uFE96', "ت"); put('\uFE97', "ت"); put('\uFE98', "ت")
        put('\uFE99', "ث"); put('\uFE9A', "ث"); put('\uFE9B', "ث"); put('\uFE9C', "ث")
        put('\uFE9D', "ج"); put('\uFE9E', "ج"); put('\uFE9F', "ج"); put('\uFEA0', "ج")
        put('\uFEA1', "ح"); put('\uFEA2', "ح"); put('\uFEA3', "ح"); put('\uFEA4', "ح")
        put('\uFEA5', "خ"); put('\uFEA6', "خ"); put('\uFEA7', "خ"); put('\uFEA8', "خ")
        put('\uFEA9', "د"); put('\uFEAA', "د")
        put('\uFEAB', "ذ"); put('\uFEAC', "ذ")
        put('\uFEAD', "ر"); put('\uFEAE', "ر")
        put('\uFEAF', "ز"); put('\uFEB0', "ز")
        put('\uFEB1', "س"); put('\uFEB2', "س"); put('\uFEB3', "س"); put('\uFEB4', "س")
        put('\uFEB5', "ش"); put('\uFEB6', "ش"); put('\uFEB7', "ش"); put('\uFEB8', "ش")
        put('\uFEB9', "ص"); put('\uFEBA', "ص"); put('\uFEBB', "ص"); put('\uFEBC', "ص")
        put('\uFEBD', "ض"); put('\uFEBE', "ض"); put('\uFEBF', "ض"); put('\uFEC0', "ض")
        put('\uFEC1', "ط"); put('\uFEC2', "ط"); put('\uFEC3', "ط"); put('\uFEC4', "ط")
        put('\uFEC5', "ظ"); put('\uFEC6', "ظ"); put('\uFEC7', "ظ"); put('\uFEC8', "ظ")
        put('\uFEC9', "ع"); put('\uFECA', "ع"); put('\uFECB', "ع"); put('\uFECC', "ع")
        put('\uFECD', "غ"); put('\uFECE', "غ"); put('\uFECF', "غ"); put('\uFED0', "غ")
        put('\uFED1', "ف"); put('\uFED2', "ف"); put('\uFED3', "ف"); put('\uFED4', "ف")
        put('\uFED5', "ق"); put('\uFED6', "ق"); put('\uFED7', "ق"); put('\uFED8', "ق")
        put('\uFED9', "ك"); put('\uFEDA', "ك"); put('\uFEDB', "ك"); put('\uFEDC', "ك")
        put('\uFEDD', "ل"); put('\uFEDE', "ل"); put('\uFEDF', "ل"); put('\uFEE0', "ل")
        put('\uFEE1', "م"); put('\uFEE2', "م"); put('\uFEE3', "م"); put('\uFEE4', "م")
        put('\uFEE5', "ن"); put('\uFEE6', "ن"); put('\uFEE7', "ن"); put('\uFEE8', "ن")
        put('\uFEE9', "ه"); put('\uFEEA', "ه"); put('\uFEEB', "ه"); put('\uFEEC', "ه")
        put('\uFEED', "و"); put('\uFEEE', "و")
        put('\uFEEF', "ي"); put('\uFEF0', "ي"); put('\uFEF1', "ي"); put('\uFEF2', "ي")
        put('\uFEF3', "ي"); put('\uFEF4', "ي")
        put('\uFEF5', "لآ"); put('\uFEF6', "لآ")
        put('\uFEF7', "لأ"); put('\uFEF8', "لأ")
        put('\uFEF9', "لإ"); put('\uFEFA', "لإ")
        put('\uFEFB', "لا"); put('\uFEFC', "لا")

        put('\uFB50', "ا"); put('\uFB51', "ا")
        put('\uFB52', "ب"); put('\uFB53', "ب"); put('\uFB54', "ب"); put('\uFB55', "ب")
        put('\uFB56', "ب"); put('\uFB57', "ب"); put('\uFB58', "ب"); put('\uFB59', "ب")
        put('\uFB7A', "ج"); put('\uFB7B', "ج"); put('\uFB7C', "ج"); put('\uFB7D', "ج")
        put('\uFB8A', "ز"); put('\uFB8B', "ز")
        put('\uFB8C', "ر"); put('\uFB8D', "ر")
        put('\uFB8E', "ك"); put('\uFB8F', "ك"); put('\uFB90', "ك"); put('\uFB91', "ك")
        put('\uFB92', "ك"); put('\uFB93', "ك"); put('\uFB94', "ك"); put('\uFB95', "ك")
        put('\uFBD3', "ك"); put('\uFBD4', "ك"); put('\uFBD5', "ك"); put('\uFBD6', "ك")
        put('\uFBFC', "ي"); put('\uFBFD', "ي"); put('\uFBFE', "ي"); put('\uFBFF', "ي")
        put('ک', "ك"); put('ی', "ي"); put('ۀ', "ه")
    }

    @JvmStatic
    fun normalizePresentationForms(text: String?): String {
        if (text == null) return ""
        val sb = StringBuilder()
        for (i in text.indices) {
            val c = text[i]
            if ((c in '\u200B'..'\u200F') || (c in '\u202A'..'\u202E') ||
                (c in '\u2060'..'\u2069') || c == '\uFEFF' || c == '\u00AD') {
                continue
            }
            val mapped = PF_MAP[c]
            if (mapped != null) {
                sb.append(mapped)
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun swapTanweenFathaAlif(text: String?): String {
        if (text.isNullOrEmpty()) return text ?: ""
        var result = text.replace(Regex("(?<![\u0648\u0648\u0651])\u064B\u0627"), "\u0627\u064B")
        result = result.replace("\u064B\u0649", "\u0649\u064B")
        return result
    }

    @JvmStatic
    fun normalize(text: String?): String = normalize(text, false)

    @JvmStatic
    fun normalize(text: String?, isSingleCharRequest: Boolean): String {
        if (text.isNullOrBlank()) return text ?: ""
        var t = text.replace("ـ", "")
        t = LatinTransliterator.transliterate(t)
        return try {
            if (isSingleCharRequest) {
                val singleName = getSingleCharName(t)
                if (singleName != null) return singleName
            }
            ArNorm.normalize(ArNorm.foldDigits(t) ?: t)
        } catch (_: Exception) {
            t
        }
    }

    @JvmStatic
    fun foldDigits(text: String?): String? {
        if (text == null) return null
        val sb = StringBuilder()
        for (i in text.indices) {
            val c = text[i]
            when (c) {
                in '٠'..'٩' -> sb.append(('0'.code + (c.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (c.code - '۰'.code)).toChar())
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun numberToWords(number: Long): String = Num2WordsAr.cardinal(number)

    private const val HAMZA_CHARS = "أإآءؤئٱ"

    @JvmStatic
    fun hasHamza(word: String?): Boolean {
        if (word == null) return false
        for (i in word.indices) {
            if (HAMZA_CHARS.indexOf(word[i]) != -1) return true
        }
        return false
    }

    private fun isHamzaChar(c: Char): Boolean = HAMZA_CHARS.indexOf(c) != -1

    private fun flattenHamza(c: Char): Char = when (c) {
        '\u0623', '\u0625', '\u0622', '\u0671' -> '\u0627'
        '\u0624' -> '\u0648'
        '\u0626', '\u0649' -> '\u064A'
        else -> c
    }

    private fun flattenWord(word: String): String {
        val sb = StringBuilder()
        for (i in word.indices) {
            val c = word[i]
            if (!isDiacritic(c) && (c in '\u0621'..'\u064A' || c == '\u0671' || c.isLetterOrDigit())) {
                sb.append(flattenHamza(c))
            }
        }
        return sb.toString()
    }

    private fun wordsMatch(oWord: String, dWord: String): Boolean =
        flattenWord(oWord) == flattenWord(dWord)

    class CharWithDiac(var baseChar: Char, var diacritics: String)

    private fun parseCharsWithDiac(text: String?): MutableList<CharWithDiac> {
        val list = mutableListOf<CharWithDiac>()
        if (text == null) return list
        var i = 0
        val len = text.length
        while (i < len) {
            val base = text[i]
            i++
            val diac = StringBuilder()
            while (i < len && isDiacritic(text[i])) {
                diac.append(text[i])
                i++
            }
            list.add(CharWithDiac(base, diac.toString()))
        }
        return list
    }

    private fun isDiacritic(c: Char): Boolean =
        (c in '\u064B'..'\u0655') || c == '\u0670' || c == '\u0640'

    private const val KASRA = '\u0650'
    private const val FATHA = '\u064E'
    private const val DAMMA = '\u064F'
    private const val SUKOON = '\u0652'
    private const val TANWEEN_FATH = '\u064B'
    private const val TANWEEN_DAMM = '\u064C'
    private const val TANWEEN_KASR = '\u064D'
    private const val HAMZA_ABOVE_MARK = '\u0654'
    private const val HAMZA_BELOW_MARK = '\u0655'
    private const val MADDA_MARK = '\u0653'

    @JvmStatic
    fun sanitizeCharVowels(base: Char, diac: String?): String {
        var res = diac ?: ""
        if (res.isNotEmpty()) {
            res = res.replace(HAMZA_ABOVE_MARK.toString(), "")
                .replace(HAMZA_BELOW_MARK.toString(), "")
                .replace(MADDA_MARK.toString(), "")
        }

        when (base) {
            '\u0625' -> { // إ (Alif with Hamza Below): Strictly ONLY Kasra or Tanween Kasr is permissible
                val hasShadda = res.indexOf(SHADDA) != -1
                val hasTanween = res.indexOf(TANWEEN_KASR) != -1 ||
                        res.indexOf(TANWEEN_FATH) != -1 ||
                        res.indexOf(TANWEEN_DAMM) != -1
                res = when {
                    hasTanween -> if (hasShadda) "$SHADDA$TANWEEN_KASR" else "$TANWEEN_KASR"
                    hasShadda -> "$SHADDA$KASRA"
                    else -> "$KASRA"
                }
            }
            '\u0623' -> { // أ (Alif with Hamza Above): Kasra and Tanween Kasr are IMPOSSIBLE
                if (res.indexOf(KASRA) != -1) res = res.replace(KASRA, FATHA)
                if (res.indexOf(TANWEEN_KASR) != -1) res = res.replace(TANWEEN_KASR, TANWEEN_FATH)
            }
            '\u0624' -> { // ؤ (Waw with Hamza Above): Kasra and Tanween Kasr are IMPOSSIBLE in Arabic
                if (res.indexOf(KASRA) != -1) res = res.replace(KASRA, DAMMA)
                if (res.indexOf(TANWEEN_KASR) != -1) res = res.replace(TANWEEN_KASR, TANWEEN_DAMM)
            }
            '\u0622' -> { // آ (Alif with Madda): Cannot take short vowels or tanween
                val sb = StringBuilder()
                for (i in res.indices) {
                    val c = res[i]
                    if (c != FATHA && c != DAMMA && c != KASRA && c != SUKOON &&
                        c != TANWEEN_FATH && c != TANWEEN_DAMM && c != TANWEEN_KASR
                    ) {
                        sb.append(c)
                    }
                }
                res = sb.toString()
            }
            '\u0649' -> { // ى (Alif Maqsura): No short vowels except tanween fath / dagger alif
                val sb = StringBuilder()
                for (i in res.indices) {
                    val c = res[i]
                    if (c != FATHA && c != DAMMA && c != KASRA && c != SUKOON) {
                        sb.append(c)
                    }
                }
                res = sb.toString()
            }
        }
        return res
    }

    @JvmStatic
    fun sanitizeWordVowels(word: String): String {
        if (word.isEmpty()) return word
        val pairs = parseCharsWithDiac(Normalizer.normalize(word, Normalizer.Form.NFC))
        val sb = StringBuilder()
        for (cd in pairs) {
            val sdiac = sanitizeCharVowels(cd.baseChar, cd.diacritics)
            sb.append(cd.baseChar).append(sdiac)
        }
        return sb.toString()
    }

    @JvmStatic
    fun postSanitizeHamzaDiacritics(text: String): String {
        if (text.isEmpty()) return text
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        val sb = StringBuilder(normalized.length + 8)
        val len = normalized.length
        var i = 0
        while (i < len) {
            val c = normalized[i]
            when (c) {
                '\u0625' -> { // إ
                    sb.append(c)
                    i++
                    val diac = StringBuilder()
                    while (i < len && isDiacritic(normalized[i])) {
                        diac.append(normalized[i])
                        i++
                    }
                    sb.append(sanitizeCharVowels('\u0625', diac.toString()))
                }
                '\u0623' -> { // أ
                    sb.append(c)
                    i++
                    val diac = StringBuilder()
                    while (i < len && isDiacritic(normalized[i])) {
                        diac.append(normalized[i])
                        i++
                    }
                    sb.append(sanitizeCharVowels('\u0623', diac.toString()))
                }
                '\u0622' -> { // آ
                    sb.append(c)
                    i++
                    val diac = StringBuilder()
                    while (i < len && isDiacritic(normalized[i])) {
                        diac.append(normalized[i])
                        i++
                    }
                    sb.append(sanitizeCharVowels('\u0622', diac.toString()))
                }
                '\u0624' -> { // ؤ
                    sb.append(c)
                    i++
                    val diac = StringBuilder()
                    while (i < len && isDiacritic(normalized[i])) {
                        diac.append(normalized[i])
                        i++
                    }
                    sb.append(sanitizeCharVowels('\u0624', diac.toString()))
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC)
    }

    private fun preserveExactHamzas(oWord: String, dWord: String): String {
        val normO = Normalizer.normalize(oWord, Normalizer.Form.NFC)
        val normD = Normalizer.normalize(dWord, Normalizer.Form.NFC)
        val o = parseCharsWithDiac(normO)
        val d = parseCharsWithDiac(normD)
        if (o.isEmpty()) return sanitizeWordVowels(normD)
        if (d.isEmpty()) return normD

        if (o.size == d.size) {
            val sb = StringBuilder()
            for (i in o.indices) {
                val oBase = o[i].baseChar
                val dBase = d[i].baseChar
                val base = if (isHamzaChar(oBase)) oBase else if (isHamzaChar(dBase) && !isAlifChar(oBase)) dBase else oBase
                val odiac = o[i].diacritics
                val ddiac = d[i].diacritics
                var chosenDiac = if (odiac.isNotEmpty()) odiac else ddiac
                chosenDiac = sanitizeCharVowels(base, chosenDiac)
                sb.append(base).append(chosenDiac)
            }
            return sb.toString()
        }

        val sb = StringBuilder()
        var oi = 0
        var di = 0
        while (oi < o.size && di < d.size) {
            val oBase = o[oi].baseChar
            val dBase = d[di].baseChar
            val oFlat = flattenHamza(oBase)
            val dFlat = flattenHamza(dBase)
            if (oFlat == dFlat) {
                val base = if (isHamzaChar(oBase)) oBase else dBase
                val odiac = o[oi].diacritics
                val ddiac = d[di].diacritics
                var chosenDiac = if (odiac.isNotEmpty()) odiac else ddiac
                chosenDiac = sanitizeCharVowels(base, chosenDiac)
                sb.append(base).append(chosenDiac)
                oi++
                di++
            } else {
                val base = dBase
                val ddiac = sanitizeCharVowels(base, d[di].diacritics)
                sb.append(base).append(ddiac)
                di++
            }
        }
        while (di < d.size) {
            val base = d[di].baseChar
            val ddiac = sanitizeCharVowels(base, d[di].diacritics)
            sb.append(base).append(ddiac)
            di++
        }
        return sb.toString()
    }

    @JvmStatic
    fun restoreHamza(original: String?, diacritized: String?): String {
        if (original.isNullOrEmpty() || diacritized.isNullOrEmpty()) return diacritized ?: ""
        val normOrig = Normalizer.normalize(original, Normalizer.Form.NFC)
        val normDiac = Normalizer.normalize(diacritized, Normalizer.Form.NFC)
        val oWords = normOrig.trim().split(Regex("\\s+"))
        val dWords = normDiac.trim().split(Regex("\\s+"))
        if (oWords.isEmpty() || dWords.isEmpty()) return postSanitizeHamzaDiacritics(normDiac)

        val sb = StringBuilder()
        if (oWords.size == dWords.size) {
            for (i in oWords.indices) {
                if (i > 0) sb.append(" ")
                sb.append(preserveExactHamzas(oWords[i], dWords[i]))
            }
        } else {
            var oi = 0
            for (di in dWords.indices) {
                if (di > 0) sb.append(" ")
                if (oi < oWords.size && wordsMatch(oWords[oi], dWords[di])) {
                    sb.append(preserveExactHamzas(oWords[oi], dWords[di]))
                    oi++
                } else {
                    var found = false
                    var lookahead = oi + 1
                    while (lookahead < oWords.size && lookahead <= oi + 5) {
                        if (wordsMatch(oWords[lookahead], dWords[di])) {
                            oi = lookahead
                            sb.append(preserveExactHamzas(oWords[oi], dWords[di]))
                            oi++
                            found = true
                            break
                        }
                        lookahead++
                    }
                    if (!found) {
                        sb.append(sanitizeWordVowels(dWords[di]))
                    }
                }
            }
        }
        return postSanitizeHamzaDiacritics(sb.toString())
    }

    private const val TANWEEN_SET = "\u064B\u064C\u064D"
    private fun isTanween(c: Char): Boolean = TANWEEN_SET.indexOf(c) != -1
    private fun isAlifChar(c: Char): Boolean = c == '\u0627' || c == '\u0649'
    private fun containsTanween(diac: String): Boolean = diac.any { isTanween(it) }
    private fun removeTanween(diac: String): String = diac.filter { !isTanween(it) }
    private fun keepOnlyTanween(diac: String): String = diac.filter { isTanween(it) }
    private fun removeShortVowels(diac: String): String =
        diac.filter { it != '\u064E' && it != '\u064F' && it != '\u0650' }

    @JvmStatic
    fun fixBareHamzaVowel(text: String?): String {
        if (text.isNullOrEmpty()) return text ?: ""
        val hamzaAbove = '\u0623'
        val hamzaBelow = '\u0625'
        val fatha = '\u064E'
        val kasra = '\u0650'
        val shadda = '\u0651'

        val words = text.split(Regex("\\s+"))
        val outAll = StringBuilder()
        for (w in words.indices) {
            if (w > 0) outAll.append(" ")
            val pairs = parseCharsWithDiac(Normalizer.normalize(words[w], Normalizer.Form.NFC))
            for (i in pairs.indices) {
                val cd = pairs[i]
                if (cd.diacritics.isEmpty() || cd.diacritics == shadda.toString()) {
                    if (cd.baseChar == hamzaBelow) {
                        cd.diacritics = if (cd.diacritics == shadda.toString()) "$shadda$kasra" else kasra.toString()
                    } else if (cd.baseChar == hamzaAbove) {
                        val isWordInitial = (i == 0)
                        val isAfterLam = (i >= 2 && pairs[i - 1].baseChar == 'ل' && pairs[i - 2].baseChar == 'ا')
                        val isAfterPrefix = (i == 1 && "وفبك".indexOf(pairs[0].baseChar) != -1)
                        if (isWordInitial || isAfterLam || isAfterPrefix) {
                            cd.diacritics = if (cd.diacritics == shadda.toString()) "$shadda$fatha" else fatha.toString()
                        }
                    }
                }
                cd.diacritics = sanitizeCharVowels(cd.baseChar, cd.diacritics)
            }
            val wb = StringBuilder()
            for (cd in pairs) wb.append(cd.baseChar).append(cd.diacritics)
            outAll.append(wb)
        }
        return postSanitizeHamzaDiacritics(outAll.toString())
    }

    @JvmStatic
    fun dedupeTanween(text: String?): String {
        if (text.isNullOrEmpty()) return text ?: ""
        val words = text.split(" ")
        val outAll = StringBuilder()
        for (w in words.indices) {
            if (w > 0) outAll.append(" ")
            val pairs = parseCharsWithDiac(words[w])
            val n = pairs.size
            for (i in 0 until n - 1) {
                val cur = pairs[i]
                val next = pairs[i + 1]
                val isLastAlif = (i + 1 == n - 1) && isAlifChar(next.baseChar)
                if (!isLastAlif) continue

                val consT = containsTanween(cur.diacritics)
                val alifT = containsTanween(next.diacritics)
                if (consT && alifT) {
                    cur.diacritics = removeTanween(cur.diacritics)
                } else if (consT && !alifT) {
                    val moved = keepOnlyTanween(cur.diacritics)
                    cur.diacritics = removeTanween(cur.diacritics)
                    next.diacritics = removeShortVowels(next.diacritics) + moved
                }
            }

            for (i in 0 until n - 1) {
                val cur = pairs[i]
                val next = pairs[i + 1]
                if (next.baseChar == '\u0627' && i + 1 < n - 1) {
                    if (cur.diacritics.isEmpty() || cur.diacritics == "\u0652") {
                        cur.diacritics = "\u064E"
                    } else if (cur.diacritics.contains("\u0651") && !cur.diacritics.contains("\u064E") && !cur.diacritics.contains("\u064F") && !cur.diacritics.contains("\u0650")) {
                        cur.diacritics = cur.diacritics + "\u064E"
                    }
                }
            }

            val wb = StringBuilder()
            for (cd in pairs) wb.append(cd.baseChar).append(cd.diacritics)
            outAll.append(wb)
        }
        return Normalizer.normalize(outAll.toString(), Normalizer.Form.NFC)
    }

    private const val NAA_MARKS = "\u064B\u064C\u064D\u064E\u064F\u0650\u0651\u0652\u0670"
    private fun isNaaMark(c: Char): Boolean = NAA_MARKS.indexOf(c) != -1
    private fun naaBare(s: String): String = s.filter { !isNaaMark(it) }

    private fun fixNaaWord(o: String, d: String): String {
        val nun = '\u0646'
        val alif = '\u0627'
        val fatha = '\u064E'
        val shadda = '\u0651'
        val tanweens = "\u064B\u064C\u064D"

        val ob = naaBare(o)
        if (!ob.endsWith("\u0646\u0627")) return d

        val chars = d.toCharArray()
        val n = chars.size
        var ai = -1
        for (k in n - 1 downTo 0) {
            if (chars[k] == alif) { ai = k; break }
        }
        if (ai < 0) return d

        val tail = StringBuilder()
        for (k in ai + 1 until n) if (!isNaaMark(chars[k])) tail.append(chars[k])

        var j = ai - 1
        while (j >= 0 && isNaaMark(chars[j])) j--
        if (j < 0) return d

        var hadShadda = false
        for (k in j + 1 until ai) if (chars[k] == shadda) { hadShadda = true; break }
        val nunMarks = (if (hadShadda) shadda.toString() else "") + fatha

        val out = StringBuilder()
        if (chars[j] != nun) {
            for (k in 0..j) if (tanweens.indexOf(chars[k]) == -1) out.append(chars[k])
            out.append(nun).append(nunMarks).append(alif).append(tail)
            return out.toString()
        }
        for (k in 0 until j) if (tanweens.indexOf(chars[k]) == -1) out.append(chars[k])
        out.append(nun).append(nunMarks).append(alif).append(tail)
        return out.toString()
    }

    @JvmStatic
    fun fixNaaPronoun(original: String?, diacritized: String?): String {
        if (original == null || diacritized == null) return diacritized ?: ""
        val oWords = original.split(" ")
        val dWords = diacritized.split(" ")
        if (oWords.size != dWords.size) {
            if (oWords.size == 1 && dWords.size == 1) return fixNaaWord(original, diacritized)
            return diacritized
        }
        val sb = StringBuilder()
        for (i in oWords.indices) {
            if (i > 0) sb.append(" ")
            sb.append(fixNaaWord(oWords[i], dWords[i]))
        }
        return sb.toString()
    }

    @JvmStatic
    fun sukoonizeEndOfSentence(text: String?): String {
        if (text.isNullOrBlank()) return text ?: ""

        val tanweenFathaPattern = Pattern.compile("\\u064B(\\s*[\\.\\,\\،\\؛\\;\\?\\؟\\!\\:\\-\\_]*\\s*)$")
        if (tanweenFathaPattern.matcher(text).find()) return text

        val taMarbutaPattern = "ة[\\u064B-\\u0652\\u0670]*(\\s*[\\.\\,\\،\\؛\\;\\?\\؟\\!\\:\\-\\_]*\\s*)$"
        if (Pattern.compile(taMarbutaPattern).matcher(text).find()) {
            return text.replaceFirst(Regex(taMarbutaPattern), "$1")
        }

        val diacEndPattern = "([\\u0621-\\u064A\\u0671])(\\u0651?)[\\u064C\\u064D\\u064E\\u064F\\u0650\\u0652\\u0670]+(\\s*[\\.\\,\\،\\؛\\;\\?\\؟\\!\\:\\-\\_]*\\s*)$"
        return text.replaceFirst(Regex(diacEndPattern), "$1$2$3")
    }

    // LATIN TRANSLITERATOR
    object LatinTransliterator {
        private val KNOWN_NAMES = HashMap<String, String>()
        private val SINGLE_LETTER_MAP = HashMap<Char, String>()
        private val LATIN_CHAR_MAP = HashMap<Char, String>()

        private val LATIN_COMBOS = arrayOf(
            arrayOf("tion", "شِن"), arrayOf("sion", "شِن"), arrayOf("ture", "تْشَر"),
            arrayOf("cious", "شَس"), arrayOf("tious", "شَس"), arrayOf("cial", "شَل"),
            arrayOf("tial", "شَل"), arrayOf("ment", "مِنْت"), arrayOf("able", "أَبِل"),
            arrayOf("ible", "إِبِل"), arrayOf("less", "لِس"), arrayOf("ness", "نِس"),
            arrayOf("ship", "شِب"), arrayOf("board", "بُورْد"), arrayOf("card", "كَارْد"),
            arrayOf("hard", "هَارْد"), arrayOf("port", "بُورْت"), arrayOf("ford", "فُورْد"),
            arrayOf("land", "لَانْد"), arrayOf("wood", "وُود"), arrayOf("good", "جُود"),
            arrayOf("book", "بُوك"), arrayOf("look", "لُوك"), arrayOf("cook", "كُوك"),
            arrayOf("walk", "وُوك"), arrayOf("talk", "تُوك"), arrayOf("work", "وِيرْك"),
            arrayOf("word", "وِرْد"), arrayOf("aight", "َايت"), arrayOf("eight", "إِيت"),
            arrayOf("ight", "َايت"), arrayOf("ought", "أُوت"), arrayOf("aught", "أُوت"),
            arrayOf("sch", "سْك"), arrayOf("tch", "تْش"), arrayOf("ch", "تْش"),
            arrayOf("sh", "ش"), arrayOf("th", "ث"), arrayOf("ph", "ف"),
            arrayOf("kh", "خ"), arrayOf("gh", "غ"), arrayOf("dh", "ذ"),
            arrayOf("zh", "ج"), arrayOf("wh", "و"), arrayOf("wr", "ر"),
            arrayOf("kn", "ن"), arrayOf("ps", "س"), arrayOf("qu", "كُو"),
            arrayOf("ck", "ك"), arrayOf("ng", "نْج"), arrayOf("eau", "أُو"),
            arrayOf("eigh", "إِي"), arrayOf("air", "إِير"), arrayOf("ear", "إِير"),
            arrayOf("eer", "إِير"), arrayOf("oor", "أُور"), arrayOf("our", "أُور"),
            arrayOf("oar", "أُور"), arrayOf("ee", "ِي"), arrayOf("oo", "ُو"),
            arrayOf("ea", "ِي"), arrayOf("oa", "ُو"), arrayOf("ai", "َاي"),
            arrayOf("ay", "َاي"), arrayOf("ey", "ِي"), arrayOf("ei", "ِي"),
            arrayOf("oi", "ُوي"), arrayOf("oy", "ُوي"), arrayOf("ou", "َاو"),
            arrayOf("ow", "َاو"), arrayOf("au", "أُو"), arrayOf("aw", "أُو"),
            arrayOf("ie", "ِي"), arrayOf("ue", "ُو"), arrayOf("ui", "ُو"),
            arrayOf("bb", "ب"), arrayOf("cc", "ك"), arrayOf("dd", "د"),
            arrayOf("ff", "ف"), arrayOf("gg", "ج"), arrayOf("ll", "ل"),
            arrayOf("mm", "م"), arrayOf("nn", "ن"), arrayOf("pp", "ب"),
            arrayOf("rr", "ر"), arrayOf("ss", "س"), arrayOf("tt", "ت"),
            arrayOf("vv", "ف"), arrayOf("zz", "ز")
        )

        init {
            SINGLE_LETTER_MAP['a'] = "إِيهْ"; SINGLE_LETTER_MAP['A'] = "إِيهْ"
            SINGLE_LETTER_MAP['b'] = "بِي"; SINGLE_LETTER_MAP['B'] = "بِي"
            SINGLE_LETTER_MAP['c'] = "سِي"; SINGLE_LETTER_MAP['C'] = "سِي"
            SINGLE_LETTER_MAP['d'] = "دِي"; SINGLE_LETTER_MAP['D'] = "دِي"
            SINGLE_LETTER_MAP['e'] = "إِي"; SINGLE_LETTER_MAP['E'] = "إِي"
            SINGLE_LETTER_MAP['f'] = "إِفْ"; SINGLE_LETTER_MAP['F'] = "إِفْ"
            SINGLE_LETTER_MAP['g'] = "جِي"; SINGLE_LETTER_MAP['G'] = "جِي"
            SINGLE_LETTER_MAP['h'] = "إِيتْشْ"; SINGLE_LETTER_MAP['H'] = "إِيتْشْ"
            SINGLE_LETTER_MAP['i'] = "آيْ"; SINGLE_LETTER_MAP['I'] = "آيْ"
            SINGLE_LETTER_MAP['j'] = "جَي"; SINGLE_LETTER_MAP['J'] = "جَي"
            SINGLE_LETTER_MAP['k'] = "كَي"; SINGLE_LETTER_MAP['K'] = "كَي"
            SINGLE_LETTER_MAP['l'] = "إِلْ"; SINGLE_LETTER_MAP['L'] = "إِلْ"
            SINGLE_LETTER_MAP['m'] = "إِمْ"; SINGLE_LETTER_MAP['M'] = "إِمْ"
            SINGLE_LETTER_MAP['n'] = "إِنْ"; SINGLE_LETTER_MAP['N'] = "إِنْ"
            SINGLE_LETTER_MAP['o'] = "أُو"; SINGLE_LETTER_MAP['O'] = "أُو"
            SINGLE_LETTER_MAP['p'] = "بِي"; SINGLE_LETTER_MAP['P'] = "بِي"
            SINGLE_LETTER_MAP['q'] = "كِيُو"; SINGLE_LETTER_MAP['Q'] = "كِيُو"
            SINGLE_LETTER_MAP['r'] = "آرْ"; SINGLE_LETTER_MAP['R'] = "آرْ"
            SINGLE_LETTER_MAP['s'] = "إِسْ"; SINGLE_LETTER_MAP['S'] = "إِسْ"
            SINGLE_LETTER_MAP['t'] = "تِي"; SINGLE_LETTER_MAP['T'] = "تِي"
            SINGLE_LETTER_MAP['u'] = "يُو"; SINGLE_LETTER_MAP['U'] = "يُو"
            SINGLE_LETTER_MAP['v'] = "فِي"; SINGLE_LETTER_MAP['V'] = "فِي"
            SINGLE_LETTER_MAP['w'] = "دَبْلِيُو"; SINGLE_LETTER_MAP['W'] = "دَبْلِيُو"
            SINGLE_LETTER_MAP['x'] = "إِكْسْ"; SINGLE_LETTER_MAP['X'] = "إِكْسْ"
            SINGLE_LETTER_MAP['y'] = "وَايْ"; SINGLE_LETTER_MAP['Y'] = "وَايْ"
            SINGLE_LETTER_MAP['z'] = "زِدْ"; SINGLE_LETTER_MAP['Z'] = "زِدْ"

            LATIN_CHAR_MAP['a'] = "َا"
            LATIN_CHAR_MAP['b'] = "ب"
            LATIN_CHAR_MAP['c'] = "ك"
            LATIN_CHAR_MAP['d'] = "د"
            LATIN_CHAR_MAP['e'] = "ِي"
            LATIN_CHAR_MAP['f'] = "ف"
            LATIN_CHAR_MAP['g'] = "ج"
            LATIN_CHAR_MAP['h'] = "ه"
            LATIN_CHAR_MAP['i'] = "ِي"
            LATIN_CHAR_MAP['j'] = "ج"
            LATIN_CHAR_MAP['k'] = "ك"
            LATIN_CHAR_MAP['l'] = "ل"
            LATIN_CHAR_MAP['m'] = "م"
            LATIN_CHAR_MAP['n'] = "ن"
            LATIN_CHAR_MAP['o'] = "ُو"
            LATIN_CHAR_MAP['p'] = "ب"
            LATIN_CHAR_MAP['q'] = "ق"
            LATIN_CHAR_MAP['r'] = "ر"
            LATIN_CHAR_MAP['s'] = "س"
            LATIN_CHAR_MAP['t'] = "ت"
            LATIN_CHAR_MAP['u'] = "ُو"
            LATIN_CHAR_MAP['v'] = "ف"
            LATIN_CHAR_MAP['w'] = "و"
            LATIN_CHAR_MAP['x'] = "كْس"
            LATIN_CHAR_MAP['y'] = "ي"
            LATIN_CHAR_MAP['z'] = "ز"

            KNOWN_NAMES["google"] = "جُوجِل"
            KNOWN_NAMES["android"] = "أَنْدْرُويْد"
            KNOWN_NAMES["apple"] = "أَبْل"
            KNOWN_NAMES["microsoft"] = "مَايْكْرُوسُوفْت"
            KNOWN_NAMES["windows"] = "وِينْدُوز"
            KNOWN_NAMES["linux"] = "لِينُكْس"
            KNOWN_NAMES["ubuntu"] = "أُوبُونْتُو"
            KNOWN_NAMES["meta"] = "مِيتَا"
            KNOWN_NAMES["facebook"] = "فَيْسْبُوك"
            KNOWN_NAMES["whatsapp"] = "وَاتْسَاب"
            KNOWN_NAMES["telegram"] = "تِلِيجْرَام"
            KNOWN_NAMES["instagram"] = "إِنْسْتِغْرَام"
            KNOWN_NAMES["twitter"] = "تُوِيتَر"
            KNOWN_NAMES["x"] = "إِكْسْ"
            KNOWN_NAMES["tiktok"] = "تِيكْ تُوك"
            KNOWN_NAMES["snapchat"] = "سْنَابْ شَات"
            KNOWN_NAMES["youtube"] = "يُوتْيُوب"
            KNOWN_NAMES["netflix"] = "نِتْفْلِيكْس"
            KNOWN_NAMES["spotify"] = "سْبُوتِيفَاي"
            KNOWN_NAMES["amazon"] = "أَمَازُون"
            KNOWN_NAMES["uber"] = "أُوبَر"
            KNOWN_NAMES["zoom"] = "زُوم"
            KNOWN_NAMES["skype"] = "سْكَايْب"
            KNOWN_NAMES["discord"] = "دِيسْكُورْد"
            KNOWN_NAMES["github"] = "جِيتْ هَاب"
            KNOWN_NAMES["gmail"] = "جِيمِيل"
            KNOWN_NAMES["chrome"] = "كْرُوم"
            KNOWN_NAMES["firefox"] = "فَايِرْفُوكْس"
            KNOWN_NAMES["edge"] = "إِيدْج"
            KNOWN_NAMES["safari"] = "سَفَارِي"
            KNOWN_NAMES["wikipedia"] = "وِيكِيبِيدْيَا"
            KNOWN_NAMES["tesla"] = "تِسْلاَ"
            KNOWN_NAMES["samsung"] = "سَامْسُونْج"
            KNOWN_NAMES["huawei"] = "هَوَاوِي"
            KNOWN_NAMES["xiaomi"] = "شَاوْمِي"
            KNOWN_NAMES["sony"] = "سُونِي"
            KNOWN_NAMES["intel"] = "إِينْتِل"
            KNOWN_NAMES["nvidia"] = "إِنْفِيدْيَا"
            KNOWN_NAMES["amd"] = "إِيهْ إِمْ دِي"
            KNOWN_NAMES["python"] = "بَايْثُون"
            KNOWN_NAMES["java"] = "جَافَا"
            KNOWN_NAMES["javascript"] = "جَافَاسْكْرِبْت"
            KNOWN_NAMES["typescript"] = "تَايِبْسْكْرِبْت"
            KNOWN_NAMES["php"] = "بِي إِتْشْ بِي"
            KNOWN_NAMES["html"] = "إِتْشْ تِي إِمْ إِل"
            KNOWN_NAMES["css"] = "سِي إِسْ إِس"
            KNOWN_NAMES["sql"] = "إِسْ كِيُو إِل"
            KNOWN_NAMES["api"] = "إِيهْ بِي آي"
            KNOWN_NAMES["sdk"] = "إِسْ دِي كَي"
            KNOWN_NAMES["apk"] = "إِيهْ بِي كَي"
            KNOWN_NAMES["pdf"] = "بِي دِي إِف"
            KNOWN_NAMES["ram"] = "رَام"
            KNOWN_NAMES["rom"] = "رُوم"
            KNOWN_NAMES["cpu"] = "سِي بِي يُو"
            KNOWN_NAMES["gpu"] = "جِي بِي يُو"
            KNOWN_NAMES["gps"] = "جِي بِي إِس"
            KNOWN_NAMES["sim"] = "سِيم"
            KNOWN_NAMES["vpn"] = "فِي بِي إِن"
            KNOWN_NAMES["wifi"] = "وَايْفَاي"
            KNOWN_NAMES["usb"] = "يُو إِسْ بِي"
            KNOWN_NAMES["sms"] = "إِسْ إِمْ إِس"
            KNOWN_NAMES["url"] = "يُو آرْ إِل"
            KNOWN_NAMES["ip"] = "آيْ بِي"
            KNOWN_NAMES["mac"] = "مَاك"
            KNOWN_NAMES["ios"] = "آيْ أُو إِس"
            KNOWN_NAMES["pc"] = "بِي سِي"
            KNOWN_NAMES["tv"] = "تِي فِي"
            KNOWN_NAMES["ai"] = "إِيهْ آي"
            KNOWN_NAMES["ok"] = "أُوكِي"
            KNOWN_NAMES["app"] = "آب"
            KNOWN_NAMES["apps"] = "آبْس"
            KNOWN_NAMES["bot"] = "بُوت"
            KNOWN_NAMES["link"] = "لِينْك"
            KNOWN_NAMES["online"] = "أُونْلاِين"
            KNOWN_NAMES["offline"] = "أُوفْلاِين"
            KNOWN_NAMES["email"] = "إِيمِيل"
            KNOWN_NAMES["e-mail"] = "إِيمِيل"
            KNOWN_NAMES["site"] = "سَايْت"
            KNOWN_NAMES["web"] = "وِيب"
            KNOWN_NAMES["chat"] = "تْشَات"
            KNOWN_NAMES["chatgpt"] = "تْشَاتْ جِي بِي تِي"
            KNOWN_NAMES["gemini"] = "جِيمِينَاي"
            KNOWN_NAMES["deepmind"] = "دِيبْ مَايْنْد"
            KNOWN_NAMES["openai"] = "أُوبْنْ إِيهْ آي"
            KNOWN_NAMES["antigravity"] = "أَنْتِي جْرَافِيتِي"
            KNOWN_NAMES["rhvoice"] = "آرْ إِتْشْ فُويْس"
            KNOWN_NAMES["talkback"] = "تُوكْ بَاك"
            KNOWN_NAMES["nvda"] = "إِنْ فِي دِي إِيه"
            KNOWN_NAMES["cancel"] = "كَانْسِل"
            KNOWN_NAMES["download"] = "دَاوْنْلُود"
            KNOWN_NAMES["upload"] = "أَبْلُود"
            KNOWN_NAMES["update"] = "أَبْدَيْت"
            KNOWN_NAMES["install"] = "إِنْسْتُول"
            KNOWN_NAMES["uninstall"] = "أَنْإِنْسْتُول"
            KNOWN_NAMES["setup"] = "سِيتْأَب"
            KNOWN_NAMES["settings"] = "سِيتِينْغْز"
            KNOWN_NAMES["options"] = "أُوبْشِنْز"
            KNOWN_NAMES["menu"] = "مِنْيُو"
            KNOWN_NAMES["login"] = "لُوجِين"
            KNOWN_NAMES["logout"] = "لُوجْآوت"
            KNOWN_NAMES["sign"] = "سَايْن"
            KNOWN_NAMES["signin"] = "سَايْنْ إِن"
            KNOWN_NAMES["signup"] = "سَايْنْ أَب"
            KNOWN_NAMES["password"] = "بَاسْوِرْد"
            KNOWN_NAMES["username"] = "يُوزَرْنِيم"
            KNOWN_NAMES["user"] = "يُوزَر"
            KNOWN_NAMES["admin"] = "أَدْمِين"
            KNOWN_NAMES["status"] = "سْتَاتُوس"
            KNOWN_NAMES["error"] = "إِيرُور"
            KNOWN_NAMES["warning"] = "وَارْنِينْج"
            KNOWN_NAMES["info"] = "إِينْفُو"
            KNOWN_NAMES["help"] = "هِيلْب"
            KNOWN_NAMES["search"] = "سِيرْتْش"
            KNOWN_NAMES["home"] = "هُوم"
            KNOWN_NAMES["back"] = "بَاك"
            KNOWN_NAMES["next"] = "نِكْسْت"
            KNOWN_NAMES["play"] = "بْلَاي"
            KNOWN_NAMES["pause"] = "بُوز"
            KNOWN_NAMES["stop"] = "سْتُوب"
            KNOWN_NAMES["resume"] = "رِيزْيُوم"
            KNOWN_NAMES["mute"] = "مْيُوت"
            KNOWN_NAMES["unmute"] = "أَنْمْيُوت"
            KNOWN_NAMES["reset"] = "رِيسِت"
            KNOWN_NAMES["restart"] = "رِيسْتَارْت"
            KNOWN_NAMES["power"] = "بَاوَر"
            KNOWN_NAMES["bluetooth"] = "بْلُوتُوث"
            KNOWN_NAMES["battery"] = "بَاتِرِي"
            KNOWN_NAMES["message"] = "مِسِيدْج"
            KNOWN_NAMES["messages"] = "مِسِيدْجِز"
            KNOWN_NAMES["call"] = "كُول"
            KNOWN_NAMES["calls"] = "كُولْز"
            KNOWN_NAMES["video"] = "فِيدْيُو"
            KNOWN_NAMES["audio"] = "أُودْيُو"
            KNOWN_NAMES["music"] = "مْيُوزِيك"
            KNOWN_NAMES["photo"] = "فُوتُو"
            KNOWN_NAMES["photos"] = "فُوتُوز"
            KNOWN_NAMES["image"] = "إِيمِيدْج"
            KNOWN_NAMES["camera"] = "كَامِيرَا"
            KNOWN_NAMES["gallery"] = "جَالِيرِي"
            KNOWN_NAMES["file"] = "فَايْل"
            KNOWN_NAMES["files"] = "فَايْلْز"
            KNOWN_NAMES["folder"] = "فُولْدَر"
            KNOWN_NAMES["document"] = "دُوكْيُومِنْت"
            KNOWN_NAMES["documents"] = "دُوكْيُومِنْتْس"
            KNOWN_NAMES["page"] = "بِيج"
            KNOWN_NAMES["view"] = "فْيُو"
            KNOWN_NAMES["edit"] = "إِدِيت"
            KNOWN_NAMES["delete"] = "دِلِيت"
            KNOWN_NAMES["remove"] = "رِيمُوف"
            KNOWN_NAMES["clear"] = "كْلِير"
            KNOWN_NAMES["copy"] = "كُوبِي"
            KNOWN_NAMES["cut"] = "كَات"
            KNOWN_NAMES["paste"] = "بَاسْت"
            KNOWN_NAMES["share"] = "شِير"
            KNOWN_NAMES["send"] = "سِينْد"
            KNOWN_NAMES["save"] = "سِيف"
            KNOWN_NAMES["open"] = "أُوبِن"
            KNOWN_NAMES["close"] = "كْلُوز"
            KNOWN_NAMES["exit"] = "إِكْزِت"
            KNOWN_NAMES["select"] = "سِلِكْت"
            KNOWN_NAMES["all"] = "أُول"
            KNOWN_NAMES["none"] = "نَان"
            KNOWN_NAMES["yes"] = "يَس"
            KNOWN_NAMES["no"] = "نُو"
            KNOWN_NAMES["true"] = "تْرُو"
            KNOWN_NAMES["false"] = "فُولْس"
            KNOWN_NAMES["code"] = "كُود"
            KNOWN_NAMES["data"] = "دَاتَا"
            KNOWN_NAMES["mode"] = "مُود"
            KNOWN_NAMES["test"] = "تِسْت"
            KNOWN_NAMES["version"] = "فِيرْشِن"
            KNOWN_NAMES["excel"] = "إِكْسِل"
            KNOWN_NAMES["word"] = "وِرْد"
            KNOWN_NAMES["outlook"] = "آوتْلُوك"
            KNOWN_NAMES["teams"] = "تِيمْز"
            KNOWN_NAMES["office"] = "أُوفِيس"
            KNOWN_NAMES["iphone"] = "آيْفُون"
        }

        private fun transliterateWord(word: String?): String {
            if (word.isNullOrEmpty()) return word ?: ""
            val lower = word.lowercase(Locale.ROOT)

            if (KNOWN_NAMES.containsKey(lower)) return KNOWN_NAMES[lower]!!
            if (word.length == 1 && SINGLE_LETTER_MAP.containsKey(word[0])) {
                return SINGLE_LETTER_MAP[word[0]]!!
            }

            if (word.length in 2..4 && word.matches(Regex("[A-Z]+"))) {
                val acronymSb = StringBuilder()
                for (i in word.indices) {
                    val ch = word[i]
                    if (SINGLE_LETTER_MAP.containsKey(ch)) {
                        if (acronymSb.isNotEmpty()) acronymSb.append(" ")
                        acronymSb.append(SINGLE_LETTER_MAP[ch])
                    }
                }
                if (acronymSb.isNotEmpty()) return acronymSb.toString()
            }

            var res = lower
            if (res.length >= 4 && res.endsWith("e")) {
                val penultimate = res[res.length - 2]
                val antepenultimate = res[res.length - 3]
                if ("bcdfghjklmnpqrstvwxyz".indexOf(penultimate) != -1 && "aeiouy".indexOf(antepenultimate) != -1) {
                    val prefix = res.substring(0, res.length - 3)
                    res = when (antepenultimate) {
                        'a' -> prefix + "ِي" + penultimate
                        'o' -> prefix + "ُو" + penultimate
                        'i' -> prefix + "َاي" + penultimate
                        'u' -> prefix + "ُو" + penultimate
                        else -> res.substring(0, res.length - 1)
                    }
                }
            }

            for (combo in LATIN_COMBOS) {
                res = res.replace(combo[0], combo[1])
            }

            res = res.replace(Regex("c(?=[eiy])"), "س")
            res = res.replace("c", "ك")
            res = res.replace(Regex("g(?=[eiy])"), "ج")
            res = res.replace("g", "ج")
            res = res.replace(Regex("^x"), "ز")
            res = res.replace("x", "كْس")

            val finalRes = StringBuilder()
            for (i in res.indices) {
                val ch = res[i]
                if (LATIN_CHAR_MAP.containsKey(ch)) {
                    finalRes.append(LATIN_CHAR_MAP[ch])
                } else {
                    finalRes.append(ch)
                }
            }
            var out = finalRes.toString()

            if (out.startsWith("َا") || out.startsWith("ا")) {
                out = "أَ" + out.replaceFirst(Regex("^َا+"), "").replaceFirst(Regex("^ا+"), "")
            } else if (out.startsWith("ِي") || out.startsWith("ي")) {
                out = "إِ" + out.replaceFirst(Regex("^ِي+"), "").replaceFirst(Regex("^ي+"), "")
            } else if (out.startsWith("ُو") || out.startsWith("و")) {
                out = "أُ" + out.replaceFirst(Regex("^ُو+"), "").replaceFirst(Regex("^و+"), "")
            }
            return out
        }

        private val PAT_LATIN_WORD =
            Pattern.compile("\\b[A-Za-z][A-Za-z0-9]*(?:[.'\\-][A-Za-z0-9]+)*\\b")

        @JvmStatic
        fun transliterate(text: String?): String {
            if (text.isNullOrEmpty()) return text ?: ""
            val matcher = PAT_LATIN_WORD.matcher(text)
            val sb = StringBuffer()
            while (matcher.find()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(transliterateWord(matcher.group(0))))
            }
            matcher.appendTail(sb)
            return sb.toString()
        }

        @JvmStatic
        fun isTransliterated(word: String?): Boolean {
            if (word == null) return false
            val lower = word.lowercase(Locale.ROOT)
            return KNOWN_NAMES.containsKey(lower) || KNOWN_NAMES.containsValue(word)
        }
    }

    // NUMBER TO WORDS (ARABIC)
    class Num2WordsAr {
        private val ARABIC_ONES = arrayOf(
            "", "وَاحِدٌ", "اثْنَانِ", "ثَلَاثَةٌ", "أَرْبَعَةٌ", "خَمْسَةٌ", "سِتَّةٌ", "سَبْعَةٌ", "ثَمَانِيَةٌ", "تِسْعَةٌ",
            "عَشَرَةٌ", "أَحَدَ عَشَرَ", "اثْنَا عَشَرَ", "ثَلَاثَةَ عَشَرَ", "أَرْبَعَةَ عَشَرَ", "خَمْسَةَ عَشَرَ",
            "سِتَّةَ عَشَرَ", "سَبْعَةَ عَشَرَ", "ثَمَانِيَةَ عَشَرَ", "تِسْعَةَ عَشَرَ"
        )
        private val ARABIC_FEMININE_ONES = arrayOf(
            "", "إِحْدَى", "اثْنَتَانِ", "ثَلَاثٌ", "أَرْبَعٌ", "خَمْسٌ", "سِتٌّ", "سَبْعٌ", "ثَمَانٍ", "تِسْعٌ",
            "عَشْرٌ", "إِحْدَى عَشْرَةَ", "اثْنَتَا عَشْرَةَ", "ثَلَاثَ عَشْرَةَ", "أَرْبَعَ عَشْرَةَ",
            "خَمْسَ عَشْرَةَ", "سِتَّ عَشْرَةَ", "سَبْعَ عَشْرَةَ", "ثَمَانِيَ عَشْرَةَ", "تِسْعَ عَشْرَةَ"
        )
        private val ARABIC_ORDINAL = arrayOf(
            "", "أَوَّلُ", "ثَانِي", "ثَالِثٌ", "رَابِعٌ", "خَامِسٌ", "سَادِسٌ", "سَابِعٌ", "ثَامِنٌ",
            "تَاسِعٌ", "عَاشِرٌ", "حَادِيَ عَشَرَ", "ثَانِيَ عَشَرَ", "ثَالِثَ عَشَرَ", "رَابِعَ عَشَرَ",
            "خَامِسَ عَشَرَ", "سَادِسَ عَشَرَ", "سَابِعَ عَشَرَ", "ثَامِنَ عَشَرَ", "تَاسِعَ عَشَرَ"
        )
        private val ARABIC_TENS = arrayOf(
            "عِشْرُونَ", "ثَلَاثُونَ", "أَرْبَعُونَ", "خَمْسُونَ", "سِتُّونَ", "سَبْعُونَ", "ثَمَانُونَ", "تِسْعُونَ"
        )
        private val ARABIC_HUNDREDS = arrayOf(
            "", "مِائَةٌ", "مِائَتَانِ", "ثَلَاثُمِائَةٍ", "أَرْبَعُمِائَةٍ", "خَمْسُمِائَةٍ", "سِتُّمِائَةٍ",
            "سَبْعُمِائَةٍ", "ثَمَانِمِائَةٍ", "تِسْعُمِائَةٍ"
        )
        private val ARABIC_APPENDED_TWOS = arrayOf(
            "مِائَتَا", "أَلْفَا", "مِلْيُونَا", "مِلْيَارَا", "تِرِيلْيُونَا", "كُوَادْرِيلْيُونَا",
            "كُوِينْتِلْيُونَا", "سِكْسْتِيلْيُونَا", "سَبْتِيلْيُونَا", "أُوكْتِيلْيُونَا",
            "نُونِيلْيُونَا", "دِيسِيلْيُونَا", "أَنْدِسِيلْيُونَا", "دُودِيسِيلْيُونَا",
            "تْرِيدِيسِيلْيُونَا", "كُوَادْرِيسِيلْيُونَا", "كُوِينْتِينِيلْيُونَا"
        )
        private val ARABIC_TWOS = arrayOf(
            "مِائَتَانِ", "أَلْفَانِ", "مِلْيُونَانِ", "مِلْيَارَانِ", "تِرِيلْيُونَانِ",
            "كُوَادْرِيلْيُونَانِ", "كُوِينْتِلْيُونَانِ", "سِكْسْتِيلْيُونَانِ", "سَبْتِيلْيُونَانِ",
            "أُوكْتِيلْيُونَانِ", "نُونِيلْيُونَانِ", "دِيسِيلْيُونَانِ", "أَنْدِسِيلْيُونَانِ",
            "دُودِيسِيلْيُونَانِ", "تْرِيدِيسِيلْيُونَانِ", "كُوَادْرِيسِيلْيُونَانِ", "كُوِينْتِينِيلْيُونَانِ"
        )
        private val ARABIC_GROUP = arrayOf(
            "مِائَةٌ", "أَلْفٌ", "مِلْيُونٌ", "مِلْيَارٌ", "تِرِيلْيُونٌ", "كُوَادْرِيلْيُونٌ",
            "كُوِينْتِلْيُونٌ", "سِكْسْتِيلْيُونٌ", "سَبْتِيلْيُونٌ", "أُوكْتِيلْيُونٌ", "نُونِيلْيُونٌ",
            "دِيسِيلْيُونٌ", "أَنْدِسِيلْيُونٌ", "دُودِيسِيلْيُونٌ", "تْرِيدِيسِيلْيُونٌ",
            "كُوَادْرِيسِيلْيُونٌ", "كُوِينْتِينِيلْيُونٌ"
        )
        private val ARABIC_APPENDED_GROUP = arrayOf(
            "", "أَلْفاً", "مِلْيُوناً", "مِلْيَاراً", "تِرِيلْيُوناً", "كُوَادْرِيلْيُوناً",
            "كُوِينْتِلْيُوناً", "سِكْسْتِيلْيُوناً", "سَبْتِيلْيُوناً", "أُوكْتِيلْيُوناً",
            "نُونِيلْيُوناً", "دِيسِيلْيُوناً", "أَنْدِسِيلْيُوناً", "دُودِيسِيلْيُوناً",
            "تْرِيدِيسِيلْيُوناً", "كُوَادْرِيسِيلْيُوناً", "كُوِينْتِينِيلْيُوناً"
        )
        private val ARABIC_PLURAL_GROUPS = arrayOf(
            "", "آلَافٍ", "مَلَايِينُ", "مِلْيَارَاتٌ", "تِرِيلْيُونَاتٌ", "كُوَادْرِيلْيُونَاتٌ",
            "كُوِينْتِلْيُونَاتٌ", "سِكْسْتِيلْيُونَاتٌ", "سَبْتِيلْيُونَاتٌ", "أُوكْتِيلْيُونَاتٌ",
            "نُونِيلْيُونَاتٌ", "دِيسِيلْيُونَاتٌ", "أَنْدِسِيلْيُونَاتٌ", "دُودِيسِيلْيُونَاتٌ",
            "تْرِيدِيسِيلْيُونَاتٌ", "كُوَادْرِيسِيلْيُونَاتٌ", "كُوِينْتِينِيلْيُونَاتٌ"
        )

        private var isCurrencyNameFeminine = false

        private fun digitFeminineStatus(digit: Int, groupLevel: Int): String {
            if (groupLevel == 0 && isCurrencyNameFeminine) {
                return ARABIC_FEMININE_ONES[digit]
            }
            return ARABIC_ONES[digit]
        }

        private fun processArabicGroup(groupNumber: Long, groupLevel: Int, remainingNumber: BigInteger): String {
            val tens = groupNumber % 100
            val hundreds = groupNumber / 100
            var retVal = ""

            if (hundreds > 0) {
                if (tens == 0L && hundreds == 2L && groupLevel > 0) {
                    retVal = ARABIC_APPENDED_TWOS[0]
                } else {
                    retVal = ARABIC_HUNDREDS[hundreds.toInt()]
                    if (retVal.isNotEmpty() && tens != 0L) {
                        retVal += " وَ"
                    }
                }
            }

            if (tens > 0) {
                if (tens < 20) {
                    if (tens == 2L && hundreds == 0L && groupLevel > 0) {
                        retVal = ARABIC_TWOS[groupLevel]
                    } else {
                        if (tens == 1L && groupLevel > 0) {
                            retVal += ARABIC_GROUP[groupLevel]
                        } else {
                            retVal += digitFeminineStatus(tens.toInt(), groupLevel)
                        }
                    }
                } else {
                    val ones = tens % 10
                    val t = (tens / 10) - 2
                    if (ones > 0) {
                        retVal += digitFeminineStatus(ones.toInt(), groupLevel)
                    }
                    if (retVal.isNotEmpty() && ones != 0L) {
                        retVal += " وَ"
                    }
                    retVal += ARABIC_TENS[t.toInt()]
                }
            }
            return retVal
        }

        private fun convertToArabic(number: BigInteger): String {
            if (number == BigInteger.ZERO) return "صِفْرٌ"
            var tempNumber = number
            var retVal = ""
            var group = 0
            val thousand = BigInteger.valueOf(1000)
            while (tempNumber > BigInteger.ZERO) {
                val numberToProcess = tempNumber.remainder(thousand).toLong()
                tempNumber = tempNumber.divide(thousand)
                var groupDescription = processArabicGroup(numberToProcess, group, tempNumber)
                if (groupDescription.isNotEmpty()) {
                    if (group > 0) {
                        if (numberToProcess != 2L && numberToProcess != 1L) {
                            val lastTwo = numberToProcess % 100
                            groupDescription = if (lastTwo in 3..10) {
                                groupDescription + " " + ARABIC_PLURAL_GROUPS[group]
                            } else if (lastTwo in 11..99) {
                                groupDescription + " " + ARABIC_APPENDED_GROUP[group]
                            } else {
                                groupDescription + " " + ARABIC_GROUP[group]
                            }
                        }
                        retVal = if (retVal.isNotEmpty()) {
                            groupDescription + " وَ" + retVal
                        } else {
                            groupDescription
                        }
                    } else {
                        retVal = groupDescription
                    }
                }
                group++
            }
            return retVal
        }

        fun toCardinal(number: BigInteger): String {
            isCurrencyNameFeminine = false
            var minus = ""
            var num = number
            if (num < BigInteger.ZERO) {
                minus = "سَالِبُ "
                num = num.negate()
            }
            return (minus + convertToArabic(num).trim()).trim()
        }

        fun toOrdinal(number: BigInteger): String {
            if (number in BigInteger.ZERO..BigInteger.valueOf(19)) {
                return ARABIC_ORDINAL[number.toInt()]
            }
            isCurrencyNameFeminine = (number < BigInteger.valueOf(100))
            return convertToArabic(number).trim()
        }

        companion object {
            @JvmStatic fun cardinal(n: BigInteger): String = Num2WordsAr().toCardinal(n)
            @JvmStatic fun cardinal(n: Long): String = cardinal(BigInteger.valueOf(n))
            @JvmStatic fun ordinal(n: BigInteger): String = Num2WordsAr().toOrdinal(n)
            @JvmStatic fun ordinal(n: Long): String = ordinal(BigInteger.valueOf(n))
        }
    }

    // ARABIC NORMALIZATION ENGINE
    object ArNorm {
        @JvmStatic
        fun toAsciiDigits(s: String?): String {
            if (s == null) return ""
            val b = StringBuilder()
            for (i in s.indices) {
                val c = s[i]
                when (c) {
                    in '\u0660'..'\u0669' -> b.append(('0'.code + (c.code - '\u0660'.code)).toChar())
                    in '\u06F0'..'\u06F9' -> b.append(('0'.code + (c.code - '\u06F0'.code)).toChar())
                    else -> b.append(c)
                }
            }
            return b.toString()
        }

        @JvmStatic
        fun foldDigits(s: String?): String? = if (s == null) null else toAsciiDigits(s)

        @JvmStatic fun cardinal(n: String): String = Num2WordsAr.cardinal(BigInteger(n))
        @JvmStatic fun cardinal(n: Long): String = Num2WordsAr.cardinal(n)

        private val DIGIT_NAMES = HashMap<Char, String>().apply {
            put('0', "صِفْرٌ"); put('1', "وَاحِدٌ"); put('2', "اثْنَانِ")
            put('3', "ثَلَاثَةٌ"); put('4', "أَرْبَعَةٌ"); put('5', "خَمْسَةٌ")
            put('6', "سِتَّةٌ"); put('7', "سَبْعَةٌ"); put('8', "ثَمَانِيَةٌ")
            put('9', "تِسْعَةٌ")
        }

        @JvmStatic
        fun spellDigits(digits: String): String {
            val b = StringBuilder()
            for (i in digits.indices) {
                if (i > 0) b.append(" ")
                val c = digits[i]
                b.append(DIGIT_NAMES[c] ?: c.toString())
            }
            return b.toString()
        }

        private val FEM_ORD = HashMap<String, String>().apply {
            put("أَوَّلُ", "الأُولَى"); put("ثَانِي", "الثَّانِيَةُ"); put("ثَالِثٌ", "الثَّالِثَةُ")
            put("رَابِعٌ", "الرَّابِعَةُ"); put("خَامِسٌ", "الخَامِسَةُ"); put("سَادِسٌ", "السَّادِسَةُ")
            put("سَابِعٌ", "السَّابِعَةُ"); put("ثَامِنٌ", "الثَّامِنَةُ"); put("تَاسِعٌ", "التَّاسِعَةُ")
            put("عَاشِرٌ", "العَاشِرَةُ")
        }

        @JvmStatic fun ordinal(n: Int): String = ordinal(n, false)

        @JvmStatic
        fun ordinal(n: Int, feminine: Boolean): String {
            val w = Num2WordsAr.ordinal(n.toLong())
            if (w.isEmpty()) return n.toString()
            if (feminine && !w.endsWith("ة") && !w.endsWith("َةُ") && !w.endsWith("َةَ")) {
                return FEM_ORD[w] ?: w
            }
            return w
        }

        private val ORD_UNITS_M = arrayOf(
            "", "الأَوَّلُ", "الثَّانِي", "الثَّالِثُ", "الرَّابِعُ", "الخَامِسُ",
            "السَّادِسُ", "السَّابِعُ", "الثَّامِنُ", "التَّاسِعُ", "العَاشِرُ"
        )
        private val ORD_UNITS_F = arrayOf(
            "", "الأُولَى", "الثَّانِيَةُ", "الثَّالِثَةُ", "الرَّابِعَةُ", "الخَامِسَةُ",
            "السَّادِسَةُ", "السَّابِعَةُ", "الثَّامِنَةُ", "التَّاسِعَةُ", "العَاشِرَةُ"
        )

        @JvmStatic
        fun ordinalDefinite(n: Int, feminine: Boolean): String {
            val tbl = if (feminine) ORD_UNITS_F else ORD_UNITS_M
            if (n in 1..10) return tbl[n]
            if (n == 11 || n == 12) {
                if (feminine) return if (n == 11) "الحَادِيَةَ عَشْرَةَ" else "الثَّانِيَةَ عَشْرَةَ"
                return if (n == 11) "الحَادِيَ عَشَرَ" else "الثَّانِيَ عَشَرَ"
            }
            if (n in 13..19) {
                val u = tbl[n - 10].replaceFirst("ال", "")
                return if (feminine) ("ال$u عَشْرَةَ") else ("ال$u عَشَرَ")
            }
            if (n == 20) return "العِشْرُونَ"
            if (n == 30) return "الثَّلَاثُونَ"
            if (n in 21..39) {
                val u = tbl[n % 10]
                val t10 = if (n / 10 == 2) "العِشْرُونَ" else "الثَّلَاثُونَ"
                return "$u وَ$t10"
            }
            return ordinal(n, feminine)
        }

        @JvmStatic fun ordinalDefinite(n: Int): String = ordinalDefinite(n, true)

        private val MONTHS = arrayOf(
            "", "يَنَايِرَ", "فِبْرَايِرَ", "مَارِسَ", "أَبْرِيلَ", "مَايُو", "يُونْيُو",
            "يُولْيُو", "أُغُسْطُسَ", "سِبْتَمْبِرَ", "أُكْتُوبِرَ", "نُوفَمْبِرَ", "دِيسَمْبِرَ"
        )

        private val CURRENCY = LinkedHashMap<String, String>().apply {
            val c = HashMap<String, String>()
            c["$"] = "دُولَاراً"; c["USD"] = "دُولَاراً"; c["£"] = "جُنَيْهاً"; c["GBP"] = "جُنَيْهاً"
            c["€"] = "يُورُو"; c["EUR"] = "يُورُو"; c["¥"] = "يِين"
            c["ر.س"] = "رِيَالاً"; c["ريال"] = "رِيَالاً"; c["SAR"] = "رِيَالاً"
            c["د.إ"] = "دِرْهَماً"; c["درهم"] = "دِرْهَماً"; c["AED"] = "دِرْهَماً"
            c["ج.م"] = "جُنَيْهاً مِصْرِيّاً"; c["د.ك"] = "دِينَاراً كُوَيْتِيّاً"; c["د.ع"] = "دِينَاراً عِرَاقِيّاً"
            c["ل.ل"] = "لِيرَةً لُبْنَانِيَّةً"; c["ل.س"] = "لِيرَةً سُورِيَّةً"; c["د.أ"] = "دِينَاراً أُرْدُنِيّاً"
            c["ر.ق"] = "رِيَالاً قَطَرِيّاً"; c["د.ب"] = "دِينَاراً بَحْرَيْنِيّاً"; c["ر.ع"] = "رِيَالاً عُمَانِيّاً"
            val keys = ArrayList(c.keys)
            keys.sortByDescending { it.length }
            for (k in keys) put(k, c[k]!!)
        }

        private class CompiledCurrency(
            val sym: String,
            val unit: String
        ) {
            val patternPost: Pattern
            val patternPre: Pattern?

            init {
                val esym = Pattern.quote(sym)
                patternPost = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*$esym")
                patternPre = if (sym == "$" || sym == "£" || sym == "€" || sym == "¥") {
                    Pattern.compile("$esym\\s*(\\d+(?:\\.\\d+)?)")
                } else {
                    null
                }
            }
        }

        private val COMPILED_CURRENCIES = ArrayList<CompiledCurrency>().apply {
            for ((key, value) in CURRENCY) {
                add(CompiledCurrency(key, value))
            }
        }

        private val PAT_DATES = Pattern.compile("\\b(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{2,4})\\b")
        private val PAT_TIME_COLON_ARNORM = Pattern.compile(
            "(?:السَّاعَةُ\\s+|السَّاعَةَ\\s+|الساعة\\s+)?(?<!\\d)(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\s*(صباحاً|صباحا|مساءً|مساء|a\\.m\\.|p\\.m\\.|am|pm|ص\\.|م\\.|ص(?!\\w|[\\u0621-\\u064A\\u0670\\u0671])|م(?!\\w|[\\u0621-\\u064A\\u0670\\u0671]))?(?!\\d)",
            Pattern.CASE_INSENSITIVE
        )
        private val PAT_TIME_SINGLE_ARNORM = Pattern.compile(
            "(?:السَّاعَةُ|السَّاعَةَ|الساعة)\\s+(\\d{1,2})\\s*(صباحاً|صباحا|مساءً|مساء|a\\.m\\.|p\\.m\\.|am|pm|ص\\.|م\\.|ص(?!\\w|[\\u0621-\\u064A\\u0670\\u0671])|م(?!\\w|[\\u0621-\\u064A\\u0670\\u0671]))?(?!\\d)",
            Pattern.CASE_INSENSITIVE
        )
        private val PAT_TIME_STANDALONE_ARNORM = Pattern.compile(
            "(?<![\\d:])\\b(1[0-2]|0?[1-9])\\s*(صباحاً|صباحا|مساءً|مساء|a\\.m\\.|p\\.m\\.|am|pm|ص\\.|م\\.|ص(?!\\w|[\\u0621-\\u064A\\u0670\\u0671])|م(?!\\w|[\\u0621-\\u064A\\u0670\\u0671]))(?![\\d:])",
            Pattern.CASE_INSENSITIVE
        )
        private val PAT_PERCENT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%")
        private val PAT_PERMILLE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*‰")
        private val PAT_MATH_PLUS = Pattern.compile("(?<=\\d)\\s*\\+\\s*(?=\\d)")
        private val PAT_MATH_MINUS = Pattern.compile("(?<=\\d)\\s*[-\u2212]\\s*(?=\\d)")
        private val PAT_MATH_MUL = Pattern.compile("(?<=\\d)\\s*[\u00D7x]\\s*(?=\\d)")
        private val PAT_MATH_DIV = Pattern.compile("(?<=\\d)\\s*[\u00F7/]\\s*(?=\\d)")
        private val PAT_MATH_EQ = Pattern.compile("(?<=\\d)\\s*=\\s*")
        private val PAT_PHONE = Pattern.compile("\\+?\\d[\\d\\s\\-]{6,}\\d")
        private val PAT_ORDINALS = Pattern.compile("الـ\\s*(\\d{1,3})")
        private val PAT_ROMAN = Pattern.compile("\\b(?=[IVX]+\\b)[IVX]{1,5}\\b")
        private val PAT_VERSION_CLEAN = Pattern.compile("(?i)(إصدار|النسخة|نسخة|تطبيق|الإصدار)\\s+[vV](\\d+)")
        private val PAT_VERSION_V = Pattern.compile("(?i)\\b[vV](\\d+(?:\\.\\d+)*)\\b")
        private val PAT_VERSION_MULTIDOT = Pattern.compile("\\b(\\d+(?:\\.\\d+)+)\\b")
        private val PAT_DECIMAL = Pattern.compile("(\\d+)[.,،\u066B](\\d+)")
        private val PAT_INTEGER = Pattern.compile("\\d+")
        private val PAT_MULTI_SPACE = Pattern.compile("\\s{2,}")

        private val SYMBOLS = LinkedHashMap<String, String>().apply {
            put("&", "وَ"); put("@", "آت"); put("#", "رَقْم")
            put("%", "بِالْمِئَة"); put("٪", "بِالْمِئَة"); put("‰", "بِالْأَلْف")
            put("+", "زَائِد"); put("=", "يُسَاوِي"); put("≠", "لَا يُسَاوِي")
            put("<", "أَصْغَرُ مِنْ"); put(">", "أَكْبَرُ مِنْ")
            put("≤", "أَصْغَرُ أَوْ يُسَاوِي"); put("≥", "أَكْبَرُ أَوْ يُسَاوِي")
            put("±", "زَائِدٌ أَوْ نَاقِص"); put("×", "ضَرْب"); put("÷", "قِسْمَة")
            put("√", "جَذْر"); put("∞", "مَا لَا نِهَايَة"); put("π", "بَاي")
            put("°", "دَرَجَة"); put("µ", "مِيكْرُو"); put("©", "حُقُوقُ النَّشْر")
            put("®", "عَلَامَةٌ مُسَجَّلَة"); put("™", "عَلَامَةٌ تِجَارِيَّة")
            put("§", "فِقْرَة"); put("¶", "عَلَامَةُ فِقْرَة")
            put("†", "عَلَامَةُ إِحَالَة"); put("•", "نُقْطَةُ تَعْدَاد")
            put("·", "نُقْطَةٌ وَسَطِيَّة")
            put("★", "نَجْمَة"); put("☆", "نَجْمَة"); put("٭", "نَجْمَة")
            put("→", "يُؤَدِّي إِلَى")
            put("←", "مِنْ"); put("↔", "ذَهَابًا وَإِيَابًا"); put("⇒", "إِذَنْ")
            put("~", "تَقْرِيبًا"); put("|", "خَطٌّ عَمُودِيّ")
            put("﴿", "قَوْسٌ قُرْآنِيٌّ مَفْتُوح"); put("﴾", "قَوْسٌ قُرْآنِيٌّ مُغْلَق")
            put("۞", "رُبْعُ حِزْب"); put("۩", "عَلَامَةُ سَجْدَة")
        }

        private val ROMAN = arrayOf(
            arrayOf("XXI", "21"), arrayOf("XIX", "19"), arrayOf("XVIII", "18"), arrayOf("XVII", "17"),
            arrayOf("XVI", "16"), arrayOf("XIV", "14"), arrayOf("XIII", "13"), arrayOf("XII", "12"),
            arrayOf("XI", "11"), arrayOf("VIII", "8"), arrayOf("VII", "7"), arrayOf("VI", "6"),
            arrayOf("XX", "20"), arrayOf("XV", "15"), arrayOf("IX", "9"), arrayOf("IV", "4"),
            arrayOf("X", "10"), arrayOf("V", "5"), arrayOf("III", "3"), arrayOf("II", "2"), arrayOf("I", "1")
        )

        private fun amountWords(num: String, unit: String): String {
            if (num.contains(".")) {
                val parts = num.split(".", limit = 2)
                val whole = parts[0]
                val frac = parts[1]
                val w = cardinal(whole)
                if (frac.isNotEmpty() && frac.toLongOrNull() != 0L) {
                    val f2 = (frac + "00").substring(0, 2)
                    return "$w $unit وَ" + cardinal(f2)
                }
                return "$w $unit"
            }
            return cardinal(num) + " " + unit
        }

        private fun cardinalDec(num: String): String {
            val parts = num.split(Regex("[.,،\u066B]"), limit = 2)
            if (parts.size == 2) {
                return cardinalDec(parts[0], parts[1])
            }
            return cardinal(num)
        }

        private fun cardinalDec(whole: String, frac: String): String {
            if (frac == "0" || frac == "00") {
                return cardinal(whole) + " فَاصِلَة صِفْرٌ"
            }
            return cardinal(whole) + " فَاصِلَة " + spellDigits(frac)
        }

        private fun xDates(t: String): String {
            val m = PAT_DATES.matcher(t)
            val sb = StringBuffer()
            while (m.find()) {
                val d = m.group(1).toInt()
                val mo = m.group(2).toInt()
                var y = m.group(3).toInt()
                if (d < 1 || d > 31 || mo < 1 || mo > 12) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)))
                    continue
                }
                if (y < 100) y += if (y < 50) 2000 else 1900
                val out = ordinalDefinite(d, false) + " مِنْ " + MONTHS[mo] + " " + cardinal(y.toLong())
                m.appendReplacement(sb, Matcher.quoteReplacement(out))
            }
            m.appendTail(sb)
            return sb.toString()
        }

        private val DIAC_ORDINAL_HOURS = arrayOf(
            "", "الوَاحِدَةُ", "الثَّانِيَةُ", "الثَّالِثَةُ", "الرَّابِعَةُ", "الخَامِسَةُ",
            "السَّادِسَةُ", "السَّابِعَةُ", "الثَّامِنَةُ", "التَّاسِعَةُ", "العَاشِرَةُ",
            "الحَادِيَةَ عَشْرَةَ", "الثَّانِيَةَ عَشْرَةَ"
        )

        private val DIAC_MIN_ONES_3_10 = arrayOf(
            "", "", "", "ثَلَاثُ", "أَرْبَعُ", "خَمْسُ", "سِتُّ", "سَبْعُ", "ثَمَانِي", "تِسْعُ", "عَشْرُ"
        )

        private val DIAC_MIN_TEENS = arrayOf(
            "", "", "", "", "", "", "", "", "", "", "",
            "إِحْدَى عَشْرَةَ", "اثْنَتَا عَشْرَةَ", "ثَلَاثَ عَشْرَةَ", "أَرْبَعَ عَشْرَةَ",
            "خَمْسَ عَشْرَةَ", "سِتَّ عَشْرَةَ", "سَبْعَ عَشْرَةَ", "ثَمَانِيَ عَشْرَةَ", "تِسْعَ عَشْرَةَ"
        )

        private val DIAC_MIN_TENS = arrayOf(
            "", "", "عِشْرُونَ", "ثَلَاثُونَ", "أَرْبَعُونَ", "خَمْسُونَ"
        )

        private val DIAC_MIN_COMPOUND_ONES = arrayOf(
            "", "", "", "ثَلَاثٌ", "أَرْبَعٌ", "خَمْسٌ", "سِتٌّ", "سَبْعٌ", "ثَمَانٍ", "تِسْعٌ"
        )

        @JvmStatic
        fun getDiacMinute(mi: Int): String {
            if (mi <= 0 || mi > 59) return ""
            if (mi == 1) return "وَدَقِيقَةٌ وَاحِدَةٌ"
            if (mi == 2) return "وَدَقِيقَتَانِ"
            if (mi in 3..10) {
                return "وَ" + DIAC_MIN_ONES_3_10[mi] + " دَقَائِقَ"
            }
            if (mi in 11..19) {
                return "وَ" + DIAC_MIN_TEENS[mi] + " دَقِيقَةً"
            }
            val ones = mi % 10
            val tens = mi / 10
            if (tens in 2..5) {
                return if (ones == 0) {
                    "وَ" + DIAC_MIN_TENS[tens] + " دَقِيقَةً"
                } else if (ones == 1) {
                    "وَإِحْدَى وَ" + DIAC_MIN_TENS[tens] + " دَقِيقَةً"
                } else if (ones == 2) {
                    "وَاثْنَتَانِ وَ" + DIAC_MIN_TENS[tens] + " دَقِيقَةً"
                } else {
                    "وَ" + DIAC_MIN_COMPOUND_ONES[ones] + " وَ" + DIAC_MIN_TENS[tens] + " دَقِيقَةً"
                }
            }
            return ""
        }

        private fun xTimes(t: String?): String? {
            if (t == null) return null

            val m = PAT_TIME_COLON_ARNORM.matcher(t)
            val sb = StringBuffer()
            while (m.find()) {
                val h = m.group(1).toInt()
                val mi = m.group(2).toInt()
                val sec = if (m.group(3) != null) m.group(3).toInt() else -1
                val periodStr = m.group(4)

                if (h !in 0..23 || mi !in 0..59) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)))
                    continue
                }

                var period = ""
                if (periodStr != null) {
                    val pLower = periodStr.lowercase(Locale.ROOT)
                    if (pLower.contains("ص") || pLower.contains("am")) {
                        period = "صَبَاحاً"
                    } else if (pLower.contains("م") || pLower.contains("pm")) {
                        period = "مَسَاءً"
                    }
                }

                var hh = h
                if (h == 0) {
                    hh = 12
                    if (period.isEmpty()) period = "مُنْتَصَفِ اللَّيْلِ"
                } else if (h == 12) {
                    hh = 12
                    if (period.isEmpty()) period = "ظُهْراً"
                } else if (h > 12) {
                    hh = h - 12
                    if (period.isEmpty()) period = "مَسَاءً"
                } else if (h in 1..11) {
                    hh = h
                    if (period.isEmpty() && m.group(1).startsWith("0")) {
                        period = "صَبَاحاً"
                    }
                }

                val hourWord = if (hh in 1..12) DIAC_ORDINAL_HOURS[hh] else hh.toString()
                val minWord = getDiacMinute(mi).let { if (it.isNotEmpty()) " $it" else "" }
                val secWord = if (sec > 0) " وَ" + cardinal(sec.toLong()) + " ثَانِيَةً" else ""
                val out = hourWord + minWord + secWord + (if (period.isEmpty()) "" else " $period")
                m.appendReplacement(sb, Matcher.quoteReplacement(out))
            }
            m.appendTail(sb)
            var res = sb.toString()

            val m2 = PAT_TIME_SINGLE_ARNORM.matcher(res)
            val sb2 = StringBuffer()
            while (m2.find()) {
                val h = m2.group(1).toInt()
                val periodStr = m2.group(2)
                if (h !in 1..24) {
                    m2.appendReplacement(sb2, Matcher.quoteReplacement(m2.group(0)))
                    continue
                }

                var period = ""
                if (periodStr != null) {
                    val pLower = periodStr.lowercase(Locale.ROOT)
                    if (pLower.contains("ص") || pLower.contains("am")) {
                        period = "صَبَاحاً"
                    } else if (pLower.contains("م") || pLower.contains("pm")) {
                        period = "مَسَاءً"
                    }
                }

                var hh = h
                if (h == 24 || h == 0) {
                    hh = 12
                    if (period.isEmpty()) period = "مُنْتَصَفِ اللَّيْلِ"
                } else if (h == 12) {
                    hh = 12
                    if (period.isEmpty()) period = "ظُهْراً"
                } else if (h > 12) {
                    hh = h - 12
                    if (period.isEmpty()) period = "مَسَاءً"
                } else {
                    hh = h
                }

                val hourWord = if (hh in 1..12) DIAC_ORDINAL_HOURS[hh] else hh.toString()
                val out = "السَّاعَةُ " + hourWord + (if (period.isEmpty()) "" else " $period")
                m2.appendReplacement(sb2, Matcher.quoteReplacement(out))
            }
            m2.appendTail(sb2)
            res = sb2.toString()

            val m3 = PAT_TIME_STANDALONE_ARNORM.matcher(res)
            val sb3 = StringBuffer()
            while (m3.find()) {
                val h = m3.group(1).toInt()
                val periodStr = m3.group(2)
                var period = ""
                if (periodStr != null) {
                    val pLower = periodStr.lowercase(Locale.ROOT)
                    if (pLower.contains("ص") || pLower.contains("am")) {
                        period = "صَبَاحاً"
                    } else if (pLower.contains("م") || pLower.contains("pm")) {
                        period = "مَسَاءً"
                    }
                }
                val hourWord = if (h in 1..12) DIAC_ORDINAL_HOURS[h] else h.toString()
                val out = hourWord + (if (period.isEmpty()) "" else " $period")
                m3.appendReplacement(sb3, Matcher.quoteReplacement(out))
            }
            m3.appendTail(sb3)
            return sb3.toString()
        }

        private fun xCurrency(t: String): String {
            var res = t
            for (cc in COMPILED_CURRENCIES) {
                val m1 = cc.patternPost.matcher(res)
                val sb = StringBuffer()
                while (m1.find()) {
                    m1.appendReplacement(sb, Matcher.quoteReplacement(amountWords(m1.group(1), cc.unit)))
                }
                m1.appendTail(sb)
                res = sb.toString()

                if (cc.patternPre != null) {
                    val m2 = cc.patternPre.matcher(res)
                    val sb2 = StringBuffer()
                    while (m2.find()) {
                        m2.appendReplacement(sb2, Matcher.quoteReplacement(amountWords(m2.group(1), cc.unit)))
                    }
                    m2.appendTail(sb2)
                    res = sb2.toString()
                }
            }
            return res
        }

        private fun xPercent(t: String): String {
            val m = PAT_PERCENT.matcher(t)
            val sb = StringBuffer()
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(cardinalDec(m.group(1)) + " بِالْمِئَةِ"))
            }
            m.appendTail(sb)
            var res = sb.toString()

            val m2 = PAT_PERMILLE.matcher(res)
            val sb2 = StringBuffer()
            while (m2.find()) {
                m2.appendReplacement(sb2, Matcher.quoteReplacement(cardinalDec(m2.group(1)) + " بِالْأَلْفِ"))
            }
            m2.appendTail(sb2)
            return sb2.toString()
        }

        private fun xMath(t: String): String {
            var res = PAT_MATH_PLUS.matcher(t).replaceAll(" زَائِدٌ ")
            res = PAT_MATH_MINUS.matcher(res).replaceAll(" نَاقِصٌ ")
            res = PAT_MATH_MUL.matcher(res).replaceAll(" ضَرْبٌ ")
            res = PAT_MATH_DIV.matcher(res).replaceAll(" قِسْمَةٌ ")
            res = PAT_MATH_EQ.matcher(res).replaceAll(" يُسَاوِي ")
            return res
        }

        private fun xPhone(t: String): String {
            val m = PAT_PHONE.matcher(t)
            val sb = StringBuffer()
            while (m.find()) {
                val s = m.group(0)
                val plus = s.startsWith("+")
                val digits = s.replace(Regex("\\D"), "")
                val out = spellDigits(digits)
                m.appendReplacement(sb, Matcher.quoteReplacement(if (plus) "زَائِدٌ $out" else out))
            }
            m.appendTail(sb)
            return sb.toString()
        }

        private fun xOrdinals(t: String): String {
            val m = PAT_ORDINALS.matcher(t)
            val sb = StringBuffer()
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(ordinalDefinite(m.group(1).toInt(), false)))
            }
            m.appendTail(sb)
            return sb.toString()
        }

        private fun xRoman(t: String): String {
            val m = PAT_ROMAN.matcher(t)
            val sb = StringBuffer()
            while (m.find()) {
                val tok = m.group(0)
                var rep = tok
                for (rv in ROMAN) {
                    if (tok == rv[0]) {
                        rep = ordinalDefinite(rv[1].toInt(), false)
                        break
                    }
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(rep))
            }
            m.appendTail(sb)
            return sb.toString()
        }

        private fun xNumbers(t: String): String {
            var res = PAT_VERSION_CLEAN.matcher(t).replaceAll("$1 $2")
            res = PAT_VERSION_V.matcher(res).replaceAll("الإِصْدَارُ $1")

            val mv = PAT_VERSION_MULTIDOT.matcher(res)
            val sv = StringBuffer()
            while (mv.find()) {
                val raw = mv.group(1)
                val parts = raw.split(".")
                val sbv = StringBuilder()
                for (i in parts.indices) {
                    if (i > 0) sbv.append(" نُقْطَةٌ ")
                    sbv.append(cardinal(parts[i]))
                }
                mv.appendReplacement(sv, Matcher.quoteReplacement(sbv.toString()))
            }
            mv.appendTail(sv)
            res = sv.toString()

            val md = PAT_DECIMAL.matcher(res)
            val s1 = StringBuffer()
            while (md.find()) {
                md.appendReplacement(s1, Matcher.quoteReplacement(cardinalDec(md.group(1), md.group(2))))
            }
            md.appendTail(s1)
            res = s1.toString()

            val mi = PAT_INTEGER.matcher(res)
            val s2 = StringBuffer()
            while (mi.find()) {
                mi.appendReplacement(s2, Matcher.quoteReplacement(cardinal(mi.group(0))))
            }
            mi.appendTail(s2)
            return s2.toString()
        }

        private fun xSymbols(t: String): String {
            var res = t
            for ((key, value) in SYMBOLS) {
                if (res.contains(key)) res = res.replace(key, " $value ")
            }
            return res
        }

        @JvmStatic
        fun normalize(text: String?): String {
            if (text.isNullOrBlank()) return text ?: ""
            return try {
                var t = toAsciiDigits(text)

                val hasDigits = t.any { it in '0'..'9' }
                if (hasDigits) {
                    t = xDates(t)
                    t = xTimes(t) ?: t
                    t = xCurrency(t)
                    t = xPercent(t)
                    t = xMath(t)
                    t = xOrdinals(t)
                    t = xPhone(t)
                    t = xNumbers(t)
                }

                t = xRoman(t)
                t = PAT_MULTI_SPACE.matcher(t).replaceAll(" ").trim()
                if (t.isEmpty()) text else t
            } catch (_: Exception) {
                text
            }
        }
    }
}
