package com.pbt.nabratts
object TextPreprocessor {
    private val ONES_AR = arrayOf(
        "", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة",
        "ستة", "سبعة", "ثمانية", "تسعة", "عشرة",
        "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر",
        "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر",
    )
    private val TENS_AR = arrayOf(
        "", "", "عشرون", "ثلاثون", "أربعون", "خمسون",
        "ستون", "سبعون", "ثمانون", "تسعون",
    )
    private val HUNDREDS_AR = arrayOf(
        "", "مئة", "مئتان", "ثلاثمئة", "أربعمئة", "خمسمئة",
        "ستمئة", "سبعمئة", "ثمانمئة", "تسعمئة",
    )
    private val SCALE_AR = arrayOf("", "ألف", "مليون", "مليار", "تريليون")
    private val EASTERN_TO_WESTERN = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
    )
    private fun intToArabic(n: Long): String {
        if (n == 0L) return "صفر"
        if (n < 0L) return "سالب " + intToArabic(-n)
        val parts = mutableListOf<String>()
        var remaining = n
        var scale = 0
        while (remaining > 0) {
            val chunk = (remaining % 1000).toInt()
            if (chunk != 0) {
                var chunkWords = chunkToArabic(chunk)
                if (scale in 1 until SCALE_AR.size) {
                    chunkWords = "$chunkWords ${SCALE_AR[scale]}"
                }
                parts.add(chunkWords)
            }
            remaining /= 1000
            scale++
        }
        return parts.reversed().joinToString(" و")
    }
    private fun chunkToArabic(n: Int): String {
        val hundreds = n / 100
        val remainder = n % 100
        val tens = remainder / 10
        val ones = remainder % 10
        val resultParts = mutableListOf<String>()
        if (hundreds > 0) {
            resultParts.add(HUNDREDS_AR[hundreds])
        }
        if (remainder < 20) {
            if (remainder > 0) {
                resultParts.add(ONES_AR[remainder])
            }
        } else {
            if (ones > 0) {
                resultParts.add(ONES_AR[ones] + " و" + TENS_AR[tens])
            } else {
                resultParts.add(TENS_AR[tens])
            }
        }
        return resultParts.joinToString(" و")
    }
    private fun convertNumber(matchValue: String): String {
        val text = matchValue.map { EASTERN_TO_WESTERN[it] ?: it }.joinToString("")
        if ('.' in text || ',' in text) {
            val normalized = text.replace(',', '.')
            val parts = normalized.split('.')
            return try {
                val integerPart = if (parts[0].isNotEmpty()) intToArabic(parts[0].toLong()) else "صفر"
                val decimalPart = if (parts.size > 1 && parts[1].isNotEmpty()) {
                    " فاصلة " + parts[1].filter { it.isDigit() }
                        .map { intToArabic(it.toString().toLong()) }
                        .joinToString(" ")
                } else ""
                integerPart + decimalPart
            } catch (e: NumberFormatException) {
                text
            }
        }
        return try {
            intToArabic(text.toLong())
        } catch (e: NumberFormatException) {
            text
        }
    }
    private val SYMBOL_MAP = mapOf(
        '+' to " زائد ",
        '-' to " ناقص ",
        '*' to " ضرب ",
        '/' to " تقسيم ",
        '=' to " يساوي ",
        '>' to " أكبر من ",
        '<' to " أصغر من ",
        '%' to " بالمئة ",
        '$' to " دولار ",
        '€' to " يورو ",
        '£' to " جنيه استرليني ",
        '&' to " و ",
        '@' to " آت ",
        ':' to " نقطتان ",
        '(' to " قوس ",
        ')' to " قوس ",
        '[' to " قوس ",
        ']' to " قوس ",
    )
    private val LATIN_CHAR_MAP = mapOf(
        'a' to "ا", 'b' to "ب", 'c' to "ك", 'd' to "د", 'e' to "ي",
        'f' to "ف", 'g' to "ج", 'h' to "ه", 'i' to "ي", 'j' to "ج",
        'k' to "ك", 'l' to "ل", 'm' to "م", 'n' to "ن", 'o' to "و",
        'p' to "ب", 'q' to "ق", 'r' to "ر", 's' to "س", 't' to "ت",
        'u' to "و", 'v' to "ف", 'w' to "و", 'x' to "كس", 'y' to "ي",
        'z' to "ز",
    )
    private val LATIN_COMBOS = listOf(
        "ch" to "تش",
        "sh" to "ش",
        "th" to "ث",
        "ph" to "ف",
        "kh" to "خ",
        "gh" to "غ",
    )
    private val KNOWN_NAMES: Map<String, String> = mapOf(
        "telegram" to "تيليجرام",
        "whatsapp" to "واتساب",
        "youtube" to "يوتيوب",
        "instagram" to "إنستجرام",
        "facebook" to "فيسبوك",
        "twitter" to "تويتر",
        "x" to "إكس",
        "tiktok" to "تيك توك",
        "snapchat" to "سناب شات",
        "google" to "جوجل",
        "microsoft" to "مايكروسوفت",
        "windows" to "ويندوز",
        "android" to "أندرويد",
        "iphone" to "آيفون",
        "apple" to "أبل",
        "amazon" to "أمازون",
        "netflix" to "نتفليكس",
        "zoom" to "زوم",
        "skype" to "سكايب",
        "discord" to "ديسكورد",
        "github" to "جيت هاب",
        "gmail" to "جيميل",
        "chrome" to "كروم",
        "firefox" to "فايرفوكس",
        "edge" to "إيدج",
        "safari" to "سفاري",
        "excel" to "إكسل",
        "word" to "ورد",
        "outlook" to "آوتلوك",
        "teams" to "تيمز",
        "office" to "أوفيس",
        "linux" to "لينوكس",
        "python" to "بايثون",
        "java" to "جافا",
        "nvda" to "إن في دي إيه",
        "ok" to "أوكي",
        "ai" to "إيه آي",
        "tv" to "تي في",
        "pc" to "بي سي",
        "usb" to "يو إس بي",
        "wifi" to "وايفاي",
        "sms" to "إس إم إس",
        "gemini" to "جيمناي",
        "chatgpt" to "تشات جي بي تي",
        "openai" to "أوبن إيه آي",
        "deepmind" to "ديب مايند",
        "antigravity" to "أنتي جرافتي",
    )
    private val ARABIC_LETTERS_MAP = mapOf(
        "ا" to "أَلِفٌ",
        "أ" to "أَلِفٌ",
        "إ" to "أَلِفٌ",
        "آ" to "أَلِفٌ مَمْدُودَةٌ",
        "ب" to "بَاءٌ",
        "ت" to "تَاءٌ",
        "ث" to "ثَاءٌ",
        "ج" to "جِيمٌ",
        "ح" to "حَاءٌ",
        "خ" to "خَاءٌ",
        "د" to "دَالٌ",
        "ذ" to "ذَالٌ",
        "ر" to "رَاءٌ",
        "ز" to "زَايٌ",
        "س" to "سِينٌ",
        "ش" to "شِينٌ",
        "ص" to "صَادٌ",
        "ض" to "ضَادٌ",
        "ط" to "طَاءٌ",
        "ظ" to "ظَاءٌ",
        "ع" to "عَيْنٌ",
        "غ" to "غَيْنٌ",
        "ف" to "فَاءٌ",
        "ق" to "قَافٌ",
        "ك" to "كَافٌ",
        "ل" to "لَامٌ",
        "م" to "مِيمٌ",
        "ن" to "نُونٌ",
        "ه" to "هَاءٌ",
        "ة" to "تَاءٌ مَرْبُوطَةٌ",
        "و" to "وَاوٌ",
        "ي" to "يَاءٌ",
        "ى" to "أَلِفٌ مَقْصُورَةٌ",
        "ئ" to "هَمْزَةٌ",
        "ؤ" to "هَمْزَةٌ",
        "ء" to "هَمْزَةٌ"
    )
    private fun transliterateLatinWord(word: String): String {
        val lower = word.lowercase()
        KNOWN_NAMES[lower]?.let { return it }
        var res = lower
        for ((eng, ar) in LATIN_COMBOS) {
            res = res.replace(eng, ar)
        }
        val final_ = StringBuilder()
        for (ch in res) {
            if (ch in 'a'..'z') {
                final_.append(LATIN_CHAR_MAP[ch] ?: ch)
            } else {
                final_.append(ch)
            }
        }
        return final_.toString()
    }
    private val URL_RE = Regex("""https?://\S+|www\.\S+""", RegexOption.IGNORE_CASE)
    private val EMAIL_RE = Regex("""\b[\w.+\-]+@[\w\-]+\.[a-z]{2,}\b""", RegexOption.IGNORE_CASE)
    private val EMOJI_RE = Regex(
        "[\u2600-\u27BF" +
        "\uD83C\uDF00-\uD83D\uDE4F" +
        "\uD83D\uDE80-\uD83D\uDEFF" +
        "\u200d\u200c\uFEFF" +
        "\u2066-\u2069]+"
    )
    private val HASHTAG_RE = Regex("""#(\w+)""")
    private val MENTION_RE = Regex("""@([\w\u0600-\u06FF]+)""")
    private val LATIN_WORD_RE = Regex("""\b[A-Za-z][A-Za-z0-9]*(?:[.'\-][A-Za-z0-9]+)*\b""")
    private val NUMBER_RE = Regex("""[٠١٢٣٤٥٦٧٨٩\d]+(?:[.,][٠١٢٣٤٥٦٧٨٩\d]+)*""")
    private val SYMBOL_CHARS = SYMBOL_MAP.keys.joinToString("") { Regex.escape(it.toString()) }
    private val SYMBOL_RE = Regex("[$SYMBOL_CHARS]")
    private val NOISE_RE = Regex("""[-_=*~`|\\<>{}]{2,}""")
    private val MULTI_SPACE_RE = Regex("""\s{2,}""")
    private val SINGLE_ARABIC_LETTER_RE = Regex("""(?<![\u0621-\u0652])([\u0621-\u064A])(?![\u0621-\u0652])""")
    fun preprocessText(text: String, userDictionary: UserDictionary? = null): String {
        if (text.isEmpty()) return ""
        var result = text
        if (userDictionary != null) {
            result = userDictionary.applyReplacements(result)
        }
        result = result.replace("\t", " ").replace("\r", " ")
        result = NOISE_RE.replace(result, " ")
        result = URL_RE.replace(result, " ")
        result = EMAIL_RE.replace(result, " ")
        result = EMOJI_RE.replace(result, " ")
        result = HASHTAG_RE.replace(result, "$1")
        result = MENTION_RE.replace(result, "$1")
        result = NUMBER_RE.replace(result) { convertNumber(it.value) }
        result = SYMBOL_RE.replace(result) { m ->
            SYMBOL_MAP[m.value[0]] ?: m.value
        }
        result = LATIN_WORD_RE.replace(result) { transliterateLatinWord(it.value) }
        result = SINGLE_ARABIC_LETTER_RE.replace(result) { m ->
            ARABIC_LETTERS_MAP[m.value] ?: m.value
        }
        result = MULTI_SPACE_RE.replace(result, " ")
        return result.trim()
    }
}
