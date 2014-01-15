package me.blog.imhallower.wimple.model;

import me.blog.imhallower.wimple.impl.db.IDatabaseRecord;

import org.json.simple.JSONObject;

import android.util.SparseArray;

public class Section implements IDatabaseRecord {

	private String id;
	private String title;
	private String description;
	private String currency;
	private Boolean isolation = false;
	private Double asset;
	private Double debt;
	private Integer skinID;
	private Integer decimalPosition;
	private String dateFormat;

	private static final SparseArray<String> columns = new SparseArray<String>();    
    
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
	
	public Section(String id, String title, String description,
			String currency, Double asset, Double debt,
			Integer skinID, Integer decimalPosition, String dateFormat) {
		super();
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

		this(section.get("section_id").toString(), section.get("title").toString(), section.get("memo").toString(), 
				section.get("currency").toString(), 
				Double.valueOf(section.get("total_assets").toString()), 
				Double.valueOf(section.get("total_liabilities").toString()), 
				Integer.valueOf(section.get("skin_id").toString()), 
				Integer.valueOf(section.get("decimal_places").toString()), 
				section.get("date_format").toString());
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
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getValue(int columnID) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SparseArray<String> getValues() {
		// TODO Auto-generated method stub
		return null;
	}
}
