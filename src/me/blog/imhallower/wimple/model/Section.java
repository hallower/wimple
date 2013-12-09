package me.blog.imhallower.wimple.model;

import org.json.simple.JSONObject;

public class Section {

	private final String id;
	private final String title;
	private final String description;
	private final String currency;
	private Boolean isolation = false;
	private final Long asset;
	private final Long debt;
	private final Integer skinID;
	private final Integer decimalPosition;
	private final String dateFormat;

	public Section(String id, String title, String description,
			String currency, Long asset, Long debt,
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
				Long.valueOf(section.get("total_assets").toString()), 
				Long.valueOf(section.get("total_liabilities").toString()), 
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

	public Long getAsset() {
		return asset;
	}

	public Long getDebt() {
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

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("-[Section : " + id + " ]------------------------------");
		sb.append("   title = " + title);
		sb.append("   description = " + description);
		sb.append("   openedDate = " + currency);
		sb.append("   closedDate = " + isolation);
		sb.append("   category = " + asset);
		sb.append("   category = " + debt);
		sb.append("   category = " + skinID);
		sb.append("   category = " + decimalPosition);
		sb.append("   category = " + dateFormat);
		sb.append("---------------------------------------------------------------------");

		return sb.toString();
	}
}
