package kr.blogspot.charlie0301.wimple.impl

import android.util.Log
import kr.blogspot.charlie0301.wimple.impl.RestAPIInvoker.HTTPMethod
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl.CommandID
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl.Path
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils
import kr.blogspot.charlie0301.wimple.model.Account
import kr.blogspot.charlie0301.wimple.model.Entry
import org.json.JSONException
import java.util.*

internal class EntryManager(private val wimpl: IWimpleImpl) {

    fun getStoredEntries(): Boolean
    {
        if (null == wimpl.entryDBHandler) {
            return false
        }

        //object : Thread() {
            //override fun run() {
                val cl = Calendar.getInstance()
                cl.add(Calendar.MONTH, -1)
                Log.d(LOG_TAG, "[StoredEntries] Flushing entries before " + cl.time.toString())
                wimpl.entryDBHandler.cleanOldEntries(cl.timeInMillis)

                Log.d(LOG_TAG, "[StoredEntries] Providing Stored Entries from Cache.")
                wimpl.sm(CommandID.CMD_GET_ENTRIES, 1, 0, wimpl.entryDBHandler.allEntrys)
            //}
        //}.start()

        return true
    }

    fun getAllEntries(sectionID: String, latestDate: String, oldestDate: String): Boolean {

        GetAllEntriesTaskThread(sectionID, latestDate, oldestDate, 0).start()
        return true
    }

    fun getAllEntries(sectionID: String, latestDate: String, oldestDate: String, count: Int): Boolean {

        GetAllEntriesTaskThread(sectionID, latestDate, oldestDate, count).start()
        return true
    }


    fun getLatestEntries(sectionID: String, count: Int, noDuplicate: Boolean): Boolean {

        GetLatestEntriesTaskThread(sectionID, count).start()
        return true
    }

    fun makeEntry(sectionID: String, date: Long?, left: Account, right: Account,
                  title: String, amount: Double?, memo: String): Boolean {

        PostEntryTaskThread(sectionID, date, left, right, title, amount, memo).start()
        return true
    }

    fun modifyEntry(sectionID: String, entryID: String, date: String, left: Account, right: Account,
                    title: String, amount: Double?, memo: String): Boolean {

        PutEntryTaskThread(sectionID, entryID, date, left, right, title, amount, memo).start()
        return true
    }

    fun removeEntry(sectionID: String, entryID: String): Boolean {

        DeleteEntryTaskThread(sectionID, entryID).start()
        return true
    }

    private inner class GetAllEntriesTaskThread(val sectionID: String, val latestDate: String, val oldestDate: String, val count: Int) : Thread() {

        override fun run() {

            try {

                val list = ArrayList<Entry>()
                var path = "?section_id=$sectionID&start_date=$oldestDate&end_date=$latestDate"

                path += if (0 > count) {
                    "&limit=$count"
                } else {
                    "&limit=40"
                }

                val json = wimpl.invokeRESTAPI(HTTPMethod.GET, Path.ENTRIES_ALL + path, "")
                if (null == json) {
                    Log.e(LOG_TAG, "[AllEntries] Error response - null returned")
                    wimpl.sm(CommandID.CMD_GET_ENTRIES, 0, 0, list)
                    return
                }

                if (!json.optString("code").startsWith("2")) {
                    Log.e(LOG_TAG, "[AllEntries] Error response - " + json.getString("message"))
                    wimpl.sm(CommandID.CMD_GET_ENTRIES, 0, 0, list)

                    val code = Integer.parseInt(json.getString("code"))
                    wimpl.handleRESTErrorResponse(code)
                    return
                }

                wimpl.setRemainedAPICall(json.getString("rest_of_api"))
                val results = json.getJSONObject("results")


                val iterator = results.keys()
                while (iterator.hasNext()) {
                    val type = iterator.next()

                    if (0 != type.compareTo("rows")) {
                        continue
                    }

                    val rows = results.getJSONArray(type)
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONObject(i)

                        if (0 == row.getString("l_account_id").compareTo("x0", ignoreCase = true) || 0 == row.getString("r_account_id").compareTo("x0", ignoreCase = true)) {
                            // TODO : handle this as removed item.
                            continue
                        }

                        // to hide unnecessary entries
                        if (row.getString("item").startsWith("Adjusted to close")) {
                            continue
                        }

                        val item = Entry(row)
                        val balance = row.getString("total")
                        if (balance.isNotEmpty()) {
                            item.setBalance(balance)
                        }

                        list.add(item)
                    }
                }

                if (list.isEmpty()) {
                    wimpl.sm(CommandID.CMD_GET_ENTRIES, 0, 0, list)
                    return
                }

                Log.d(LOG_TAG, "[AllEntries] Providing All Entries from Server")
                wimpl.entryDBHandler.insert(list)
                wimpl.sm(CommandID.CMD_GET_ENTRIES, 1, 0, list)

            } catch (e: JSONException) {
                Log.e(LOG_TAG, "[AllEntries] Error response - null returned")
                wimpl.sm(CommandID.CMD_GET_ENTRIES, 0, 0, ArrayList<Entry>())

            } finally {
                wimpl.getApiAvailableSemaphore("getAllEntries").release()
            }
        }
    }

    private inner class GetLatestEntriesTaskThread(val sectionID: String, val count: Int) : Thread() {

        override fun run() {

            val list = ArrayList<Entry>()
            var path = "?section_id=$sectionID"

            if (0 > count) {
                path += "&limit=$count"
            }

            try {
                val json = wimpl.invokeRESTAPI(HTTPMethod.GET, Path.ENTRIES_LATEST + path, "")
                if (null == json) {
                    Log.e(LOG_TAG, "[LatestEntries] Error response - null returned")
                    wimpl.sm(CommandID.CMD_GET_LATEST_ENTRIES, 0, 0, list)
                    return
                }

                if (!json.optString("code").startsWith("2")) {
                    Log.e(LOG_TAG, "[LatestEntries] Error response - " + json.getString("message"))
                    wimpl.sm(CommandID.CMD_GET_LATEST_ENTRIES, 0, 0, list)

                    val code = Integer.parseInt(json.getString("code"))
                    wimpl.handleRESTErrorResponse(code)
                    return
                }

                wimpl.setRemainedAPICall(json.getString("rest_of_api"))
                val results = json.getJSONArray("results")
                for (i in 0 until results.length()) {
                    val row = results.getJSONObject(i)

                    if (0 == row.getString("l_account_id").compareTo("x0", ignoreCase = true) ||
                            0 == row.getString("r_account_id").compareTo("x0", ignoreCase = true)) {
                        // omit removed item.
                        continue
                    }

                    // to hide unnecessary entries
                    if (row.getString("item").startsWith("Adjusted to close")) {
                        continue
                    }

                    val item = Entry(row)
                    val balance = row.getString("total")
                    if (balance.isNotEmpty()) {
                        item.setBalance(balance)
                    }

                    list.add(item)
                }
                Log.d(LOG_TAG, "[LatestEntries] Providing Latest Entries from Server")
                wimpl.sm(CommandID.CMD_GET_LATEST_ENTRIES, 1, 0, list)

            } catch (e: JSONException) {
                Log.e(LOG_TAG, "[LatestEntries] Error response - null returned")
                wimpl.sm(CommandID.CMD_GET_LATEST_ENTRIES, 0, 0, ArrayList<Entry>())
            }

        }

    }

    private inner class PostEntryTaskThread(val sectionID: String, val date: Long?, val left: Account, val right: Account,
                                            val title: String, val amount: Double?, val memo: String) : Thread() {

        override fun run() {

            val formatEntryPost = "[{" +
                    "\"entry_date\" : %s," +
                    "\"l_account\" : \"%s\"," +
                    "\"l_account_id\" : \"%s\"," +
                    "\"r_account\" : \"%s\"," +
                    "\"r_account_id\" : \"%s\"," +
                    "\"item\" : \"%s\"," +
                    "\"money\" : %.4f," +
                    "\"memo\" : \"%s\"" +
                    "}]"
            val pushingContent = String.format(DateFormatUtils.getDefaultLocale(),
                    formatEntryPost,
                    DateFormatUtils.getServerDateFormat().format(Date(date!!)),
                    left.what,
                    left.id,
                    right.what,
                    right.id,
                    title,
                    amount,
                    memo
            )

            val path = "section_id=$sectionID&data_type=json&entries=$pushingContent"

            //Log.d(LOG_TAG, path);

            val json = wimpl.invokeRESTAPI(HTTPMethod.POST, Path.ENTRIES_LATEST, path)
            if (null == json) {
                Log.e(LOG_TAG, "[PostEntry] Error response - null returned")
                wimpl.sm(CommandID.CMD_POST_ENTRY, 0, 0, "")
                return
            }

            try {
                if (!json.optString("code").startsWith("2")) {
                    Log.e(LOG_TAG, "[PostEntry] Error response - " + json.getString("message"))
                    wimpl.sm(CommandID.CMD_POST_ENTRY, 0, 0, "")
                    return
                }

                wimpl.setRemainedAPICall(json.getString("rest_of_api"))
                val results = json.getJSONArray("results")
                val row = results.getJSONObject(0)

                Log.d(LOG_TAG, "[PostEntry] Providing response")
                wimpl.sm(CommandID.CMD_POST_ENTRY, 1, 0, row.getString("entry_date"))

            } catch (e: JSONException) {
                Log.e(LOG_TAG, "[PostEntry] Error response - null returned")
                wimpl.sm(CommandID.CMD_POST_ENTRY, 0, 0, "")
            }

        }
    }

    private inner class PutEntryTaskThread(val sectionID: String, val entryID: String, val date: String, val left: Account, val right: Account,
                                           val title: String, val amount: Double?, val memo: String) : Thread() {

        override fun run() {

            val formatEntryPut = "" +
                    "&entry_date=%s" +
                    "&l_account=%s" +
                    "&l_account_id=%s" +
                    "&r_account=%s" +
                    "&r_account_id=%s" +
                    "&item=%s" +
                    "&money=%.4f" +
                    ""
            val pushingContent = String.format(DateFormatUtils.getDefaultLocale(),
                    formatEntryPut,
                    date,
                    left.what,
                    left.id,
                    right.what,
                    right.id,
                    title,
                    amount
            )

            var path = "section_id=$sectionID&data_type=json$pushingContent"
            if (memo.isNotEmpty()) {
                path += "&memo=$memo"
            }

            //Log.d(LOG_TAG, path);

            val json = wimpl.invokeRESTAPI(HTTPMethod.PUT, Path.ENTRIES_MODIFY + entryID + ".json_array", path)
            if (null == json) {
                Log.e(LOG_TAG, "[PutEntry] Error response - null returned")
                wimpl.sm(CommandID.CMD_PUT_ENTRY, 0, 0, Entry())
                return
            }

            try {
                if (!json.optString("code").startsWith("2")) {
                    Log.e(LOG_TAG, "[PutEntry] Error response -  - " + json.getString("message"))
                    wimpl.sm(CommandID.CMD_PUT_ENTRY, 0, 0, Entry())
                    return
                }

                wimpl.setRemainedAPICall(json.getString("rest_of_api"))
                val row = json.getJSONObject("results")

                val item = Entry(row)

                Log.d(LOG_TAG, "[PutEntry] Providing response")
                wimpl.sm(CommandID.CMD_PUT_ENTRY, 1, 0, item)

            } catch (e: JSONException) {
                Log.e(LOG_TAG, "[PutEntry] Error response - null returned")
                wimpl.sm(CommandID.CMD_PUT_ENTRY, 0, 0, Entry())
            }

        }
    }

    private inner class DeleteEntryTaskThread constructor(val sectionID: String, val entryID: String) : Thread() {

        override fun run() {

            val json = wimpl.invokeRESTAPI(HTTPMethod.DELETE, Path.ENTRIES_REMOVE + entryID + ".json_array", "")
            if (null == json) {
                Log.e(LOG_TAG, "[DeleteEntry] Error response - null returned")
                wimpl.sm(CommandID.CMD_DELETE_ENTRY, 0, 0, "")
                return
            }

            try {
                if (!json.optString("code").startsWith("2")) {
                    Log.e(LOG_TAG, "[DeleteEntry] Error response -  - " + json.get("message").toString())
                    wimpl.sm(CommandID.CMD_DELETE_ENTRY, 0, 0, "")
                    return
                }

                Log.d(LOG_TAG, "[DeleteEntry] Providing response")
                wimpl.entryDBHandler.remove(entryID)
                wimpl.sm(CommandID.CMD_DELETE_ENTRY, 1, 0, entryID)

            } catch (e: JSONException) {
                Log.e(LOG_TAG, "[DeleteEntry] Error response - null returned")
                wimpl.sm(CommandID.CMD_DELETE_ENTRY, 0, 0, "")
            }

        }
    }

    companion object {

        private const val LOG_TAG = "EntryManager"
    }
}
