package kr.blogspot.charlie0301.wimple

import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.os.Message
import android.preference.PreferenceManager
import android.util.Log
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.view.View.OnTouchListener
import android.widget.AbsListView
import android.widget.AbsListView.OnScrollListener
import android.widget.AdapterView.AdapterContextMenuInfo
import android.widget.FrameLayout
import android.widget.Toast
import kotlinx.android.synthetic.main.fragment_transaction_list_tab.*
import kr.blogspot.charlie0301.wimple.WimpleActivity.Companion.CommandID
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils
import kr.blogspot.charlie0301.wimple.model.Entry
import kr.blogspot.charlie0301.wimple.model.Item
import kr.blogspot.charlie0301.wimple.model.Item.DateAscCompare
import kr.blogspot.charlie0301.wimple.widget.ItemListView
import kr.blogspot.charlie0301.wimple.widget.entry.EntryItemListAdapter
import java.util.*
import java.util.concurrent.Semaphore

class TransactionListFragment : androidx.fragment.app.Fragment(), IWimpleFragment {
    private val wimple = WimpleImpl.getInstance()

    private lateinit var entryAdapter: EntryItemListAdapter

    private var isAllOldActivityFetched = false

    private var maxHeight = 0
    private var touchBaseY = 0f
    private var isShowingFirstItem = false
    private var isSwipeDowned = false

    private var prevLastDate = ""
    private var countLastDateRequest = 0


    /**
     * onAttach() > onCreate() > onCreateView() > onActivityCreated() > onStart() > onResume()
     * onPause() > onStop() > onDestoryView() > onDestory() > onDetach()
     */

    override fun onResume() {
        updateSettings()
        // TODO : what is better? below line is duplicated running when activity restarting and after log in.
        updateLatestItems(false)
        super.onResume()
    }

    private fun updateLatestItems(forceUpdateMonthlyItems: Boolean) {
        // TODO : what is best? performance
        Log.e(LOG_TAG, "Force Refresh!!!")
        setShowingNotification(true, true)
        wimple.getAllEntries(DateFormatUtils.getCurrentDateString(), DateFormatUtils.getLastMonthDateString(0L), 0)

        if (monthlyDisplay) {
            wimple.getMonthlyItems(forceUpdateMonthlyItems)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_transaction_list_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sessionParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

        entryAdapter = EntryItemListAdapter(context)

        entry_list_view.setAdapter(entryAdapter)
        entry_list_view.layoutParams = sessionParams

        registerForContextMenu(entry_list_view)

        entry_list_view.setOnScrollListener(object : OnScrollListener {

            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {}

            override fun onScroll(view: AbsListView, firstVisibleItem: Int,
                                  visibleItemCount: Int, totalItemCount: Int) {

                //Log.d(LOG_TAG, "firstVisible=" + firstVisibleItem + ", visibleItemCount=" + visibleItemCount + ", totalItemcount=" + totalItemCount);

                isShowingFirstItem = firstVisibleItem == 0

                val percentage = (firstVisibleItem.toFloat() + visibleItemCount.toFloat()) / totalItemCount.toFloat() * 100
                //Log.d(LOG_TAG, "Percentage=" + percentage + ", isAllOldActivityFetched=" + isAllOldActivityFetched + ", available permit=" + available.availablePermits());

                if (percentage >= 70 &&
                        !isAllOldActivityFetched &&
                        available.tryAcquire()) {

                    Log.d(LOG_TAG, "Get More!!!, percentage=$percentage")

                    if (entryAdapter.count == 0) {
                        setShowingNotification(true, false)
                        wimple.getAllEntries(DateFormatUtils.getCurrentDateString(), DateFormatUtils.getLastMonthDateString(0L), 0)
                    } else {
                        val entry = entryAdapter.getItem(entryAdapter.count - 1) as Item
                        var lastDate = entry.dateValue

                        if (!lastDate.isEmpty()) {
                            lastDate = lastDate.substring(1)
                        }

                        //Log.d(LOG_TAG, "1 lastDate=" + lastDate + ", prevLastDate=" + prevLastDate + ", count=" + countLastDateRequest);
                        if (!prevLastDate.isEmpty() && 0 == lastDate.compareTo(prevLastDate)) {
                            countLastDateRequest += 1
                        }
                        prevLastDate = lastDate

                        if (countLastDateRequest > 3) {
                            Log.d(LOG_TAG, "No More!!!")
                            isAllOldActivityFetched = true
                            countLastDateRequest = 4
                            return
                        }
                        //Log.d(LOG_TAG, "2 lastDate=" + lastDate + ", prevLastDate=" + prevLastDate + ", count=" + countLastDateRequest);

                        setShowingNotification(true, false)
                        wimple.getAllEntries(DateFormatUtils.getServerDateString(lastDate), DateFormatUtils.getLastMonthDateString(lastDate), 0)
                    }
                }
            }
        })

        entry_list_view.setOnDataSelectionListener { _, _, position, _ ->
            val item = entryAdapter.getItem(position) as Item
            if(item is Entry){
                WimpleActivity.sm(CommandID.MODIFY_ENTRY, item)
            }else{
                WimpleActivity.sm(CommandID.ADD_MONTHLY_ITEM, item)
            }
        }

        val wm = context!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val size = Point()
        display.getSize(size)
        maxHeight = size.y

        entry_list_view.setOnTouchListener(OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    //TOUCH STARTED
                    touchBaseY = event.y
                    isSwipeDowned = false
                    return@OnTouchListener false
                }
                MotionEvent.ACTION_MOVE -> {

                    val currentY = event.y
                    val sizeInY = (touchBaseY - currentY).toDouble()

                    // TODO : position check
                    if (sizeInY < 0 && isShowingFirstItem) {
                        if (Math.abs(sizeInY) > maxHeight.toDouble() * 0.17) {
                            isSwipeDowned = true
                        }
                    }
                    return@OnTouchListener false
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    if (isSwipeDowned) {
                        isSwipeDowned = false
                        updateLatestItems(true)
                    }
                    return@OnTouchListener false
                }
            }


            false
        })

        // TODO : remove old data
        wimple.storedEntries
    }


    override fun handleMessage(msg: Message) {

        val command = msg.what
        val booleanStatus = msg.arg1 == 1
        val obj = msg.obj

        // if fragment is added or not to the activity
        if (!isAdded)
            return

        if (null == context)
            return

        when (command) {
            /*
		case  CommandID.CONNECTED :
		case  CommandID.UPDATE_FEEDS :

			//String sid = obj.toString();
			getFeeds();

			break;

		 */

            CommandID.WIMPLE_LOGGIN_SUCCESS, CommandID.GET_ALL_SECTION_RECEIVED -> {
                wimple.getAllEntries(DateFormatUtils.getCurrentDateString(), DateFormatUtils.getLastMonthDateString(0L), 0)
            }

            CommandID.GET_ENTRIES_RECEIVED -> {
                run {

                    setShowingNotification(false, true)
                    try {
                        if (!booleanStatus)
                            return

                        @Suppress("UNCHECKED_CAST") val list = obj as Collection<Entry>

                        for (item in list) {
                            entryAdapter.addItem(item)
                            //Log.d(LOG_TAG, "Entry adding - [" + item.getId() + "] " + item.getItem() + ", " + (new Date(item.getDate())).toString());
                        }
                        entryAdapter.notifyDataSetChanged()
                    } finally {
                        // TODO : I cant' find how to solve this issue.
                        if (available.availablePermits() < 1)
                            available.release()
                    }
                }
            }

            CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED -> {
                run {
                    val entryDate = obj as String

                    wimple.getAllEntries(DateFormatUtils.getServerDateString(entryDate), DateFormatUtils.getServerDateString(entryDate, -1), 0)
                    // by date limit
                    //wimple.getAllEntries(DateFormatUtils.getCurrentDateString(), DateFormatUtils.getServerDateString(entryDate, -(int)(long)(monthlyDisplayAllowingDays)), 0);

                    if (monthlyDisplay) {
                        if (booleanStatus) {
                            entryAdapter.removeSameDatedMonthlyItem(entryDate)
                        }
                        wimple.getMonthlyItems(true)
                    }
                }
            }

            CommandID.GET_MODIFY_ENTRY_RESPONSE_RECEIVED -> {

                val entry = obj as Entry

                if (booleanStatus) {
                    entryAdapter.removeEntry(entry.id)
                    entryAdapter.notifyDataSetChanged()

                    var lastDate = entry.dateValue

                    if (!lastDate.isEmpty())
                        lastDate = lastDate.substring(1)

                    wimple.getAllEntries(DateFormatUtils.getServerDateString(lastDate), DateFormatUtils.getServerDateString(lastDate, -1), 0)
                    // by date limit
                    //wimple.getAllEntries(DateFormatUtils.getCurrentDateString(), DateFormatUtils.getServerDateString(entryDate, -(int)(long)(monthlyDisplayAllowingDays)), 0);
                }
            }

            CommandID.GET_MONTHLY_ITEMS_RESPONSE_RECEIVED -> {

                if (!booleanStatus)
                    return

                if (!monthlyDisplay)
                    return

                entryAdapter.removeAllMonthlyItem()

                @Suppress("UNCHECKED_CAST") val list = obj as ArrayList<Item>
                Collections.sort(list, DateAscCompare())

                var counts = if (monthlyDisplayItemsNumbers > list.size) list.size else monthlyDisplayItemsNumbers

                for (item in list) {
                    if (counts <= 0)
                        break

                    val monthlyDisplayAllowingDays = 10L
                    if (monthlyDisplayAllowingDays < DateFormatUtils.getDifferenceDays(item.date)) {
                        Log.d(LOG_TAG, "Skip Monthly item - " + item.item + ", " + Date(item.date!!).toString())
                        break
                    }
                    counts -= 1
                    entryAdapter.addItem(item)
                    Log.d(LOG_TAG, "Adding Monthly item - ${item.id} - ${item.item} ${Date(item.date!!)}")
                    /*
                    for(i in 0 until item.values.size()){
                        Log.e(LOG_TAG, "${item.columns.get(i)} - ${item.getValue(i)}")
                    }
                    */
                }

                entryAdapter.notifyDataSetChanged()
            }

            CommandID.REMOVE_ENTRY_RESPONSE_RECEIVED -> {
                if (booleanStatus) {
                    WimpleActivity.sm(CommandID.TOAST_LONG, resources.getString(R.string.remove_entry_success))

                    // TODO : efficient
                    entryAdapter.removeEntry(obj as String)
                    entryAdapter.notifyDataSetChanged()
                    wimple.getMonthlyItems(true)
                } else {
                    WimpleActivity.sm(CommandID.TOAST_LONG, resources.getString(R.string.remove_entry_failed))
                }
            }

            CommandID.REMOVE_MONTHLY_ITEMS_RESPONSE_RECEIVED -> {
                if (booleanStatus) {
                    WimpleActivity.sm(CommandID.TOAST_LONG, resources.getString(R.string.remove_monthly_item_success))

                    // TODO : efficient
                    entryAdapter.removeItem(obj as String)
                    entryAdapter.notifyDataSetChanged()
                } else {
                    WimpleActivity.sm(CommandID.TOAST_LONG, resources.getString(R.string.remove_monthly_item_failed))
                }
            }
        }
    }

    override fun setActivityInstance(instance: WimpleActivity) {
        //mainActivity = instance;
    }

    fun setShowingNotification(show: Boolean, isLatest: Boolean) {

        if (null == context)
            return

        entry_list_notification_text.text = if (isLatest) {
            context!!.resources.getString(R.string.update_latest_items)
        } else {
            context!!.resources.getString(R.string.update_old_items)
        }

        entry_list_notification.visibility = if (show) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }


    override fun onCreateContextMenu(menu: ContextMenu, v: View,
                                     menuInfo: ContextMenuInfo) {

        if (v.id == R.id.entry_list_view) {
            val lv = v as ItemListView
            val acmi = menuInfo as AdapterContextMenuInfo
            val item = lv.getItemAtPosition(acmi.position) as Item

            menu.setHeaderTitle(item.item)

            menu.add(Menu.NONE, R.string.context_menu_delete_item, Menu.NONE, resources.getString(R.string.context_menu_delete_item))
        }
        //super.onCreateContextMenu(menu, v, menuInfo);
    }

    override fun onContextItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.string.context_menu_delete_item -> {
                val info = item.menuInfo as AdapterContextMenuInfo

                val selectedItem = entryAdapter.getItem(info.position) as Item

                Log.d(LOG_TAG, "removing item pos=${info.position}, name=${selectedItem.item}")
                //mAdapter.remove(info.position);

                // 9 : item, 7 : entry
                if (selectedItem.dateValue.startsWith("9")) {
                    wimple.removeMonthlyItem(selectedItem.id)
                } else {
                    wimple.removeEntry(selectedItem.id)
                }

                return true
            }

            R.string.context_menu_one -> {
                Toast.makeText(context, resources.getString(R.string.context_menu_one), Toast.LENGTH_LONG).show()
                return true
            }
            else -> return super.onContextItemSelected(item)
        }
    }

    private fun updateSettings() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(context)

        monthlyDisplay = sharedPref.getBoolean(SettingsFragment.KEY_MONTHLY_ITEM_DISPLAY, true)
        val pref = sharedPref.getString(SettingsFragment.KEY_MONTHLY_ITEM_COUNT, "5")
        monthlyDisplayItemsNumbers = Integer.parseInt(pref!!)
    }

    companion object {

        private val LOG_TAG = "TransactionFragment"

        // Static reference

        private var monthlyDisplay = true
        private var monthlyDisplayItemsNumbers = 4
        private val available = Semaphore(1)
    }
}
