package kr.blogspot.charlie0301.wimple.widget.entry;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kr.blogspot.charlie0301.wimple.model.Item;
import kr.blogspot.charlie0301.wimple.model.Item.DateDescCompare;

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
            return;
        } else {
            items.add(it);
        }

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

            if (itemDate.startsWith("9")) {
                //Log.d(LOG_TAG, "Remove Monthly Item - id=" + items.get(i).getId() +
                //		", name=" + items.get(i).getItem());
                items.remove(i);
                i -= 1;
                //return;
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
        Collections.sort(items, new DateDescCompare());
    }

}
