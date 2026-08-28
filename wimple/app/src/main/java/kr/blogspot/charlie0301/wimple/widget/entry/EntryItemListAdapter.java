package kr.blogspot.charlie0301.wimple.widget.entry;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import kr.blogspot.charlie0301.wimple.model.Entry;
import kr.blogspot.charlie0301.wimple.model.Item;

public class EntryItemListAdapter extends BaseAdapter {

    private static final String LOG_TAG = "EntryItemListAdapter";
    private Context mContext;

    private List<Item> items = new ArrayList<>();

    public EntryItemListAdapter(Context context) {
        mContext = context;
    }

    public void addItem(Item it) {

        if (items == null) {
            return;
        }

        //Log.e(LOG_TAG, "time=" + (new Date(it.getTime()).toString()) );

        int res = items.indexOf(it);
        if (res > -1) {
            items.remove(res);
            items.add(res, it);
        } else {
            items.add(it);
        }

        // Always resort, including the update-in-place branch above — skipping it there used
        // to rely on the list already being correctly ordered, which silently broke down
        // whenever this call landed between two out-of-order full sorts (e.g. a resume that
        // re-delivers already-known entries while newly-refreshed monthly items are still
        // mid-update). Re-sorting a modest list on every add is cheap and removes that whole
        // class of staleness.
        sortByDate();
    }


    public void removeEntry(String entryID) {

        for (int i = 0; i < items.size(); i++) {

            String itemDate = items.get(i).getDateValue();
            /*
			if(itemDate.startsWith("7")){
				Log.e(LOG_TAG, "MonthlyItem- date=" + items.get(i).getDateValue() + 
						", name=" + items.get(i).getItem() + " <> " + itemDate + " <>" + parsedEntryDate);
			}
			 */

            if (itemDate.startsWith("7") &&
                    0 == items.get(i).getId().compareTo(entryID)) {
                //Log.d(LOG_TAG, "Remove entry - id=" + items.get(i).getId() +
                //		", name=" + items.get(i).getItem());
                items.remove(i);
                return;
            }
        }
        Log.d(LOG_TAG, "Cante Remove entry - id=" + entryID);
    }

    public void removeItem(String itemID) {

        for (int i = 0; i < items.size(); i++) {

            String itemDate = items.get(i).getDateValue();

            if (itemDate.startsWith("9") &&
                    0 == items.get(i).getId().compareTo(itemID)) {
                //Log.d(LOG_TAG, "Remove item - id=" + items.get(i).getId() +
                //		", name=" + items.get(i).getItem());
                items.remove(i);
                return;
            }
        }
        Log.d(LOG_TAG, "Cante Remove item - id=" + itemID);
    }

    /*
     * because of no consistence between monthly item and newly added entry item,
     * just remove all same dated monthly items before all monthly item updating
     */
    public void removeSameDatedMonthlyItem(String entryDate) {

        String parsedEntryDate = entryDate;
        int pos = parsedEntryDate.indexOf(".");
        if (pos > 0) {
            parsedEntryDate = parsedEntryDate.substring(0, pos);
        }

        for (int i = 0; i < items.size(); i++) {

            String itemDate = items.get(i).getDateValue();
			/*
			if(itemDate.startsWith("9")){
				Log.e(LOG_TAG, "MonthlyItem- date=" + items.get(i).getDateValue() + 
						", name=" + items.get(i).getItem() + " <> " + itemDate + " <>" + parsedEntryDate);
			}
			 */

            if (itemDate.startsWith("9") &&
                    0 == itemDate.substring(1).compareTo(parsedEntryDate)) {
                //Log.d(LOG_TAG, "Remove Monthly Item - id=" + items.get(i).getId() +
                //		", name=" + items.get(i).getItem());
                items.remove(i);
                i -= 1;
                //return;
            }
        }
    }

    public void removeAllMonthlyItem() {

        for (int i = 0; i < items.size(); i++) {

            String itemDate = items.get(i).getDateValue();

            if (! (items.get(i) instanceof Entry)) {
                items.remove(i);
                i -= 1;
            }
        }
    }

    public void setListItems(List<Item> lit) {
        items = lit;
    }

    public int getCount() {
        return items.size();
    }

    public Object getItem(int position) {
        return items.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {

        Item item = items.get(position);

        if (null == convertView) {

            return new EntryItemView(mContext, item);
        } else {

            EntryItemView itemView = (EntryItemView) convertView;
            if (!itemView.setData(item))
                itemView = new EntryItemView(mContext, item);
            return itemView;
        }
    }

    public void clean() {
        items.clear();
    }


    private void sortByDate() {
        Collections.sort(items, new MonthlyAwareDateCompare());
    }

    /**
     * Regular entries keep the existing newest-first order (dateValue descending), and always
     * sort entirely after every monthly item — this is a strict two-block grouping, not a
     * blended date sort, so monthly previews and recorded entries never interleave. Monthly
     * ("9"-prefixed dateValue) items are ordered among themselves by their real due-date
     * timestamp DESCENDING, so the furthest-future occurrence is at the top of the monthly
     * block and the nearest one (today, or still-unpaid and overdue) sits at the bottom,
     * immediately above where the real entries (newest-first) begin.
     *
     * This deliberately compares the full {@link Item#getDate()} timestamp rather than just
     * day-of-month: a monthly item's due date is already the server's resolved NEXT
     * occurrence (year + month + day) — e.g. once this month's charge is entered, the item's
     * date rolls forward to next month — so extracting day-of-month alone and reasoning about
     * "today ± wraparound" both threw away information the data already had AND actively
     * misread a same-day-number-next-month due date (e.g. 9/28 when today is 8/28) as due
     * today. A plain timestamp compare has no such ambiguity.
     */
    private static class MonthlyAwareDateCompare implements Comparator<Item> {
        @Override
        public int compare(Item lhs, Item rhs) {
            boolean lhsMonthly = lhs.getDateValue().startsWith("9");
            boolean rhsMonthly = rhs.getDateValue().startsWith("9");
            if (lhsMonthly != rhsMonthly) {
                return lhsMonthly ? -1 : 1;
            }
            if (!lhsMonthly) {
                return -1 * lhs.getDateValue().compareTo(rhs.getDateValue());
            }
            return Long.compare(rhs.getDate(), lhs.getDate());
        }
    }

}
