package kr.blogspot.charlie0301.wimple.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages the opt-in dialog for emailing AI classification diagnostic logs.
 *
 * Trigger conditions (any one is sufficient):
 *   (a) AI log has ≥ 90 entries (full buffer filled for the first time)
 *   (b) ≥ 10 UNPARSED/ERROR results in the most recent 30 log entries
 *   (c) ≥ 10 review-row dismissals within the last 30 days
 *
 * Rate-limiting: monthly cap — dialog is not shown within 30 days of the last display.
 * The user can permanently opt out with "다시 묻지 않기".
 *
 * Privacy:
 *   - Logs are sent only on explicit user consent.
 *   - The consent statement is embedded in the email body.
 *   - The email body states that logs will be deleted within 30 days of receipt.
 */
object AiLogConsentManager {

    private const val KEY_NEVER_ASK    = "pref_aiLogConsent_neverAsk"
    private const val KEY_LAST_SHOWN   = "pref_aiLogConsent_lastShownMs"
    private const val KEY_DISMISSALS   = "pref_aiLogConsent_dismissals"

    private const val MONTHLY_CAP_MS         = 30L * 24 * 3600 * 1000
    private const val DISMISSAL_WINDOW_MS    = 30L * 24 * 3600 * 1000
    private const val FAILURE_THRESHOLD      = 10
    private const val DISMISSAL_THRESHOLD    = 10
    private const val RECENT_WINDOW_SIZE     = 30
    private const val FULL_LOG_THRESHOLD     = 90

    // ---- Public API ---------------------------------------------------------

    /** Record a review-row dismissal. Call from btn_dismiss handler. */
    fun recordDismissal(ctx: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val now = System.currentTimeMillis()
        val cutoff = now - DISMISSAL_WINDOW_MS
        val raw = prefs.getString(KEY_DISMISSALS, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }

        // Prune entries older than 30 days and append the new one
        val pruned = JSONArray()
        for (i in 0 until arr.length()) {
            val ts = arr.optLong(i, 0L)
            if (ts >= cutoff) pruned.put(ts)
        }
        pruned.put(now)
        prefs.edit().putString(KEY_DISMISSALS, pruned.toString()).apply()
    }

    /** Returns true if the consent dialog should be shown right now. */
    fun shouldPrompt(ctx: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)

        if (prefs.getBoolean(KEY_NEVER_ASK, false)) return false

        val now = System.currentTimeMillis()
        val lastShown = prefs.getLong(KEY_LAST_SHOWN, 0L)
        if (now - lastShown < MONTHLY_CAP_MS) return false

        val entries = AiClassificationLog.getAll(ctx)

        // (a) Full log buffer
        if (entries.size >= FULL_LOG_THRESHOLD) return true

        // (b) Too many failures in the recent window
        val failCount = entries.takeLast(RECENT_WINDOW_SIZE).count { isFailure(it) }
        if (failCount >= FAILURE_THRESHOLD) return true

        // (c) Too many dismissals in the last 30 days
        val cutoff = now - DISMISSAL_WINDOW_MS
        val raw = prefs.getString(KEY_DISMISSALS, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val dismissCount = (0 until arr.length()).count { arr.optLong(it, 0L) >= cutoff }
        if (dismissCount >= DISMISSAL_THRESHOLD) return true

        return false
    }

    /** Record that the dialog was displayed (updates the monthly cap timestamp). */
    fun markShown(ctx: Context) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .edit().putLong(KEY_LAST_SHOWN, System.currentTimeMillis()).apply()
    }

    /** Permanently suppress future prompts. */
    fun neverAsk(ctx: Context) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .edit().putBoolean(KEY_NEVER_ASK, true).apply()
    }

    /**
     * Build a mailto: Intent that carries the AI log JSON and the embedded consent text.
     * The email body is plain text to keep it readable in any client.
     */
    fun buildEmailIntent(ctx: Context): Intent {
        val logJson = AiClassificationLog.exportJson(ctx)
        val body = buildEmailBody(logJson)
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(DEVELOPER_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, EMAIL_SUBJECT)
            putExtra(Intent.EXTRA_TEXT, body)
        }
    }

    // ---- Internals ----------------------------------------------------------

    private fun isFailure(entry: AiClassificationLog.Entry): Boolean {
        val json = entry.resultJson ?: return true    // no result = failure
        return try {
            val state = JSONObject(json).optString("state", "UNPARSED")
            state == "UNPARSED" || state == "ERROR"
        } catch (_: Exception) {
            true
        }
    }

    private fun buildEmailBody(logJson: String): String = """
[사용자 동의 및 개인정보 처리 안내]

본 이메일에는 Wimple 앱의 AI 자동 분류 진단 로그가 포함되어 있습니다.

■ 수집 목적: AI 분류 정확도 개선
■ 수집 항목: 은행 앱 알림 제목·본문(텍스트), AI 처리 단계별 로그
■ 보유 기간: 수신일로부터 30일 이내 삭제
■ 제공 대상: 앱 개발자(개인) 외 제3자에게 제공하지 않음

위 내용을 확인하고 사용자가 직접 이 이메일을 전송하는 것으로 동의에 갈음합니다.

─────────────────────────────────────────
[AI 분류 로그 JSON]
─────────────────────────────────────────
$logJson
""".trimIndent()

    private const val DEVELOPER_EMAIL = "hallower@gmail.com"
    private const val EMAIL_SUBJECT   = "[Wimple] AI 분류 진단 로그"
}
