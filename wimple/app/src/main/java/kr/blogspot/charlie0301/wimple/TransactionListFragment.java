package kr.blogspot.charlie0301.wimple;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.Semaphore;

import kr.blogspot.charlie0301.wimple.impl.WimpleImpl;
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.wimple.model.Entry;
import kr.blogspot.charlie0301.wimple.model.Item;
import kr.blogspot.charlie0301.wimple.model.Item.DateAscCompare;
import kr.blogspot.charlie0301.wimple.widget.ItemListView;
import kr.blogspot.charlie0301.wimple.widget.OnItemSelectionListener;
import kr.blogspot.charlie0301.wimple.widget.entry.EntryItemListAdapter;

import kr.blogspot.charlie0301.wimple.WimpleActivity.Companion.CommandID;

public class TransactionListFragment extends Fragment implements IWimpleFragment {

    private final static String LOG_TAG = "TransactionFragment";
    private final WimpleImpl wimple = WimpleImpl.getInstance();
    //private WimpleActivity mainActivity = null;

    private View view;
    private Context context;

    // Static reference

    private static boolean monthlyDisplay = true;
    private static int monthlyDisplayItemsNumbers = 4;


    private WeakReference<EntryItemListAdapter> entryAdapter;
    private LinearLayout llNotification;
    private TextView txtNotification;

    private boolean isAllOldActivityFechted = false;
    private static final Semaphore available = new Semaphore(1);

    private int maxHeight = 0;
    private float touchBaseY = 0;
    private boolean isShowingFirstItem = false;
    private boolean isSwipeDowned = false;

    private String prevLastDate = "";
    private int countLastDateRequest = 0;


    /**
     * onAttach() > onCreate() > onCreateView() > onActivityCreated() > onStart() > onResume()
     * onPause() > onStop() > onDestoryView() > onDestory() > onDetach()
     */

    @Override
    public void onResume() {
        context = WimpleActivity.Companion.getContext().get();

        updateSettings();
        // TODO : what is better? below line is duplicated running when activity restarting and after log in.
        updateLatestItems(false);
        super.onResume();
    }

    public void updateLatestItems(boolean forceUpdateMonthlyItems) {
        // TODO : what is best? performance
        Log.e(LOG_TAG, "Force Refresh!!!");
        setShowingNotification(true, true);
        wimple.getAllEntries(DateFormatUtils.getCurrentDateString(), DateFormatUtils.getLastMonthDateString(0L), 0);

        if (monthlyDisplay) {
            wimple.getMonthlyItems(forceUpdateMonthlyItems);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        context = WimpleActivity.Companion.getContext().get();
        view = inflater.inflate(R.layout.fragment_transaction_list_tab, container, false);

        llNotification = (LinearLayout) view.findViewById(R.id.entry_list_notification);
        txtNotification = (TextView) view.findViewById(R.id.entry_list_notification_text);

        FrameLayout.LayoutParams sessionParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        WeakReference<ItemListView> entryList = new WeakReference<>((ItemListView) view.findViewById(R.id.entry_list_view));
        entryAdapter = new WeakReference<>(new EntryItemListAdapter(context));

        entryList.get().setAdapter(entryAdapter.get());
        entryList.get().setLayoutParams(sessionParams);

        registerForContextMenu(entryList.get());

        entryList.get().setOnScrollListener(new OnScrollListener() {

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {

                //Log.d(LOG_TAG, "firstVisible=" + firstVisibleItem + ", visibleItemCount=" + visibleItemCount +
                //		", totalItemcount=" + totalItemCount);

                isShowingFirstItem = firstVisibleItem == 0;

                float percentage = (((float) firstVisibleItem + (float) visibleItemCount) / (float) totalItemCount) * 100;
                //Log.d(LOG_TAG, "Percentage=" + percentage + ", isAllOldActivityFechted=" + isAllOldActivityFechted + ", available permit=" + available.availablePermits());

                if (percentage >= 70 &&
                        !isAllOldActivityFechted &&
                        available.tryAcquire()) {

                    Log.d(LOG_TAG, "Get More!!!, percentage=" + percentage);

                    if (entryAdapter.get().getCount() == 0) {
                        setShowingNotification(true, false);
                        wimple.getAllEntries(DateFormatUtils.getCurrentDateString(), DateFormatUtils.getLastMonthDateString(0L), 0);
                    } else {
                        Item entry = (Item) entryAdapter.get().getItem(entryAdapter.get().getCount() - 1);
                        String lastDate = entry.getDateValue();

                        if (!lastDate.isEmpty()) {
                            lastDate = lastDate.substring(1);
                        }

                        //Log.d(LOG_TAG, "1 lastDate=" + lastDate + ", prevLastDate=" + prevLastDate + ", count=" + countLastDateRequest);
                        if (!prevLastDate.isEmpty() &&
                                0 == lastDate.compareTo(prevLastDate)) {
                            countLastDateRequest += 1;
                        }
                        prevLastDate = lastDate;

                        if (countLastDateRequest > 3) {
                            Log.d(LOG_TAG, "No More!!!");
                            isAllOldActivityFechted = true;
                            countLastDateRequest = 4;
                            return;
                        }
                        //Log.d(LOG_TAG, "2 lastDate=" + lastDate + ", prevLastDate=" + prevLastDate + ", count=" + countLastDateRequest);

                        setShowingNotification(true, false);
                        wimple.getAllEntries(DateFormatUtils.getServerDateString(lastDate), DateFormatUtils.getLastMonthDateString(lastDate), 0);
                    }
                }
            }
        });

        entryList.get().setOnDataSelectionListener(new OnItemSelectionListener() {

            @Override
            public void onDataSelected(AdapterView<?> parent, View v, int position, long id) {
                Item item = (Item) entryAdapter.get().getItem(position);
                WimpleActivity.Companion.sm( CommandID.MODIFY_ENTRY_OR_ADD_MONTHLY_ITEM, item);
            }
        });


        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        maxHeight = size.y;

        entryList.get().setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        //TOUCH STARTED
                        touchBaseY = event.getY();
                        isSwipeDowned = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:

                        float currentY = event.getY();
                        double sizeInY = touchBaseY - currentY;

                        // TODO : position check
                        if (sizeInY < 0 &&
                                isShowingFirstItem) {
                            if (Math.abs(sizeInY) > ((double) maxHeight * 0.17)) {
                                isSwipeDowned = true;
                            }
                        }
                        return false;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        if (isSwipeDowned) {
                            isSwipeDowned = false;
                            updateLatestItems(true);
                        }
                        return false;
                }


                return false;
            }
        });

        // TODO : remove old data
        wimple.getStoredEntries();
        return view;
    }


    @SuppressWarnings("unchecked")
    public void handleMessage(Message msg) {

        int command = msg.what;
        boolean booleanStatus = msg.arg1 == 1;
        Object obj = msg.obj;

        // if fragment is added or not to the activity
        if (!isAdded())
            return;

        if (null == context) {
            context = WimpleActivity.Companion.getContext().get();
            if (null == context)
                return;
        }

        switch (command) {
        /*
		case  CommandID.CONNECTED :
		case  CommandID.UPDATE_FEEDS :

			//String sid = obj.toString();
			getFeeds();

			break;

		 */

            case  CommandID.WIMPLE_LOGGIN_SUCCESS:
            case  CommandID.GET_ALL_SECTION_RECEIVED: {
                wimple.getAllEntries(DateFormatUtils.getCurrentDateString(), DateFormatUtils.getLastMonthDateString(0L), 0);
                break;
            }

            case  CommandID.GET_ENTRIES_RECEIVED: {

                setShowingNotification(false, true);
                try {
                    if (!booleanStatus)
                        return;

                    if (null == entryAdapter.get())
                        return;

                    Collection<Entry> list = (Collection<Entry>) obj;

                    for (Entry item : list) {
                        entryAdapter.get().addItem(item);
                        //Log.d(LOG_TAG, "Entry adding - [" + item.getId() + "] " + item.getItem() + ", " + (new Date(item.getDate())).toString());
                    }
                    entryAdapter.get().notifyDataSetChanged();
                    break;
                } finally {
                    // TODO : I cant' find how to solve this issue.
                    if (available.availablePermits() < 1)
                        available.release();
                }
            }


            case  CommandID.GET_MAKE_ENTRY_RESPONSE_RECEIVED: {
                String entryDate = (String) obj;

                if (null == entryAdapter.get())
                    return;

                wimple.getAllEntries(DateFormatUtils.getServerDateString(entryDate), DateFormatUtils.getServerDateString(entryDate, -1), 0);
                // by date limit
                //wimple.getAllEntries(DateFormatUtils.getCurrentDateString(), DateFormatUtils.getServerDateString(entryDate, -(int)(long)(monthlyDisplayAllowingDays)), 0);

                if (monthlyDisplay) {
                    if (booleanStatus) {
                        entryAdapter.get().removeSameDatedMonthlyItem(entryDate);
                    }
                    wimple.getMonthlyItems(true);
                }
            }
            break;

            case  CommandID.GET_MODIFY_ENTRY_RESPONSE_RECEIVED: {

                Entry entry = (Entry) obj;

                if (booleanStatus) {
                    entryAdapter.get().removeEntry(entry.getId());
                    entryAdapter.get().notifyDataSetChanged();

                    String lastDate = entry.getDateValue();

                    if (!lastDate.isEmpty())
                        lastDate = lastDate.substring(1);

                    wimple.getAllEntries(DateFormatUtils.getServerDateString(lastDate), DateFormatUtils.getServerDateString(lastDate, -1), 0);
                    // by date limit
                    //wimple.getAllEntries(DateFormatUtils.getCurrentDateString(), DateFormatUtils.getServerDateString(entryDate, -(int)(long)(monthlyDisplayAllowingDays)), 0);
                }
            }
            break;

            case  CommandID.GET_MONTHLY_ITEMS_RESPONSE_RECEIVED: {

                if (!booleanStatus)
                    return;

                if (null == entryAdapter.get())
                    return;

                if (!monthlyDisplay)
                    return;

                entryAdapter.get().removeAllMonthlyItem();

                ArrayList<Item> list = (ArrayList<Item>) obj;
                Collections.sort(list, new DateAscCompare());

                int counts = (monthlyDisplayItemsNumbers > list.size()) ? list.size() : monthlyDisplayItemsNumbers;

                for (Item item : list) {
                    if (counts <= 0)
                        break;

                    Long monthlyDisplayAllowingDays = 10L;
                    if (monthlyDisplayAllowingDays < DateFormatUtils.getDifferenceDays(item.getDate())) {
                        Log.d(LOG_TAG, "Skip Monthly item - " + item.getItem() + ", " + (new Date(item.getDate())).toString());
                        break;
                    }
                    counts -= 1;
                    entryAdapter.get().addItem(item);
                    Log.d(LOG_TAG, "Adding Monthly item - " + item.getItem() + ", " + (new Date(item.getDate())).toString());
                }

                entryAdapter.get().notifyDataSetChanged();
            }
            break;

            case  CommandID.REMOVE_ENTRY_RESPONSE_RECEIVED: {
                if (booleanStatus) {
                    WimpleActivity.Companion.sm( CommandID.TOAST_LONG, getResources().getString(R.string.remove_entry_success));

                    // TODO : efficient
                    entryAdapter.get().removeEntry((String) obj);
                    entryAdapter.get().notifyDataSetChanged();
                    wimple.getMonthlyItems(true);
                } else {
                    WimpleActivity.Companion.sm( CommandID.TOAST_LONG, getResources().getString(R.string.remove_entry_failed));
                }
            }
            break;

            case  CommandID.REMOVE_MONTHLY_ITEMS_RESPONSE_RECEIVED: {
                if (booleanStatus) {
                    WimpleActivity.Companion.sm( CommandID.TOAST_LONG, getResources().getString(R.string.remove_monthly_item_success));

                    // TODO : efficient
                    entryAdapter.get().removeItem((String) obj);
                    entryAdapter.get().notifyDataSetChanged();
                } else {
                    WimpleActivity.Companion.sm( CommandID.TOAST_LONG, getResources().getString(R.string.remove_monthly_item_failed));
                }
            }
            break;

        }
    }

    @Override
    public void onDetach() {

        context = null;
        view = null;
        super.onDetach();
    }

    @Override
    public void setActivityInstance(WimpleActivity instance) {
        //mainActivity = instance;
    }

    public void setShowingNotification(boolean show, boolean isLatest) {

        if (null == context) {
            context = WimpleActivity.Companion.getContext().get();
            if (null != context) {
                if (isLatest) {
                    txtNotification.setText(context.getResources().getString(R.string.update_latest_items));
                } else {
                    txtNotification.setText(context.getResources().getString(R.string.update_old_items));
                }
            }
        }

        if (show) {
            llNotification.setVisibility(View.VISIBLE);
        } else {
            llNotification.setVisibility(View.GONE);
        }
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {

        if (v.getId() == R.id.entry_list_view) {
            ItemListView lv = (ItemListView) v;
            AdapterView.AdapterContextMenuInfo acmi = (AdapterContextMenuInfo) menuInfo;
            Item item = (Item) lv.getItemAtPosition(acmi.position);

            menu.setHeaderTitle(item.getItem());

            menu.add(Menu.NONE, R.string.context_menu_delete_item, Menu.NONE, getResources().getString(R.string.context_menu_delete_item));
        }
        //super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.string.context_menu_delete_item:
                AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

                Item selectedItem = (Item) entryAdapter.get().getItem(info.position);

                Log.d(LOG_TAG, "removing item pos=" + info.position + ", name=" + selectedItem.getItem());
                //mAdapter.remove(info.position);

                // 9 : item, 7 : entry
                if (selectedItem.getDateValue().startsWith("9")) {
                    wimple.removeMonthlyItem(selectedItem.getId());
                } else {
                    wimple.removeEntry(selectedItem.getId());
                }

                return true;

            case R.string.context_menu_one:
                Toast.makeText(context, getResources().getString(R.string.context_menu_one), Toast.LENGTH_LONG).show();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void updateSettings() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

        monthlyDisplay = sharedPref.getBoolean(SettingsFragment.KEY_MONTHLY_ITEM_DISPLAY, true);
        String pref = sharedPref.getString(SettingsFragment.KEY_MONTHLY_ITEM_COUNT, "5");
        monthlyDisplayItemsNumbers = Integer.parseInt(pref);
    }
}
