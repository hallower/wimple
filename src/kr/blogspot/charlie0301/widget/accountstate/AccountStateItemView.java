package kr.blogspot.charlie0301.widget.accountstate;

import kr.blogspot.charlie0301.R;
import kr.blogspot.charlie0301.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.model.AccountState;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AccountStateItemView extends LinearLayout {

	private final Context context;

	private LinearLayout llbackground = null;
	private TextView type = null;
	private TextView title = null;
	private TextView amount = null;

	public AccountStateItemView(Context context){
		super(context);
		this.context = context;	

		// Layout Inflation
		LayoutInflater inflater = (LayoutInflater)this.context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.list_account_state, this, true);

		llbackground = (LinearLayout)findViewById(R.id.as_backgroud);
		type = (TextView)findViewById(R.id.as_item_type);
		title = (TextView)findViewById(R.id.as_item_title);
		amount = (TextView)findViewById(R.id.as_item_amount);
	}

	public AccountStateItemView(Context context, AccountState item) {
		this(context);
		setData(item);
	}


	public void setData(AccountState item) {

		title.setText(item.getAccountName());
		amount.setText(DateFormatUtils.getDecimalFormat().format(item.getAmount()));

		setBackgroundAccountWidget(title, item.getCategory());

		LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);

		if(item.getGroup()){
			//type.setText(context.getResources().getString(R.string.title_group));
			//type.setTextColor(context.getResources().getColor(R.color.text_black));
			title.setBackgroundResource(R.drawable.progress_p);
			amount.setTypeface(null,Typeface.BOLD);
			layoutParams.setMargins(0, 0, 0, 0);
		}else{
			layoutParams.setMargins(50, 0, 0, 0);
		}
		llbackground.setLayoutParams(layoutParams);

	}

	public void setBackgroundAccountWidget(TextView tv, String account){
		switch(account.charAt(0)){

		case 'a' :
			type.setText(context.getResources().getString(R.string.title_saving));
			type.setTextColor(context.getResources().getColor(R.color.text_blue));
			tv.setBackgroundResource(R.drawable.input_color_box_3);
			break;

		case 'l' :
			type.setText(context.getResources().getString(R.string.title_debt));
			type.setTextColor(context.getResources().getColor(R.color.text_red));

			tv.setBackgroundResource(R.drawable.input_color_box);
			break;

		default :
			type.setText(context.getResources().getString(R.string.title_adjust));
			tv.setBackgroundResource(R.drawable.progress_n);
			break;	

		}
		Drawable background = tv.getBackground();
		background.setAlpha(90);
	}

	public void clear(){
		type = null;
		title = null;
		amount = null;
	}

	@Override
	protected void onDetachedFromWindow() {
		clear();
		super.onDetachedFromWindow();
	}

}
