
package kr.blogspot.charlie0301.widget.budgetstate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kr.blogspot.charlie0301.model.AccountState;
import kr.blogspot.charlie0301.model.Budget;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public class BudgetStateItemListAdapter extends BaseAdapter {

	//private static final String LOG_TAG = "BudgetStateItemListAdapter";
	private Context mContext;

	private List<AccountState> items = new ArrayList<AccountState>();
	private Map<String, Budget> budgets = new HashMap<String, Budget>();
	
	public BudgetStateItemListAdapter(Context context) {
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
		sort();
	}

	
	public void setListAccountStates(List<AccountState> lit) {
		items = lit;
	}
	
	public void setBudgets(Map<String, Budget> budgets) {
		this.budgets = budgets;
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

		AccountState accountState = items.get(position);
		
		Budget budget = null;
		
		try{
			budget = budgets.get(accountState.getAccountID());	
		} catch(Exception e){
			budget = null;
		}
		
		if (convertView == null) {
			return new BudgetStateItemView(mContext, accountState, budget);
		} else {

			BudgetStateItemView BudgetStateItemView = (BudgetStateItemView)convertView;
			BudgetStateItemView.setData(accountState, budget);
			return BudgetStateItemView;
		}
	}

	public void clean(){
		items.clear();
	}


	public void sort(){
		Collections.sort(items, new AccountState.DateAscCompare());
	}
}
