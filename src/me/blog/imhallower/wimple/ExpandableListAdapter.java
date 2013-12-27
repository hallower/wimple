package me.blog.imhallower.wimple;

import java.util.List;
import java.util.Map;

import me.blog.imhallower.wimple.model.Account;
import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

public class ExpandableListAdapter extends BaseExpandableListAdapter{
	private Context _context;
	private static final String LOG_TAG = "ExpandableListAdapter";

	private List<String> _listDataHeader; // header titles
	// child data in format of header title, child title
	private Map<String, List<Account>> _listDataChild;

	private boolean isSelected = false;
	private int selectedGroupPosition;
	private int selectedChildPosition;

	public ExpandableListAdapter(Context context, List<String> listDataHeader,
			Map<String, List<Account>> listChildData) {
		this._context = context;
		this._listDataHeader = listDataHeader;
		this._listDataChild = listChildData;
	}

	public void setData(List<String> listDataHeader,
			Map<String, List<Account>> listChildData) {
		this._listDataHeader = listDataHeader;
		this._listDataChild = listChildData;
	}

	@Override
	public Object getChild(int groupPosition, int childPosititon) {
		try{
			return this._listDataChild.get(this._listDataHeader.get(groupPosition))
					.get(childPosititon);
		}catch(Exception e){
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

		final String childText = ((Account) getChild(groupPosition, childPosition)).getTitle();

		if (convertView == null) {
			LayoutInflater infalInflater = (LayoutInflater) this._context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = infalInflater.inflate(R.layout.exp_list_item, null);
		}

		TextView txtListChild = (TextView) convertView
				.findViewById(R.id.expListGroupItem);

		txtListChild.setText(childText);
		return convertView;
	}

	@Override
	public int getChildrenCount(int groupPosition) {

		try{
			return this._listDataChild.get(this._listDataHeader.get(groupPosition))
					.size();	
		}catch(Exception e){
			return 0;
		}

	}

	@Override
	public Object getGroup(int groupPosition) {
		return this._listDataHeader.get(groupPosition);
	}

	@Override
	public int getGroupCount() {
		return this._listDataHeader.size();
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
			LayoutInflater infalInflater = (LayoutInflater) this._context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = infalInflater.inflate(R.layout.exp_list_group, null);
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

	public void setSelected(int groupPosition, int childPosition, long id){
		this.isSelected = true;
		this.selectedGroupPosition = groupPosition;
		this.selectedChildPosition = childPosition;

		Log.d(LOG_TAG, "Selected => " + getSelected().getTitle());
	}

	public Account getSelected(){
		if(false == this.isSelected){
			return null;
		}

		return (Account) getChild(selectedGroupPosition, selectedChildPosition);
	}
}
