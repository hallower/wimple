package kr.blogspot.charlie0301.wimple;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import kr.blogspot.charlie0301.wimple.WimpleActivity.CommandID;
import kr.blogspot.charlie0301.wimple.impl.WimpleImpl;
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils;
import kr.blogspot.charlie0301.wimple.impl.util.ImageUtils;
import kr.blogspot.charlie0301.wimple.model.AccountState;
import kr.blogspot.charlie0301.wimple.model.Budget;

public class IncomeExpenseSummaryFragment extends Fragment implements IWimpleFragment {

    private final static String LOG_TAG = "IncomeExpenseSumFrag";

    private final WimpleImpl wimple = WimpleImpl.getInstance();
    //private WimpleActivity mainActivity = null;
    private Context context;

    // GUI
    //private WeakReference<ItemListView> asList;
    //private WeakReference<AccountStateItemListAdapter> asAdapter;
    private ImageView ivIncomeBar;
    private ImageView ivExpenseBar;
    private ImageView ivIncomeBudgetBase;
    private ImageView ivIncomeBudgetCurrent;
    private TextView ivIncomeBudgetCurrentPercentage;
    private ImageView ivExpenseBudgetBase;
    private ImageView ivExpenseBudgetCurrent;
    private TextView ivExpenseBudgetCurrentPercentage;

    private LinearLayout llUpdateNotice;
    private TextView tvIncomeValue;
    private TextView tvExpenseValue;
    private TextView tvSumValue;

    // Data
    private boolean firstUpdate;
    private boolean isUsingBudgetInformation;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        context = WimpleActivity.context.get();

        View view = inflater.inflate(R.layout.fragment_income_expense_summary_tab, container, false);

        ivIncomeBar = (ImageView) view.findViewById(R.id.ine_bar_income);
        ivExpenseBar = (ImageView) view.findViewById(R.id.ine_bar_expense);
        ivIncomeBudgetBase = (ImageView) view.findViewById(R.id.ine_bar_budget_base_income);
        ivIncomeBudgetCurrent = (ImageView) view.findViewById(R.id.ine_bar_budget_current_income);
        ivIncomeBudgetCurrentPercentage = (TextView) view.findViewById(R.id.ine_bar_budget_current_income_percentage);
        ivExpenseBudgetBase = (ImageView) view.findViewById(R.id.ine_bar_budget_base_expense);
        ivExpenseBudgetCurrent = (ImageView) view.findViewById(R.id.ine_bar_budget_current_expense);
        ivExpenseBudgetCurrentPercentage = (TextView) view.findViewById(R.id.ine_bar_budget_current_expense_percentage);


        tvSumValue = (TextView) view.findViewById(R.id.ine_sum_value);
        tvIncomeValue = (TextView) view.findViewById(R.id.ine_income_value);
        tvExpenseValue = (TextView) view.findViewById(R.id.ine_expense_value);

        llUpdateNotice = (LinearLayout) view.findViewById(R.id.ine_update_notification);

        firstUpdate = true;

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        isUsingBudgetInformation = sharedPref.getBoolean(SettingsFragment.KEY_INCOME_EXPENSE_ENABLE_BUDGET, true);

        if (!isUsingBudgetInformation) {
            view.findViewById(R.id.ine_budget_status_title).setVisibility(View.GONE);
            view.findViewById(R.id.ine_budget_status_income).setVisibility(View.GONE);
            view.findViewById(R.id.ine_budget_status_expense).setVisibility(View.GONE);

        }

        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        c.set(Calendar.DATE, 1);

        wimple.getIncomeAndExpense(DateFormatUtils.getServerDateString(c.getTimeInMillis()), DateFormatUtils.getServerDateString(""), false);
        if (isUsingBudgetInformation) {
            wimple.getBudget(true, DateFormatUtils.getServerDateString(c.getTimeInMillis()), DateFormatUtils.getServerDateString(""), false);
            wimple.getBudget(false, DateFormatUtils.getServerDateString(c.getTimeInMillis()), DateFormatUtils.getServerDateString(""), false);
        }


        view.findViewById(R.id.ine_refresh).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Calendar c = Calendar.getInstance();
                c.setTime(new Date());
                c.set(Calendar.DATE, 1);

                wimple.getIncomeAndExpense(DateFormatUtils.getServerDateString(c.getTimeInMillis()), DateFormatUtils.getServerDateString(""), true);
                if (isUsingBudgetInformation) {
                    wimple.getBudget(true, DateFormatUtils.getServerDateString(c.getTimeInMillis()), DateFormatUtils.getServerDateString(""), true);
                    wimple.getBudget(false, DateFormatUtils.getServerDateString(c.getTimeInMillis()), DateFormatUtils.getServerDateString(""), true);
                }

                llUpdateNotice.setVisibility(View.VISIBLE);
            }
        });

        return view;
    }

    @Override
    public void onDestroy() {

        ivIncomeBar = null;
        ivExpenseBar = null;
        tvIncomeValue = null;
        tvExpenseValue = null;
        tvSumValue = null;

        super.onDestroy();
    }

    @Override
    public void onResume() {
        context = WimpleActivity.context.get();
        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        c.set(Calendar.DATE, 1);

        wimple.getIncomeAndExpense(DateFormatUtils.getServerDateString(c.getTimeInMillis()), DateFormatUtils.getServerDateString(""), false);
        if (isUsingBudgetInformation) {
            wimple.getBudget(true, DateFormatUtils.getServerDateString(c.getTimeInMillis()), DateFormatUtils.getServerDateString(""), false);
            wimple.getBudget(false, DateFormatUtils.getServerDateString(c.getTimeInMillis()), DateFormatUtils.getServerDateString(""), false);
        }

        super.onResume();
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    @Override
    public void handleMessage(Message msg) {
        int command = msg.what;
        boolean booleanStatus = msg.arg1 == 1;
        Object obj = msg.obj;

        // if fragment is added or not to the activity
        if (!isAdded()) {
            return;
        }

        if (null == context) {
            context = WimpleActivity.context.get();
            if (null == context) {
                return;
            }
        }

        switch (command) {

            case CommandID.GET_INCOME_AND_EXPENSE_RESPONSE_RECEIVED: {

                llUpdateNotice.setVisibility(View.GONE);

                if (firstUpdate) {
                    firstUpdate = false;
                    // To show previous data during new data dispatching without any GUI display delay.
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
                    boolean autoRefresh = sharedPref.getBoolean(SettingsFragment.KEY_INCOME_EXPENSE_STATE_AUTO_REFRESH, true);
                    if (autoRefresh) {
                        Calendar c = Calendar.getInstance();
                        c.setTime(new Date());
                        c.set(Calendar.DATE, 1);
                        wimple.getIncomeAndExpense(DateFormatUtils.getServerDateString(c.getTimeInMillis()), DateFormatUtils.getServerDateString(""), true);
                        if (isUsingBudgetInformation) {
                            wimple.getBudget(true, DateFormatUtils.getServerDateString(c.getTimeInMillis()), DateFormatUtils.getServerDateString(""), true);
                            wimple.getBudget(false, DateFormatUtils.getServerDateString(c.getTimeInMillis()), DateFormatUtils.getServerDateString(""), true);
                        }

                        llUpdateNotice.setVisibility(View.VISIBLE);
                    }
                }

                if (!booleanStatus) {
                    return;
                }

                Double income = 0.0;
                Double expense = 0.0;

                Collection<AccountState> accountStates = (Collection<AccountState>) obj;
                for (AccountState as : accountStates) {

                    if (!as.getGroup()) {
                        if (as.getCategory().startsWith("ex")) {
                            expense += as.getAmount();
                        }
                        if (as.getCategory().startsWith("in")) {
                            income += as.getAmount();
                        }
                    }
                }
                //asAdapter.get().notifyDataSetChanged();

                tvIncomeValue.setText(DateFormatUtils.getNoPointDecimalFormat().format(income));
                tvExpenseValue.setText(DateFormatUtils.getNoPointDecimalFormat().format(-expense));

                Double sum = income - expense;

                tvSumValue.setText(DateFormatUtils.getNoPointDecimalFormat().format(sum));
                if (sum >= 0) {
                    tvSumValue.setTextColor(getResources().getColor(R.color.text_blue));
                } else {
                    tvSumValue.setTextColor(getResources().getColor(R.color.text_red));
                }

                WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                Display display = wm.getDefaultDisplay();
                Point size = new Point();
                display.getSize(size);
                int width = size.x - ImageUtils.getDPSize(100);

                Log.d(LOG_TAG, "width = " + width + ", expense = " + expense + ", income = " + income);

                FrameLayout.LayoutParams params;

                if (income > expense) {
                    Log.d(LOG_TAG, "expense / income = " + (expense / (double) income));

                    params = (FrameLayout.LayoutParams) ivIncomeBar.getLayoutParams();
                    params.width = width;
                    ivIncomeBar.setLayoutParams(params);

                    width = (int) (width * (expense / (double) income));

                    params = (FrameLayout.LayoutParams) ivExpenseBar.getLayoutParams();
                    params.width = width;
                    ivExpenseBar.setLayoutParams(params);

                } else {
                    Log.d(LOG_TAG, "income / expense = " + (income / (double) expense));

                    params = (FrameLayout.LayoutParams) ivExpenseBar.getLayoutParams();
                    params.width = width;
                    ivExpenseBar.setLayoutParams(params);

                    width = (int) (width * (income / (double) expense));

                    params = (FrameLayout.LayoutParams) ivIncomeBar.getLayoutParams();
                    params.width = width;
                    ivIncomeBar.setLayoutParams(params);
                }


            }
            break;

            case CommandID.GET_BUDGET_RESPONSE_RECEIVED: {

                if (!booleanStatus) {
                    return;
                }

                if (!isUsingBudgetInformation) {
                    return;
                }

                boolean isIncome = (msg.arg2 == 1);

                Map<String, Budget> map = (Map<String, Budget>) obj;
                Budget budgetStatus;
                Double current;
                Double budget;

                try {
                    budgetStatus = map.get(Budget.SUMMARYACCOUNTID);
                } catch (Exception e) {
                    Log.d(LOG_TAG, "oops no budget summary!!!");
                    return;
                }

                if (null == budgetStatus) {
                    Log.d(LOG_TAG, "oops no budget summary!!!");
                    return;
                }

                current = budgetStatus.getCurrent();
                budget = budgetStatus.getBudget();

                Log.d(LOG_TAG, "current=" + current + ", budget=" + budget);

                WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                Display display = wm.getDefaultDisplay();
                Point size = new Point();
                display.getSize(size);
                int width = size.x - ImageUtils.getDPSize(130);

                FrameLayout.LayoutParams params;

                if (0 == budget) {

                    if (isIncome) {
                        params = (FrameLayout.LayoutParams) ivIncomeBudgetBase.getLayoutParams();
                        params.width = width;
                        ivIncomeBudgetBase.setLayoutParams(params);

                        params = (FrameLayout.LayoutParams) ivIncomeBudgetCurrent.getLayoutParams();
                        params.width = 0;
                        ivIncomeBudgetCurrent.setLayoutParams(params);

                        ivIncomeBudgetCurrentPercentage.setText(getResources().getString(R.string.budget_not_yet));

                    } else {
                        params = (FrameLayout.LayoutParams) ivExpenseBudgetBase.getLayoutParams();
                        params.width = width;
                        ivExpenseBudgetBase.setLayoutParams(params);

                        params = (FrameLayout.LayoutParams) ivExpenseBudgetCurrent.getLayoutParams();
                        params.width = 0;
                        ivExpenseBudgetCurrent.setLayoutParams(params);

                        ivExpenseBudgetCurrentPercentage.setText(getResources().getString(R.string.budget_not_yet));
                    }

                } else if (budget > current) {
                    if (isIncome) {
                        params = (FrameLayout.LayoutParams) ivIncomeBudgetBase.getLayoutParams();
                        params.width = width;
                        ivIncomeBudgetBase.setLayoutParams(params);
                    } else {
                        params = (FrameLayout.LayoutParams) ivExpenseBudgetBase.getLayoutParams();
                        params.width = width;
                        ivExpenseBudgetBase.setLayoutParams(params);
                    }

                    int percentage = (int) ((current / (double) budget) * 100);
                    Log.d(LOG_TAG, "current / budget = " + ((current / (double) budget)) * 100);
                    width = (int) (width * current / budget);

                    if (isIncome) {
                        params = (FrameLayout.LayoutParams) ivIncomeBudgetCurrent.getLayoutParams();
                        params.width = width;
                        ivIncomeBudgetCurrent.setLayoutParams(params);
                        ivIncomeBudgetCurrentPercentage.setText("" + percentage + "%");
                    } else {
                        params = (FrameLayout.LayoutParams) ivExpenseBudgetCurrent.getLayoutParams();
                        params.width = width;
                        ivExpenseBudgetCurrent.setLayoutParams(params);
                        ivExpenseBudgetCurrentPercentage.setText("" + percentage + "%");
                    }
                } else {

                    if (isIncome) {
                        params = (FrameLayout.LayoutParams) ivIncomeBudgetCurrent.getLayoutParams();
                        params.width = width;
                        ivIncomeBudgetCurrent.setLayoutParams(params);
                    } else {
                        params = (FrameLayout.LayoutParams) ivExpenseBudgetCurrent.getLayoutParams();
                        params.width = width;
                        ivExpenseBudgetCurrent.setLayoutParams(params);
                    }

                    Log.d(LOG_TAG, "current / budget = " + (current / (double) budget));
                    int percentage = (int) ((current / (double) budget) * 100);
                    //width = (int)(width * ((double)budget/ (double)current));

                    if (isIncome) {
                        params = (FrameLayout.LayoutParams) ivIncomeBudgetBase.getLayoutParams();
                        params.width = width;
                        ivIncomeBudgetBase.setLayoutParams(params);
                        ivIncomeBudgetCurrentPercentage.setText("" + percentage + "%");
                    } else {
                        params = (FrameLayout.LayoutParams) ivExpenseBudgetBase.getLayoutParams();
                        params.width = width;
                        ivExpenseBudgetBase.setLayoutParams(params);
                        ivExpenseBudgetCurrentPercentage.setText("" + percentage + "%");
                    }
                }
            }
            break;
        }
    }

    @Override
    public void setActivityInstance(WimpleActivity instance) {
    }

}
