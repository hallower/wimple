package kr.blogspot.charlie0301.entry;

import java.util.Date;

import kr.blogspot.charlie0301.R;
import kr.blogspot.charlie0301.impl.WimpleImpl;
import kr.blogspot.charlie0301.impl.util.Utils;
import kr.blogspot.charlie0301.model.Entry;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class EntryItemView extends LinearLayout {

	private final Context context;

	private TextView date = null;
	private TextView title = null;
	private TextView memo = null;
	private TextView amount = null;
	private TextView total = null;
	private TextView left = null;
	private TextView right = null;


	public EntryItemView(Context context){
		super(context);
		this.context = context;	

		// Layout Inflation
		LayoutInflater inflater = (LayoutInflater)this.context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.list_entries, this, true);

		date = (TextView)findViewById(R.id.entry_item_date);
		title = (TextView)findViewById(R.id.entry_item_title);
		memo = (TextView)findViewById(R.id.entry_item_memo);
		amount = (TextView)findViewById(R.id.entry_item_amount);
		total = (TextView)findViewById(R.id.entry_item_total);
		left = (TextView)findViewById(R.id.entry_item_left);
		right = (TextView)findViewById(R.id.entry_item_right);

	}

	public EntryItemView(Context context, Entry item) {
		this(context);
		setData(item);
	}


	public void setData(Entry item) {

		//date.setText(formatter.format(new Date(item.getDate())));				
		date.setText(Utils.getGUIDateFormat().format(new Date(item.getDate())));
		title.setText(item.getItem());

		if(item.getMemo().isEmpty()){
			memo.setVisibility(View.GONE);
		}else{
			memo.setText(item.getMemo());
			memo.setVisibility(View.VISIBLE);
		}		
		amount.setText(Utils.getDecimalFormat().format(item.getAmount()));
		total.setText(Utils.getDecimalFormat().format(item.getBalance()));

		/*
		 * // assets
		 * // liabilities
		 * // capital
		 * // income
		 * // expenses
		 */
		left.setText(WimpleImpl.getInstance().getAccountName(item.getLeftAccountID()));
		setBackgroundAccountWidget(left, item.getLeftAccount());
		right.setText(WimpleImpl.getInstance().getAccountName(item.getRightAccountID()));
		setBackgroundAccountWidget(right, item.getRightAccount());

	}

	public void setBackgroundAccountWidget(TextView tv, String account){
		switch(account.charAt(0)){

		case 'c' :
			tv.setBackgroundResource(R.drawable.progress_n);
			break;	

		case 'e' :
			tv.setBackgroundResource(R.drawable.input_color_box_4);
			break;

		case 'a' :
			tv.setBackgroundResource(R.drawable.input_color_box);
			break;

		case 'l' :
			tv.setBackgroundResource(R.drawable.input_color_box_3);
			break;

		case 'i' :
			tv.setBackgroundResource(R.drawable.input_color_box_6);
			break;
		}
		Drawable background = tv.getBackground();
		background.setAlpha(90);
	}

	public void clear(){
		date = null;
		title = null;
		memo = null;
		amount = null;
		total = null;
		left = null;
		right = null;
	}

	@Override
	protected void onDetachedFromWindow() {
		clear();
		super.onDetachedFromWindow();
	}

}
