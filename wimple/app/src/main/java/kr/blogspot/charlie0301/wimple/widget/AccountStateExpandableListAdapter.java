package kr.blogspot.charlie0301.wimple.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import kr.blogspot.charlie0301.wimple.R;
import kr.blogspot.charlie0301.wimple.model.AccountState;

public class AccountStateExpandableListAdapter extends BaseExpandableListAdapter {
    private Context context;
    private static final String LOG_TAG = "ExpandableListAdapter";

    private List<String> listDataHeader; // header titles
    // child data in format of header title, child title
    private Map<String, List<AccountState>> listDataChild;

    private boolean isSelected = false;
    private int selectedGroupPosition = -1;
    private int selectedChildPosition = -1;

    private AccountStateExpandableListAdapter(Context context) {
        this.context = context;
    }

    public AccountStateExpandableListAdapter(Context context, List<String> listDataHeader,
                                             Map<String, List<AccountState>> listChildData) {
        this(context);
        this.listDataHeader = listDataHeader;
        this.listDataChild = listChildData;
        sort();
    }

    public void setData(List<String> listDataHeader,
                        Map<String, List<AccountState>> listChildData) {
        this.listDataHeader = listDataHeader;
        this.listDataChild = listChildData;
        sort();
    }

    private void sort() {
        for (String key : this.listDataChild.keySet()) {
            List<AccountState> value = this.listDataChild.get(key);
            Collections.sort(value, new AccountState.DateAscCompare());
        }
    }

    public void clear() {
        try {
            this.listDataHeader.clear();
            this.listDataChild.clear();
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public Object getChild(int groupPosition, int childPosititon) {
        try {
            return this.listDataChild.get(this.listDataHeader.get(groupPosition))
                    .get(childPosititon);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public View getChildView(int groupPosition, final int childPosition,
                             boolean isLastChild, View convertView, ViewGroup parent) {

        final String childText = ((AccountState) getChild(groupPosition, childPosition)).getAccountName().replaceAll("\\r\\n|\\r|\\n", "");

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) this.context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.exp_list_item, null);
        }

        TextView txtListChild = (TextView) convertView
                .findViewById(R.id.expListGroupItem);

        txtListChild.setText(childText);

        if (groupPosition == selectedGroupPosition &&
                childPosition == selectedChildPosition) {
            txtListChild.setTextColor(context.getResources().getColor(R.color.text_red));
        } else {
            txtListChild.setTextColor(context.getResources().getColor(R.color.text_basic));
        }
        return convertView;
    }

    @Override
    public int getChildrenCount(int groupPosition) {

        try {
            return this.listDataChild.get(this.listDataHeader.get(groupPosition))
                    .size();
        } catch (Exception e) {
            return 0;
        }

    }

    @Override
    public Object getGroup(int groupPosition) {
        return this.listDataHeader.get(groupPosition);
    }

    @Override
    public int getGroupCount() {
        try {
            return this.listDataHeader.size();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded,
                             View convertView, ViewGroup parent) {
        String headerTitle = (String) getGroup(groupPosition);
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) this.context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.exp_list_group, null);
        }

        TextView lblListHeader = (TextView) convertView
                .findViewById(R.id.expListGroupHeader);
        lblListHeader.setTypeface(null, Typeface.BOLD);
        lblListHeader.setText(headerTitle);

        return convertView;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }


    @Override
    public void notifyDataSetChanged() {
        // TODO : is need this?
        //this.isSelected = false;
        super.notifyDataSetChanged();
    }

    public void clearSelection() {
        this.isSelected = false;
        this.selectedGroupPosition = -1;
        this.selectedChildPosition = -1;
        this.notifyDataSetChanged();
        Log.d(LOG_TAG, "Clear Selection");
    }

    public void setSelected(int groupPosition, int childPosition, long id) {
        this.isSelected = true;
        this.selectedGroupPosition = groupPosition;
        this.selectedChildPosition = childPosition;
        this.notifyDataSetChanged();
        Log.d(LOG_TAG, "Selected => " + getSelected().getAccountName());
    }
/*
    public int setSelected(String id){
		
		for(String key : listDataChild.keySet()){
			List<AccountState> list = listDataChild.get(key);			
			
			for(AccountState account : list){
				if(0 == account.getId().compareTo(id)){
					this.isSelected = true;
					this.selectedGroupPosition = listDataHeader.indexOf(key);
					this.selectedChildPosition = list.indexOf(account);
					this.notifyDataSetChanged();
					Log.d(LOG_TAG, "Selected, group=" + selectedGroupPosition + ", child=" + selectedChildPosition + ", account=" + account.getTitle());
					//Log.d(LOG_TAG, "Selected => " + getSelected().getTitle());					

					return this.selectedGroupPosition;
				}
			}
		}
		Log.e(LOG_TAG, "Cant find selected item => " + id);
		return -1;
	}
*/

    public int getSelectedGroupPosition() {
        return selectedGroupPosition;
    }

    public int getSelectedChildPosition() {
        return selectedChildPosition;
    }

    private AccountState getSelected() {
        if (!this.isSelected) {
            return null;
        }

        return (AccountState) getChild(selectedGroupPosition, selectedChildPosition);
    }

    public boolean isSelected() {
        return (this.selectedGroupPosition != -1) && (this.selectedChildPosition != -1);
    }
}
