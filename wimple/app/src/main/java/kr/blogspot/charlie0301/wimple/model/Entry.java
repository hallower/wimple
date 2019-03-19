package kr.blogspot.charlie0301.wimple.model;

import android.util.Log;
import android.util.SparseArray;

import org.json.JSONObject;

import java.util.Date;

import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils;

public class Entry extends Item {

    private final static String LOG_TAG = "Section";

    private Double balance = 0.0;        // optional
    private String memo;
    private String appID;

    public static final SparseArray<String> columns = new SparseArray<>();

    static {
        columns.append(0, "id");
        columns.append(1, "date");
        columns.append(2, "leftAccount");
        columns.append(3, "leftAccountID");
        columns.append(4, "rightAccount");
        columns.append(5, "rightAccountID");
        columns.append(6, "item");
        columns.append(7, "amount");
        columns.append(8, "balance");
        columns.append(9, "memo");
        columns.append(10, "appID");
        columns.append(11, "dateValue");
    }

    // Only for the database item inserting.
    public Entry() {
        super();

        balance = 0.0;        // optional
        memo = "";
        appID = "";
    }

    public Entry(String id, String leftAccount,
                 String leftAccountID, String rightAccount, String rightAccountID,
                 String item, Double amount, String memo, String appID) {

        super(id, leftAccount, leftAccountID,
                rightAccount, rightAccountID, item, amount);

        this.memo = memo;
        this.appID = appID;
    }


    public Entry(JSONObject entry) {
/*
        this(entry.get("entry_id").toString(), entry.get("l_account").toString(),
				entry.get("l_account_id").toString(), entry.get("r_account").toString(), entry.get("r_account_id").toString(), 
				entry.get("item").toString(), Double.valueOf(entry.get("money").toString()), entry.get("memo").toString(), entry.get("app_id").toString());
*/
        try {
            this.id = entry.getString("entry_id");
            this.leftAccount = entry.getString("l_account");
            this.leftAccountID = entry.getString("l_account_id");
            this.rightAccount = entry.getString("r_account");
            this.rightAccountID = entry.getString("r_account_id");
            this.item = entry.getString("item");
            this.amount = Double.valueOf(entry.getString("money"));
            this.memo = entry.getString("memo");
            this.appID = entry.getString("app_id");


            Long dateLong;

            String dateString = entry.get("entry_date").toString();
            setDateValue("7" + dateString);
            int pos = dateString.indexOf(".");
            if (pos > 0) {
                dateString = dateString.substring(0, pos);
            }

            Date date = DateFormatUtils.getServerDateFormat().parse(dateString);
            String convertedDateString = DateFormatUtils.getServerDateFormat().format(date);
            if (convertedDateString.compareTo(dateString) != 0) {
                Log.e(LOG_TAG, "reconverted is not match " + dateString + " != " + convertedDateString);
            }

            dateLong = date.getTime();
            setDate(dateLong);

        } catch (Exception e) {
            e.printStackTrace();
            Log.e(LOG_TAG, "entry_date is invalid!!! => " + (entry.has("entry_date") ? entry.opt("entry_date").toString() : ""));
        }

    }

    public Double getBalance() {
        return balance;
    }


    public String getMemo() {
        return memo;
    }


    public String getAppID() {
        return appID;
    }

    public void setBalance(String balance) {
        this.balance = Double.valueOf(balance);
    }

    public void setBalance(Double balance) {
        this.balance = balance;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("\n-[Section : ").append(getId()).append(" ]------------------------------");
        sb.append("\n   date = ").append(getDate());
        sb.append("\n   dateValue = ").append(getDateValue());
        sb.append("\n   leftAccount = ").append(getLeftAccount());
        sb.append("\n   leftAccountID = ").append(getLeftAccountID());
        sb.append("\n   rightAccount = ").append(getRightAccount());
        sb.append("\n   rightAccountID = ").append(getRightAccountID());
        sb.append("\n   amount = ").append(getAmount());
        sb.append("\n   balance = ").append(balance);
        sb.append("\n   memo = ").append(memo);
        sb.append("\n   appID = ").append(appID);
        sb.append("\n---------------------------------------------------------------------");

        return sb.toString();
    }


    @Override
    public boolean equals(Object o) {

        if (!(o instanceof Entry)) {
            return false;
        }

        return (0 == getId().compareTo(((Entry) o).getId()));
    }

    @Override
    public SparseArray<String> getColumns() {
        return columns;
    }

    @Override
    public boolean setValues(SparseArray<String> values) {
        int key;
        String value;

        for (int i = 0; i < values.size(); i++) {
            key = values.keyAt(i);
            value = values.get(key);

            switch (key) {
                case 0:
                    setId(value);
                    break;
                case 1:
                    setDate(Long.parseLong(value));
                    break;
                case 2:
                    setLeftAccount(value);
                    break;
                case 3:
                    setLeftAccountID(value);
                    break;
                case 4:
                    setRightAccount(value);
                    break;
                case 5:
                    setRightAccountID(value);
                    break;
                case 6:
                    setItem(value);
                    break;
                case 7:
                    setAmount(Double.parseDouble(value));
                    break;
                case 8:
                    this.balance = Double.parseDouble(value);
                    break;
                case 9:
                    this.memo = value;
                    break;
                case 10:
                    this.appID = value;
                    break;
                case 11:
                    setDateValue(value);
                    break;
                default:
                    Log.e(LOG_TAG, "Invalid columnID!!!");
                    break;
            }
        }

        return true;
    }

    @Override
    public String getValue(int columnID) {
        switch (columnID) {
            case 0:
                return getId();
            case 1:
                return getDate().toString();
            case 2:
                return getLeftAccount();
            case 3:
                return getLeftAccountID();
            case 4:
                return getRightAccount();
            case 5:
                return getRightAccountID();
            case 6:
                return getItem();
            case 7:
                return getAmount().toString();
            case 8:
                return balance.toString();
            case 9:
                return memo;
            case 10:
                return appID;
            case 11:
                return getDateValue();
            default:
                Log.e(LOG_TAG, "Invalid columnID!!!");
                break;
        }
        return "";
    }

    @Override
    public SparseArray<String> getValues() {
        SparseArray<String> values = new SparseArray<>();

        values.append(0, getId());
        values.append(1, getDate().toString());
        values.append(2, getLeftAccount());
        values.append(3, getLeftAccountID());
        values.append(4, getRightAccount());
        values.append(5, getRightAccountID());
        values.append(6, getItem());
        values.append(7, getAmount().toString());
        values.append(8, balance.toString());
        values.append(9, memo);
        values.append(10, appID);
        values.append(11, getDateValue());

        return values;
    }

}
