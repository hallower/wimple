package kr.blogspot.charlie0301.wimple.impl.util

class KoreanWordSearch {

    companion object {
        private const val HANGUL_BEGIN_UNICODE = 44032      // 가
        private const val HANGUL_LAST_UNICODE = 55203       // 힣
        private const val HANGUL_BASE_UNIT = 588            // 각자음 마다 가지는 글자수
        private val INITIAL_SOUND = charArrayOf('ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ')


        private fun isInitialSound(searchChar: Char): Boolean {
            for (c in INITIAL_SOUND)
                if (c == searchChar)
                    return true
            return false
        }

        private fun getInitialSound(c: Char): Char {
            return INITIAL_SOUND[(c - HANGUL_BEGIN_UNICODE).toInt() / HANGUL_BASE_UNIT]
        }

        private fun isHangul(c: Char): Boolean {
            return c.toInt() in HANGUL_BEGIN_UNICODE..HANGUL_LAST_UNICODE
        }

        fun matchString(value: String, search: String): Boolean {
            val searchLen = search.length
            val lenDiff = value.length - searchLen
            if (lenDiff < 0) return false
            for (i in 0..lenDiff) {
                var t = 0
                while (t < searchLen) {
                    if (isInitialSound(search[t]) && isHangul(value[i + t])) {
                        if (getInitialSound(value[i + t]) == search[t])
                            t++ else break
                    } else { // value is not Hangul initial sound
                        if (value[i + t] == search[t])
                            t++ else break
                    }
                }
                if (t == searchLen) return true
            }
            return false
        }
    }



}