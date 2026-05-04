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
        amount.setTextColor(androidx.core.content.ContextCompat.getColor(getContext(), R.color.md_theme_on_surface));

        setBackgroundAccountWidget(title, item.getCategory());

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

        if (item.getGroup()) {
            title.setBackgroundResource(R.color.md_theme_primary);
            title.setTextColor(androidx.core.content.ContextCompat.getColor(getContext(), R.color.md_theme_on_primary));
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
        int bgColorResId;
        int textColorResId;

        switch (account.charAt(0)) {
            case 'c': // capital
                bgColorResId = R.color.md_theme_surface_container_high;
                textColorResId = R.color.md_theme_on_surface;
                break;
            case 'a': // assets
                bgColorResId = R.color.md_theme_primary_container;
                textColorResId = R.color.md_theme_on_primary_container;
                break;
            case 'l': // liabilities (Red)
                bgColorResId = R.color.md_theme_liabilities_container;
                textColorResId = R.color.md_theme_on_liabilities_container;
                break;
            case 'i': // income (Green)
                bgColorResId = R.color.md_theme_success_container;
                textColorResId = R.color.md_theme_on_success_container;
                break;
            case 'e': // expenses (Yellow)
                bgColorResId = R.color.md_theme_warning_container;
                textColorResId = R.color.md_theme_on_warning_container;
                break;
            default:
                tv.setBackgroundResource(R.drawable.progress_n);
                return;
        }

        tv.setBackgroundResource(bgColorResId);
        tv.setTextColor(androidx.core.content.ContextCompat.getColor(getContext(), textColorResId));

        Drawable background = tv.getBackground();
        if (background != null) {
            background.setAlpha(200);
        }
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
