package kr.blogspot.charlie0301.wimple.widget.budgetstate;

import kr.blogspot.charlie0301.wimple.R;
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.wimple.model.AccountState;
import kr.blogspot.charlie0301.wimple.model.Budget;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

public class BudgetStateItemView extends LinearLayout {

	private final Context context;

	private LinearLayout llbackground = null;
	private TextView budget = null;
	private TextView title = null;
	private TextView percentage = null;
	private TextView amount = null;

	public BudgetStateItemView(Context context){
		super(context);
		this.context = context;	

		// Layout Inflation
		LayoutInflater inflater = (LayoutInflater)this.context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.list_budget_state, this, true);

		llbackground = (LinearLayout)findViewById(R.id.as_background);
		budget = (TextView)findViewById(R.id.as_item_budget);
		title = (TextView)findViewById(R.id.as_item_title);
		percentage = (TextView)findViewById(R.id.as_item_percentage);
		amount = (TextView)findViewById(R.id.as_item_amount);
	}

	public BudgetStateItemView(Context context, AccountState item) {
		this(context);
		setData(item);
	}

	public BudgetStateItemView(Context context, AccountState item, Budget budget) {
		this(context);
		setData(item, budget);
	}

	public boolean setData(AccountState item) {
		return setData(item, null);
	}

	public boolean setData(AccountState item, Budget budget) {

		if(null == title)
			return false;

		title.setText(item.getAccountName());
		amount.setText(DateFormatUtils.getDecimalFormat().format(item.getAmount()));
		amount.setTextColor(getResources().getColor(R.color.text_black));
		setBackgroundAccountWidget(title, item.getCategory());

		LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

		if(item.getGroup()){
			//type.setText(context.getResources().getString(R.string.title_group));
			//type.setTextColor(context.getResources().getColor(R.color.text_black));
			title.setBackgroundResource(R.drawable.progress_p);
			amount.setTypeface(null,Typeface.BOLD);
			layoutParams.setMargins(0, 0, 0, 0);
		}else{
			amount.setTypeface(null,Typeface.NORMAL);
			layoutParams.setMargins(50, 0, 0, 0);
		}
		llbackground.setLayoutParams(layoutParams);

		if(null == budget){
			return true;
		}

		amount.setTextColor(getResources().getColor(R.color.text_black));
		percentage.setTextColor(getResources().getColor(R.color.text_black));

		if(budget.getBudget() > 0){

			if(item.getAmount() > budget.getBudget()){
				if(item.getCategory().startsWith("in")){
					amount.setTextColor(getResources().getColor(R.color.text_blue));
					percentage.setTextColor(getResources().getColor(R.color.text_blue));
				}else{
					amount.setTextColor(getResources().getColor(R.color.text_red));	
					percentage.setTextColor(getResources().getColor(R.color.text_red));
				}
			}		

			this.budget.setText(DateFormatUtils.getDecimalFormat().format(budget.getBudget()));
			int pc = (int) ((item.getAmount() / budget.getBudget()) * 100);
			this.percentage.setText(String.valueOf(pc) + "%");
		}else{
			this.budget.setText(getResources().getString(R.string.budget_nothing_budget));
			this.percentage.setText(getResources().getString(R.string.budget_nothing_budget));
		}
		return true;
	}

	public void setBackgroundAccountWidget(TextView tv, String account){
		switch(account.charAt(0)){

		case 'a' :
			tv.setBackgroundColor(getResources().getColor(R.color.text_blue));
			//tv.setBackgroundResource(R.drawable.input_color_box_3);
			break;

		case 'l' :
			tv.setBackgroundColor(getResources().getColor(R.color.text_red));
			//tv.setBackgroundResource(R.drawable.input_color_box);
			break;

		case 'i' :
			tv.setBackgroundColor(getResources().getColor(R.color.text_green));
			//tv.setBackgroundResource(R.drawable.input_color_box_6);
			break;

		case 'e' :
			tv.setBackgroundColor(getResources().getColor(R.color.text_yellow));
			//tv.setBackgroundResource(R.drawable.input_color_box_4);
			break;

		default :
			tv.setBackgroundResource(R.drawable.progress_n);
			break;	

		}
		Drawable background = tv.getBackground();
		background.setAlpha(90);
	}

	public void clear(){
		budget = null;
		title = null;
		amount = null;
	}

	@Override
	protected void onDetachedFromWindow() {
		clear();
		super.onDetachedFromWindow();
	}

}
