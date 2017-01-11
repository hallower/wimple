package kr.blogspot.charlie0301.wimple.model;

import kr.blogspot.charlie0301.wimple.impl.db.IDatabaseRecord;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;
import android.util.SparseArray;

public class Section implements IDatabaseRecord {

	private final static String LOG_TAG = "Section";

	private String id;
	private String title;
	private String description;
	private String currency;
	private Boolean isolation = false;		// optional
	private Double asset;
	private Double debt;
	private Integer skinID;
	private Integer decimalPosition;
	private String dateFormat;

	public static final SparseArray<String> columns = new SparseArray<>();

	static {
		columns.append(0, "id");
		columns.append(1, "title");
		columns.append(2, "description");
		columns.append(3, "currency");
		columns.append(4, "isolation");
		columns.append(5, "asset");
		columns.append(6, "debt");
		columns.append(7, "skinid");
		columns.append(8, "decimalposition");
		columns.append(9, "dateformat");
	}

	// Only for the database item inserting.
	public Section(){
		super();

		id = "";
		title = "";
		description = "";
		currency = "";
		isolation = false;
		asset = 0.0;
		debt = 0.0;
		skinID = 0;
		decimalPosition = 0;
		dateFormat = "";
	}

	public Section(String id, String title, String description,
			String currency, Double asset, Double debt,
			Integer skinID, Integer decimalPosition, String dateFormat) {
		this();

		this.id = id;
		this.title = title;
		this.description = description;
		this.currency = currency;
		this.asset = asset;
		this.debt = debt;
		this.skinID = skinID;
		this.decimalPosition = decimalPosition;
		this.dateFormat = dateFormat;
	}

	public Section(JSONObject section) {
		this(section.optString("section_id"), section.optString("title"), section.optString("memo"),
				section.optString("currency"),
				Double.valueOf(section.optString("total_assets")),
				Double.valueOf(section.optString("total_liabilities")),
				Integer.valueOf(section.optString("skin_id")),
				Integer.valueOf(section.optString("decimal_places")),
				section.optString("date_format"));
	}

	public String getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getCurrency() {
		return currency;
	}

	public Boolean getIsolation() {
		return isolation;
	}

	public Double getAsset() {
		return asset;
	}

	public Double getDebt() {
		return debt;
	}

	public int getSkinID() {
		return skinID;
	}

	public int getDecimalPosition() {
		return decimalPosition;
	}

	public String getDateFormat() {
		return dateFormat;
	}




	public void setId(String id) {
		this.id = id;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public void setIsolation(Boolean isolation) {
		this.isolation = isolation;
	}

	public void setAsset(Double asset) {
		this.asset = asset;
	}

	public void setDebt(Double debt) {
		this.debt = debt;
	}

	public void setSkinID(Integer skinID) {
		this.skinID = skinID;
	}

	public void setDecimalPosition(Integer decimalPosition) {
		this.decimalPosition = decimalPosition;
	}

	public void setDateFormat(String dateFormat) {
		this.dateFormat = dateFormat;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("\n-[Section : " + id + " ]------------------------------");
		sb.append("\n   title = " + title);
		sb.append("\n   description = " + description);
		sb.append("\n   openedDate = " + currency);
		sb.append("\n   closedDate = " + isolation);
		sb.append("\n   category = " + asset);
		sb.append("\n   category = " + debt);
		sb.append("\n   category = " + skinID);
		sb.append("\n   category = " + decimalPosition);
		sb.append("\n   category = " + dateFormat);
		sb.append("\n---------------------------------------------------------------------");

		return sb.toString();
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

		for(int i = 0; i < values.size() ; i++){
			key = values.keyAt(i);
			value = values.get(key);

			switch(key){
			case 0 :
				this.id = value;
				break;
			case 1 :
				this.title = value;
				break;
			case 2 :
				this.description = value;
				break;
			case 3 :
				this.currency = value;
				break;
			case 4 :
				this.isolation  = Boolean.parseBoolean(value);
				break;
			case 5 :
				this.asset = Double.parseDouble(value);
				break;
			case 6 :
				this.debt = Double.parseDouble(value);
				break;
			case 7 :
				this.skinID = Integer.parseInt(value);
				break;
			case 8 :
				this.decimalPosition = Integer.parseInt(value);
				break;
			case 9 :
				this.dateFormat = value;
				break;

			default :
				Log.e(LOG_TAG, "Invalid columnID!!!");
				break;
			}			
		}		

		return true;
	}

	@Override
	public String getValue(int columnID) {
		switch(columnID){
		case 0 :
			return id;
		case 1 :
			return title;
		case 2 :
			return description;
		case 3 :
			return currency;
		case 4 :
			return isolation.toString();
		case 5 :
			return asset.toString();
		case 6 :
			return debt.toString();
		case 7 :
			return skinID.toString();
		case 8 :
			return decimalPosition.toString();
		case 9 :
			return dateFormat;
		default :
			Log.e(LOG_TAG, "Invalid columnID!!!");
			break;
		}
		return "";
	}

	@Override
	public SparseArray<String> getValues() {
		SparseArray<String> values = new SparseArray<>();

		values.append(0, id);
		values.append(1, title);
		values.append(2, description);
		values.append(3, currency);
		values.append(4, isolation.toString());
		values.append(5, asset.toString());
		values.append(6, debt.toString());
		values.append(7, skinID.toString());
		values.append(8, decimalPosition.toString());
		values.append(9, dateFormat);

		return values;
	}
}
