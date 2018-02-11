package kr.blogspot.charlie0301.wimple.widget.accountstate;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import kr.blogspot.charlie0301.wimple.R;
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.wimple.model.AccountState;

@SuppressWarnings("deprecation")
public class AccountStateItemView extends LinearLayout {

    private final Context context;

    private LinearLayout llBackground = null;
    private TextView title = null;
    private TextView amount = null;

    public AccountStateItemView(Context context) {
        super(context);
        this.context = context;

        // Layout Inflation
        LayoutInflater inflater = (LayoutInflater) this.context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.list_account_state, this, true);

        llBackground = (LinearLayout) findViewById(R.id.as_background);
        title = (TextView) findViewById(R.id.as_item_title);
        amount = (TextView) findViewById(R.id.as_item_amount);
    }

    public AccountStateItemView(Context context, AccountState item) {
        this(context);
        setData(item);
    }


    public boolean setData(AccountState item) {

        if (null == title)
            return false;

        title.setText(item.getAccountName());
        amount.setText(DateFormatUtils.getDecimalFormat().format(item.getAmount()));

        setBackgroundAccountWidget(title, item.getCategory());

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

        if (item.getGroup()) {
            //type.setText(context.getResources().getString(R.string.title_group));
            //type.setTextColor(context.getResources().getColor(R.color.text_black));
            title.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
            amount.setTypeface(null, Typeface.BOLD);
            layoutParams.setMargins(0, 0, 0, 0);
        } else {
            amount.setTypeface(null, Typeface.NORMAL);
            layoutParams.setMargins(30, 0, 0, 0);
        }
        llBackground.setLayoutParams(layoutParams);

        return true;
    }

    public void setBackgroundAccountWidget(TextView tv, String account) {
        switch (account.charAt(0)) {

            case 'a':
                tv.setBackgroundColor(getResources().getColor(R.color.text_blue));
                //tv.setBackgroundResource(R.drawable.input_color_box_3);
                break;

            case 'l':
                tv.setBackgroundColor(getResources().getColor(R.color.text_red));
                //tv.setBackgroundResource(R.drawable.input_color_box);
                break;

            case 'i':
                tv.setBackgroundColor(getResources().getColor(R.color.text_green));
                //tv.setBackgroundResource(R.drawable.input_color_box_6);
                break;

            case 'e':
                tv.setBackgroundColor(getResources().getColor(R.color.text_yellow));
                //tv.setBackgroundResource(R.drawable.input_color_box_4);
                break;

            default:
                tv.setBackgroundResource(R.drawable.progress_n);
                break;

        }
        Drawable background = tv.getBackground();
        background.setAlpha(90);
    }

    public void clear() {
        title = null;
        amount = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        clear();
        super.onDetachedFromWindow();
    }

}
