package com.pbt.nabratts

/**
 * Unified Arabic text preprocessing router connecting to ArabicTextNormalizer and DictionaryManager.
 */
object TextPreprocessor {
    @JvmStatic
    fun preprocessText(
        text: String,
        userDictionary: UserDictionary? = null,
        readEmojis: Boolean = true
    ): String {
        if (text.isBlank()) return ""

        // 1. Emoji expansion if enabled and loaded
        val emojiProcessed = if (readEmojis && DictionaryManager.isEmojiLoaded()) {
            DictionaryManager.replaceEmojis(text)
        } else {
            text
        }

        // 2. Custom User Dictionary replacement
        val userProcessed = userDictionary?.applyReplacements(emojiProcessed) ?: emojiProcessed

        // 3. Default Dictionary speech & abbreviation rules
        val defaultProcessed = DictionaryManager.applyDefaultRules(userProcessed)

        // 4. Comprehensive Arabic Normalization (Dates, Times, Currencies, Numbers, Math, Latin)
        val normalized = ArabicTextNormalizer.normalize(defaultProcessed, false)

        return normalized.trim()
    }

    @JvmStatic
    fun preprocessText(text: String, userDictionary: UserDictionary?): String {
        return preprocessText(text, userDictionary, true)
    }
}
