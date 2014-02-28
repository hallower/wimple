package kr.blogspot.charlie0301.widget.accountstate;

import java.util.Calendar;
import java.util.Date;

import kr.blogspot.charlie0301.R;
import kr.blogspot.charlie0301.impl.WimpleImpl;
import kr.blogspot.charlie0301.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.model.Entry;
import kr.blogspot.charlie0301.model.AccountState;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AccountStateItemView extends LinearLayout {

	private final Context context;

	private TextView id = null;
	private TextView title = null;
	private TextView category = null;
	private TextView amount = null;

	public AccountStateItemView(Context context){
		super(context);
		this.context = context;	

		// Layout Inflation
		LayoutInflater inflater = (LayoutInflater)this.context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.list_account_state, this, true);

		id = (TextView)findViewById(R.id.as_item_id);
		title = (TextView)findViewById(R.id.as_item_title);
		category = (TextView)findViewById(R.id.as_item_category);
		amount = (TextView)findViewById(R.id.as_item_amount);
	}

	public AccountStateItemView(Context context, AccountState item) {
		this(context);
		setData(item);
	}


	public void setData(AccountState item) {

		id.setText(item.getAccountID());
		title.setText(item.getAccountName());
		category.setText(item.getCategory());
		amount.setText(DateFormatUtils.getDecimalFormat().format(item.getAmount()));

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
		id = null;
		title = null;
		category = null;
		amount = null;
	}

	@Override
	protected void onDetachedFromWindow() {
		clear();
		super.onDetachedFromWindow();
	}

}
