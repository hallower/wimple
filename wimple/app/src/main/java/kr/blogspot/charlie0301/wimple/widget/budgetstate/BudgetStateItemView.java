package kr.blogspot.charlie0301.wimple.widget.budgetstate;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import kr.blogspot.charlie0301.wimple.R;
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.wimple.model.AccountState;
import kr.blogspot.charlie0301.wimple.model.Budget;

public class BudgetStateItemView extends LinearLayout {

    private LinearLayout llBackground = null;
    private TextView budget = null;
    private TextView title = null;
    private TextView percentage = null;
    private TextView amount = null;

    public BudgetStateItemView(Context context) {
        super(context);

        // Layout Inflation
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.list_budget_state, this, true);

        llBackground = (LinearLayout) findViewById(R.id.as_background);
        budget = (TextView) findViewById(R.id.as_item_budget);
        title = (TextView) findViewById(R.id.as_item_title);
        percentage = (TextView) findViewById(R.id.as_item_percentage);
        amount = (TextView) findViewById(R.id.as_item_amount);
    }

    public BudgetStateItemView(Context context, AccountState item, Budget budget) {
        this(context);
        setData(item, budget);
    }

    public boolean setData(AccountState item) {
        return setData(item, null);
    }

    @SuppressWarnings("deprecation")
    public boolean setData(AccountState item, Budget budget) {

        if (null == title)
            return false;

        title.setText(item.getAccountName());
        amount.setText(DateFormatUtils.getDecimalFormat().format(item.getAmount()));
        amount.setTextColor(getResources().getColor(R.color.md_theme_on_surface));
        setBackgroundAccountWidget(title, item.getCategory());

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

        if (item.getGroup()) {
            //type.setText(context.getResources().getString(R.string.title_group));
            //type.setTextColor(context.getResources().getColor(R.color.text_black));
            title.setBackgroundResource(R.color.md_theme_primary);
            title.setTextColor(getResources().getColor(R.color.md_theme_on_primary));
            amount.setTypeface(null, Typeface.BOLD);
            layoutParams.setMargins(0, 0, 0, 0);
        } else {
            amount.setTypeface(null, Typeface.NORMAL);
            layoutParams.setMargins(30, 0, 0, 0);
        }
        llBackground.setLayoutParams(layoutParams);

        if (null == budget) {
            return true;
        }

        amount.setTextColor(getResources().getColor(R.color.md_theme_on_surface));
        percentage.setTextColor(getResources().getColor(R.color.md_theme_on_surface));

        if (budget.getBudget() > 0) {

            if (item.getAmount() > budget.getBudget()) {
                if (item.getCategory().startsWith("in")) {
                    amount.setTextColor(getResources().getColor(R.color.md_theme_primary));
                    percentage.setTextColor(getResources().getColor(R.color.md_theme_primary));
                } else {
                    amount.setTextColor(getResources().getColor(R.color.md_theme_error));
                    percentage.setTextColor(getResources().getColor(R.color.md_theme_error));
                }
            }

            this.budget.setText(DateFormatUtils.getDecimalFormat().format(budget.getBudget()));
            int pc = (int) ((item.getAmount() / budget.getBudget()) * 100);
            this.percentage.setText(String.valueOf(pc) + "%");
        } else {
            this.budget.setText(getResources().getString(R.string.budget_nothing_budget));
            this.percentage.setText(getResources().getString(R.string.budget_nothing_budget));
        }
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
            case 'l': // liabilities
                bgColorResId = R.color.md_theme_warning_container;
                textColorResId = R.color.md_theme_on_warning_container;
                break;
            case 'i': // income
                bgColorResId = R.color.md_theme_success_container;
                textColorResId = R.color.md_theme_on_success_container;
                break;
            case 'e': // expenses
                bgColorResId = R.color.md_theme_error_container;
                textColorResId = R.color.md_theme_on_error_container;
                break;
            default:
                tv.setBackgroundResource(R.drawable.progress_n);
                return;
        }

        tv.setBackgroundResource(bgColorResId);
        tv.setTextColor(getResources().getColor(textColorResId));

        Drawable background = tv.getBackground();
        if (background != null) {
            background.setAlpha(200);
        }
    }

    public void clear() {
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
