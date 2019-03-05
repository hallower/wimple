package kr.blogspot.charlie0301.wimple.impl

import android.util.Log
import kr.blogspot.charlie0301.wimple.impl.RestAPIInvoker.HTTPMethod
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl.CommandID
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl.Path
import kr.blogspot.charlie0301.wimple.model.Item
import org.json.JSONArray
import org.json.JSONException
import java.util.*

internal class ItemManager(private val wimpl: IWimpleImpl) {


    fun getFrequentItems(sectionID: String): Boolean {

        GetFrequentItemsTaskThread(sectionID).start()
        return true
    }

    private inner class GetFrequentItemsTaskThread internal constructor(internal val sectionID: String) : Thread() {

        override fun run() {

            val list = ArrayList<Item>()
            val path = "?section_id=$sectionID"

            val json = wimpl.invokeRESTAPI(HTTPMethod.GET, Path.ITEM_FREQUENT + path, "")
            if (null == json) {
                Log.e(LOG_TAG, "[Frequent Item] Error response - null returned")
                wimpl.sm(CommandID.CMD_GET_FRQUENT_ITEMS, 0, 0, list)
                return
            }

            try {
                if (!json.get("code").toString().startsWith("2")) {
                    Log.e(LOG_TAG, "[Frequent Item] Error response - " + json.get("message").toString())
                    wimpl.sm(CommandID.CMD_GET_FRQUENT_ITEMS, 0, 0, list)
                    return
                }

                wimpl.setRemainedAPICall(json.getString("rest_of_api"))
                val results = json.getJSONObject("results")

                val iterator = results.keys()
                while (iterator.hasNext()) {
                    val type = iterator.next()
                    val rows = results.getJSONArray(type)
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONObject(i)

                        list.add(Item(row))
                    }
                }
                Log.d(LOG_TAG, "[Frequent Item] Providing Frequent Items")
                wimpl.sm(CommandID.CMD_GET_FRQUENT_ITEMS, 1, 0, list)
            } catch (e: JSONException) {
                Log.e(LOG_TAG, "[Frequent Item] Error response - null returned")
                wimpl.sm(CommandID.CMD_GET_FRQUENT_ITEMS, 0, 0, list)
            }

        }

    }


    fun getLatestItems(sectionID: String, forceUpdate: Boolean): Boolean {

        GetLatestItemsTaskThread(sectionID, forceUpdate).start()
        return true
    }

    private inner class GetLatestItemsTaskThread internal constructor(internal val sectionID: String?, internal val forceUpdate: Boolean) : Thread() {

        override fun run() {

            if (!forceUpdate &&
                    null != wimpl.latestItemDBHandler &&
                    wimpl.latestItemDBHandler.hasData()) {
                Log.d(LOG_TAG, "[Latest Item] Providing GetLatestItems from Cache!!!")
                wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 1, 0, wimpl.latestItemDBHandler.allItems)
                return
            }

            val list = ArrayList<Item>()
            if (null == sectionID || sectionID.isEmpty()) {
                Log.e(LOG_TAG, "[Latest Item] Failed - invalid sectionID !!!")
                wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 0, 0, list)
                return
            }

            try {
                val path = "?section_id=$sectionID"

                val json = wimpl.invokeRESTAPI(HTTPMethod.GET, Path.ITEM_LATEST + path, "")
                if (null == json) {
                    Log.e(LOG_TAG, "[Latest Item] Error response - null returned")
                    wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 0, 0, list)
                    return
                }

                if (!json.optString("code").startsWith("2")) {
                    Log.d(LOG_TAG, "[Latest Item] Failed - GetLatestItems from Server!!!" + json.get("message").toString())
                    wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 0, 0, list)
                    return
                }

                wimpl.setRemainedAPICall(json.get("rest_of_api").toString())
                val results = json.get("results") as JSONArray
                for (i in 0 until results.length()) {
                    val row = results.getJSONObject(i)

                    // to hide unnecessary entries
                    if (row.optString("item").startsWith("Adjusted to close")) {
                        continue
                    }

                    list.add(Item(row))
                }
                wimpl.latestItemDBHandler.insert(list)
                Log.d(LOG_TAG, "[Latest Item] Providing GetLatestItems from Server!!!")
                wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 1, 0, list)
            } catch (e: JSONException) {
                Log.e(LOG_TAG, "[Latest Item] Failed - invalid sectionID !!!")
                wimpl.sm(CommandID.CMD_GET_LATEST_ITEMS, 0, 0, list)
            }

        }

    }


    fun getMonthlyItems(sectionID: String, forceUpdate: Boolean): Boolean {

        GetMonthlyItemsTaskThread(sectionID, forceUpdate).start()
        return true
    }

    private inner class GetMonthlyItemsTaskThread internal constructor(internal val sectionID: String, internal val forceUpdate: Boolean) : Thread() {

        override fun run() {

            if (!forceUpdate &&
                    null != wimpl.monthlyItemDBHandler &&
                    wimpl.monthlyItemDBHandler.hasData()) {
                Log.d(LOG_TAG, "[Monthly Item] Providing GetMonthlyItem from Cache!!!")
                wimpl.sm(CommandID.CMD_GET_MONTHLY_ITEMS, 1, 0, wimpl.monthlyItemDBHandler.allItems)
                return
            }

            try {
                val list = ArrayList<Item>()
                val path = "?section_id=$sectionID"

                val json = wimpl.invokeRESTAPI(HTTPMethod.GET, Path.ITEM_MONTHLY + path, "")
                if (null == json) {
                    Log.e(LOG_TAG, "[Monthly Item] Error response - null returned")
                    wimpl.sm(CommandID.CMD_GET_MONTHLY_ITEMS, 0, 0, list)
                    return
                }

                if (!json.optString("code").startsWith("2")) {
                    Log.e(LOG_TAG, "[Monthly Item] Error response - " + json.get("message").toString())
                    wimpl.sm(CommandID.CMD_GET_MONTHLY_ITEMS, 0, 0, list)
                    return
                }

                wimpl.setRemainedAPICall(json.optString("rest_of_api"))
                val results = json.getJSONObject("results")

                val iterator = results.keys()
                while (iterator.hasNext()) {
                    val type = iterator.next()

                    if (!type.startsWith("slot")) {
                        continue
                    }

                    val rows = results.getJSONArray(type)
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONObject(i)

                        list.add(Item(row))
                    }
                }
                wimpl.monthlyItemDBHandler.clean()
                wimpl.monthlyItemDBHandler.insert(list)
                Log.d(LOG_TAG, "[Monthly Item] Providing Monthly Items")
                wimpl.sm(CommandID.CMD_GET_MONTHLY_ITEMS, 1, 0, list)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "[Monthly Item] Error response - null returned")
                wimpl.sm(CommandID.CMD_GET_MONTHLY_ITEMS, 0, 0, ArrayList<Item>())
            }

        }
    }


    fun removeMonthlyItem(sectionID: String, itemID: String): Boolean {

        DeleteMonthlyItemTaskThread(sectionID, itemID).start()
        return true
    }

    private inner class DeleteMonthlyItemTaskThread internal constructor(internal val sectionID: String, internal val itemID: String) : Thread() {

        override fun run() {

            val json = wimpl.invokeRESTAPI(HTTPMethod.DELETE, Path.ITEM_MONTHLY_REMOVE + itemID + "/" + sectionID + ".json_array", "")
            if (null == json) {
                Log.e(LOG_TAG, "[DeleteMonthItem] Error response - null returned")
                wimpl.sm(CommandID.CMD_DELETE_MONTHLY_ITEMS, 0, 0, "")
                return
            }

            try {
                if (!json.optString("code").startsWith("2")) {
                    Log.e(LOG_TAG, "[DeleteMonthItem] Error response -  - " + json.get("message").toString())
                    wimpl.sm(CommandID.CMD_DELETE_MONTHLY_ITEMS, 0, 0, "")
                    return
                }

                Log.d(LOG_TAG, "[DeleteMonthItem] Providing response")
                wimpl.monthlyItemDBHandler.remove(itemID)
                wimpl.sm(CommandID.CMD_DELETE_MONTHLY_ITEMS, 1, 0, itemID)
            } catch (e: JSONException) {
                Log.e(LOG_TAG, "[DeleteMonthItem] Error response - null returned")
                wimpl.sm(CommandID.CMD_DELETE_MONTHLY_ITEMS, 0, 0, "")
            }

        }
    }

    companion object {

        private const val LOG_TAG = "ItemManager"
    }

}
