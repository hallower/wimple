package kr.blogspot.charlie0301.wimple.model;

import android.util.Log;
import android.util.SparseArray;

import org.json.JSONObject;

import java.util.Comparator;
import java.util.Date;

import kr.blogspot.charlie0301.wimple.impl.db.IDatabaseRecord;
import kr.blogspot.charlie0301.wimple.impl.util.DateFormatUtils;

public class Item implements IDatabaseRecord {

    private final static String LOG_TAG = "Item";

    protected String id;        // optional
    protected Long date;    // optional
    protected String leftAccount;
    protected String leftAccountID;
    protected String rightAccount;
    protected String rightAccountID;
    protected String item;
    protected Double amount;
    /*
     * dateValue => [prefix]yyyy/MM/dd.[sequence]
     *               120140101.002
     *               prefix, item=9, entry=7
     */
    private String dateValue;

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
        columns.append(8, "dateValue");
    }

    // This must be used only for DB result inserting.
    public Item() {
        super();

        id = "";
        date = 0L;
        leftAccount = "";
        leftAccountID = "";
        rightAccount = "";
        rightAccountID = "";
        item = "";
        amount = 0.0;
        dateValue = "";
    }

    public Item(String id, String leftAccount,
                String leftAccountID, String rightAccount, String rightAccountID,
                String item, Double amount) {
        super();
        this.id = id;
        this.date = 0L;
        this.leftAccount = leftAccount;
        this.leftAccountID = leftAccountID;
        this.rightAccount = rightAccount;
        this.rightAccountID = rightAccountID;
        this.item = item;
        this.amount = amount;
        this.dateValue = "";
    }


    public Item(JSONObject item) {
        this(item.optString("item_id"), item.optString("l_account"), item.optString("l_account_id"),
                item.optString("r_account"), item.optString("r_account_id"),
                item.optString("item"), Double.valueOf(item.optString("money")));

        // Date
        /*
		 * Monthly Item => due_date
		 * Frequent Item => X
		 * Entry => entry_date
		 */
        try {
            Long dateLong;

            String dateString = item.optString("due_date");
            if (dateString.length() == 0)
                return;

            this.dateValue = "9" + dateString;
            int pos = dateString.indexOf(".");
            if (pos > 0) {
                dateString = dateString.substring(0, pos);
            }

            Date date = DateFormatUtils.getServerDateFormat().parse(dateString);
            dateLong = date.getTime();
            setDate(dateLong);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Date conversion failed !!!, " + item.optString("due_date"));
        }

    }

    public String getId() {
        return id;
    }


    public Long getDate() {
        return date;
    }


    public String getLeftAccount() {
        return leftAccount;
    }


    public String getLeftAccountID() {
        return leftAccountID;
    }


    public String getRightAccount() {
        return rightAccount;
    }


    public String getRightAccountID() {
        return rightAccountID;
    }


    public String getItem() {
        return item;
    }


    public Double getAmount() {
        return amount;
    }

    public String getDateValue() {
        return this.dateValue;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setLeftAccount(String leftAccount) {
        this.leftAccount = leftAccount;
    }

    public void setLeftAccountID(String leftAccountID) {
        this.leftAccountID = leftAccountID;
    }

    public void setRightAccount(String rightAccount) {
        this.rightAccount = rightAccount;
    }

    public void setRightAccountID(String rightAccountID) {
        this.rightAccountID = rightAccountID;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public void setDate(Long date) {
        this.date = date;
    }

    public void setDateValue(String dateValue) {
        this.dateValue = dateValue;
    }

    @Override
    public String toString() {
		/*
		StringBuilder sb = new StringBuilder();

		sb.append("\n-[Item : " + id + " ]------------------------------");
		sb.append("\n   date = " + date);
		sb.append("\n   leftAccount = " + leftAccount);
		sb.append("\n   leftAccountID = " + leftAccountID);
		sb.append("\n   rightAccount = " + rightAccount);
		sb.append("\n   rightAccountID = " + rightAccountID);
		sb.append("\n   amount = " + amount);
		sb.append("\n---------------------------------------------------------------------");

		return sb.toString();
		 */
        return getItem();
    }

    @Override
    public String getKeyValue() {
        return this.id;
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
                    this.id = value;
                    break;
                case 1:
                    try {
                        this.date = Long.parseLong(value);
                    } catch (Exception ignored) {
                    }
                    break;
                case 2:
                    this.leftAccount = value;
                    break;
                case 3:
                    this.leftAccountID = value;
                    break;
                case 4:
                    this.rightAccount = value;
                    break;
                case 5:
                    this.rightAccountID = value;
                    break;
                case 6:
                    this.item = value;
                    break;
                case 7:
                    this.amount = Double.parseDouble(value);
                    break;
                case 8:
                    this.dateValue = value;
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
                return id;
            case 1:
                return date.toString();
            case 2:
                return leftAccount;
            case 3:
                return leftAccountID;
            case 4:
                return rightAccount;
            case 5:
                return rightAccountID;
            case 6:
                return item;
            case 7:
                return amount.toString();
            case 8:
                return dateValue;

            default:
                Log.e(LOG_TAG, "Invalid columnID!!!");
                break;
        }
        return "";
    }

    @Override
    public SparseArray<String> getValues() {
        SparseArray<String> values = new SparseArray<>();

        values.append(0, id);
        values.append(1, date.toString());
        values.append(2, leftAccount);
        values.append(3, leftAccountID);
        values.append(4, rightAccount);
        values.append(5, rightAccountID);
        values.append(6, item);
        values.append(7, amount.toString());
        values.append(8, dateValue);

        return values;
    }


    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Item)) {
            return false;
        }

        Item item = (Item) o;
        if (0 != item.getItem().compareTo(this.item))
            return false;
        return (0 == item.getDateValue().compareTo(this.dateValue));
    }

    public static class DateDescCompare implements Comparator<Item> {

        @Override
        public int compare(Item lhs, Item rhs) {
            return -1 * lhs.getDateValue().compareTo(rhs.getDateValue());
        }

    }

    public static class DateAscCompare implements Comparator<Item> {

        @Override
        public int compare(Item lhs, Item rhs) {
            return lhs.getDateValue().compareTo(rhs.getDateValue());
        }

    }
}
