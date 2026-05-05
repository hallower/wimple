package kr.blogspot.charlie0301.wimple.impl

import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object BankNotificationPayloadBuilder {

    data class PayloadNotification(
        val time: Long,
        val label: String,
        val title: String,
        val text: String
    )

    private val dateInMessageRegex = Regex("""\d{1,2}/\d{1,2}""")

    fun buildFromArray(arr: JSONArray): String {
        val notifications = ArrayList<PayloadNotification>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            notifications.add(PayloadNotification(
                time = o.optLong("t"),
                label = o.optString("label"),
                title = o.optString("title"),
                text = o.optString("text")
            ))
        }
        return build(notifications)
    }

    fun build(notifications: List<PayloadNotification>): String {
        val sb = StringBuilder()
        val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        for (notification in notifications) {
            val label = notification.label.trim()
            val rawTitle = notification.title.trim()
            val title = if (label.isNotEmpty() && rawTitle.equals(label, ignoreCase = true)) {
                ""
            } else {
                rawTitle
            }
            val text = notification.text
                .replace(Regex("""[\r\n]+"""), " ")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()

            val body = when {
                title.isNotEmpty() && text.isNotEmpty() -> "$title $text"
                title.isNotEmpty() -> title
                else -> text
            }
            if (body.isEmpty()) continue

            if (label.isNotEmpty()) sb.append(label).append(' ')
            sb.append(body)
            if (!dateInMessageRegex.containsMatchIn(body)) {
                sb.append(' ').append(fmt.format(Date(notification.time)))
            }
            sb.append('\n')
        }
        return sb.toString()
    }
}
