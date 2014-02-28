
package kr.blogspot.charlie0301.widget.accountstate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kr.blogspot.charlie0301.model.AccountState;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public class AccountStateItemListAdapter extends BaseAdapter {

	private static final String LOG_TAG = "AccountStateListAdapter";
	private Context mContext;

	private List<AccountState> items = new ArrayList<AccountState>();
	public AccountStateItemListAdapter(Context context) {
		mContext = context;
	}

	public void addAccountState(AccountState it) {

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
		//sort();
	}

	
	public void setListAccountStates(List<AccountState> lit) {
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

		AccountState AccountState = items.get(position);

		if (convertView == null) {

			return new AccountStateItemView(mContext, AccountState);
		} else {

			AccountStateItemView AccountStateItemView = (AccountStateItemView)convertView;
			AccountStateItemView.setData(AccountState);
			return AccountStateItemView;
		}
	}

	public void clean(){
		items.clear();
	}


	public void sort(){
		Collections.sort(items, new AccountState.DateAscCompare());
	}
}
