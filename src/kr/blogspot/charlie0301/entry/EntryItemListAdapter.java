
package kr.blogspot.charlie0301.entry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import kr.blogspot.charlie0301.model.Entry;
import kr.blogspot.charlie0301.model.Entry.DateDescCompare;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public class EntryItemListAdapter extends BaseAdapter {

	//private static final String LOG_TAG = "EntryItemListAdapter";
	private Context mContext;

	private List<Entry> items = new ArrayList<Entry>();
	public EntryItemListAdapter(Context context) {
		mContext = context;
	}

	public void addItem(Entry it) {

		if(items == null){
			return;
		}

		//Log.e(LOG_TAG, "time=" + (new Date(it.getTime()).toString()) );

		int res = items.indexOf(it);
		if(res > -1){			
			items.remove(res);
			items.add(res, it);
			return;
		}else{
			items.add(it);	
		}
		
		sortByDate();
	}

	public void setListItems(List<Entry> lit) {
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

		Entry item = items.get(position);

		if (convertView == null) {
		
			return new EntryItemView(mContext, item);
		} else {

			EntryItemView itemView = (EntryItemView)convertView;
			itemView.setData(item);
			return itemView;
		}
	}

	public void clean(){
		items.clear();
	}


	public void sortByDate(){
		Collections.sort(items, new DateDescCompare());
	}

}
