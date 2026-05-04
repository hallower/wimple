package kr.blogspot.charlie0301.wimple.widget.entry;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Date;

import kr.blogspot.charlie0301.wimple.R;
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl;
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.wimple.model.Entry;
import kr.blogspot.charlie0301.wimple.model.Item;

@SuppressWarnings("deprecation")
public class EntryItemView extends LinearLayout {

    private static final String LOG_TAG = "EntryItemView";
    private final Context context;

    private TextView date = null;
    private TextView title = null;
    private TextView memo = null;
    private TextView amount = null;
    private TextView total = null;
    private TextView left = null;
    private TextView right = null;
    private TextView notyet = null;

    public EntryItemView(Context context) {
        super(context);
        this.context = context;

        // Layout Inflation
        LayoutInflater inflater = (LayoutInflater) this.context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.list_entries, this, true);

        date = (TextView) findViewById(R.id.entry_item_date);
        title = (TextView) findViewById(R.id.entry_item_title);
        memo = (TextView) findViewById(R.id.entry_item_memo);
        amount = (TextView) findViewById(R.id.entry_item_amount);
        total = (TextView) findViewById(R.id.entry_item_total);
        left = (TextView) findViewById(R.id.entry_item_left);
        right = (TextView) findViewById(R.id.entry_item_right);
        notyet = (TextView) findViewById(R.id.entry_item_notyet);
    }

    public EntryItemView(Context context, Item item) {
        this(context);
        setData(item);
    }


    public boolean setData(Item item) {

        if (null == date)
            return false;

        //date.setText(formatter.format(new Date(item.getDate())));
        date.setText(DateFormatUtils.getGUIDateFormat().format(new Date(item.getDate())));
        title.setText(item.getItem());
        amount.setText(DateFormatUtils.getDecimalFormat().format(item.getAmount()));

        memo.setVisibility(View.GONE);
        total.setText("-");

        if (item instanceof Entry) {

            Entry entry = (Entry) item;

            notyet.setVisibility(View.INVISIBLE);
            if (entry.getMemo().isEmpty()) {
                memo.setVisibility(View.GONE);
            } else {
                memo.setText(entry.getMemo());
                memo.setTextColor(context.getResources().getColor(R.color.text_black));
                memo.setVisibility(View.VISIBLE);
            }
            total.setText(DateFormatUtils.getDecimalFormat().format(entry.getBalance()));

            date.setTextColor(context.getResources().getColor(R.color.text_basic));
            title.setTextColor(context.getResources().getColor(R.color.text_black));
            amount.setTextColor(context.getResources().getColor(R.color.text_black));
        } else {
            /*
			background.setBackgroundResource(R.drawable.gray_box);
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
			params.setMargins(Utils.getDPSize(3), Utils.getDPSize(3), Utils.getDPSize(3), Utils.getDPSize(3));
			background.setLayoutParams(params);
			*/
            notyet.setVisibility(View.VISIBLE);
            if (item.getDate() < Calendar.getInstance().getTimeInMillis()) {
                date.setTextColor(context.getResources().getColor(R.color.text_red));
            } else {
                date.setTextColor(context.getResources().getColor(R.color.text_light_dimmed));
            }
            title.setTextColor(context.getResources().getColor(R.color.text_light_dimmed));
            amount.setTextColor(context.getResources().getColor(R.color.text_light_dimmed));

        }
		
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

        return true;
    }

    public void setBackgroundAccountWidget(TextView tv, String account) {

        if (account.isEmpty()) {
            Log.e(LOG_TAG, "fatal, account string is empty!!!");
            return;
        }

        int bgColorResId;
        int textColorResId;

        switch (account.charAt(0)) {
            case 'c': // capital
                bgColorResId = R.color.md_theme_surface_container_high;
                textColorResId = R.color.md_theme_on_surface;
                break;
            case 'e': // expenses
                bgColorResId = R.color.md_theme_error_container;
                textColorResId = R.color.md_theme_on_error_container;
                break;
            case 'a': // assets
                bgColorResId = R.color.md_theme_primary_container;
                textColorResId = R.color.md_theme_on_primary_container;
                break;
            case 'l': // liabilities
                bgColorResId = R.color.md_theme_warning_container;
                textColorResId = R.color.md_theme_on_warning_container;
                break;
            case 'i': // income
                bgColorResId = R.color.md_theme_success_container;
                textColorResId = R.color.md_theme_on_success_container;
                break;
            default:
                return;
        }

        tv.setBackgroundResource(bgColorResId);
        tv.setTextColor(context.getResources().getColor(textColorResId));
        
        Drawable background = tv.getBackground();
        if (background != null) {
            background.setAlpha(200);
        }
    }

    public void clear() {
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
