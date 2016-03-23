package com.blogspot.charlie0301.model;

import com.blogspot.charlie0301.impl.db.IDatabaseRecord;

import org.json.simple.JSONObject;

import android.util.Log;
import android.util.SparseArray;

public class UserInfo implements IDatabaseRecord {

	private static String LOG_TAG = "UserInfo";
	/*
	 *  [results] => Array
        (
            [user_id] => 20169
            [username] => Hallo
            [email] => hallower@gmail.com
            [new_email] => 
            [level] => 1
            [last_ip] => 110.70.49.19
            [last_login_timestamp] => 1391738068
            [created_timestamp] => 1386322731
            [modified_timestamp] => 1388400755
            [language] => ko
            [expire] => 0
            [timezone] => Asia/Seoul
            [currency] => KRW
            [country] => KR
            [image] => 0
            [twitter_id] => 
            [mileage] => 1085
            [money] => 0
            [adv] => n
            [rmail] => y
            [sound] => y
            [image_url] => https://s3-ap-northeast-1.amazonaws.com/whooingprofile/p0.jpg
        )
	 */

	private String id;
	private String name;
	private Long joinDate;
	private String userImgURL;
	private Integer mileage;
	private Integer apiCountLevel;


	public static final SparseArray<String> columns = new SparseArray<String>();

	static {
		columns.append(0, "id");
		columns.append(1,  "name");
		columns.append(2,  "join_date");
		columns.append(3,  "profile_image_url");
		columns.append(4,  "mileage");
		columns.append(5,  "api_count_level");
	}


	public UserInfo(){
		super();
	}

	public UserInfo(String id, String name, Long joinDate,
			String userImgURL, Integer mileage, Integer level) {
		super();
		this.id = id;
		this.name = name;
		this.joinDate = joinDate;
		this.userImgURL = userImgURL;
		this.mileage = mileage;
		this.apiCountLevel = level;
	}

	public UserInfo(JSONObject json) {

		this(json.get("user_id").toString(), json.get("username").toString(), 
				Long.valueOf(json.get("created_timestamp").toString()), 
				json.get("image_url").toString(),
				Integer.valueOf(json.get("mileage").toString()),
				Integer.valueOf(json.get("level").toString()));
	}

	public String getID() {
		return id;
	}

	public void setID(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Long getJoinDate() {
		return joinDate;
	}

	public void setJoinDate(Long joinDate) {
		this.joinDate = joinDate;
	}

	public String getUserImgURL() {
		return userImgURL;
	}

	public void setUserImgURL(String userImgURL) {
		this.userImgURL = userImgURL;
	}

	public Integer getMileage() {
		return mileage;
	}

	public void setMileage(Integer mileage) {
		this.mileage = mileage;
	}
	
	public Integer getAPICountLevel(){
		return apiCountLevel;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("\n-[UserInfo : " + id + " ]------------------------------");
		sb.append("\n   name = " + name);
		sb.append("\n   join since = " + joinDate);
		sb.append("\n   userImgUrl = " + userImgURL);
		sb.append("\n   mileage = " + mileage);
		sb.append("\n---------------------------------------------------------------------");

		return sb.toString();
	}

	@Override
	public String getKeyValue() {
		return id;
	}

	@Override
	public SparseArray<String> getColumns() {
		return columns;
	}

	@Override
	public boolean setValues(SparseArray<String> values) {

		int key = 0;

		for(int i = 0; i < values.size(); i++){
			key = values.keyAt(i);

			switch(key){
			case 0 :
				id = values.get(key);
				break;

			case 1 :
				name = values.get(key);
				break;

			case 2 :
				joinDate = Long.valueOf(values.get(key));
				break;

			case 3 :
				userImgURL = values.get(key);
				break;

			case 4 :
				mileage = Integer.valueOf(values.get(key));
				break;

			case 5 :
				apiCountLevel = Integer.valueOf(values.get(key));
				break;
				
			default :
				Log.e(LOG_TAG, "UserInfo setValue got invalid index = " + key);
				return false;
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
			return name;

		case 2 :
			return joinDate.toString();

		case 3 :
			return userImgURL;

		case 4 :
			return mileage.toString();

		case 5 :
			return apiCountLevel.toString();

		default :
			Log.e(LOG_TAG, "UserInfo getValue got invalid index = " + columnID);
			break;		
		}
		return "";
	}

	@Override
	public SparseArray<String> getValues() {
		SparseArray<String> values = new SparseArray<String>();

		for(int i = 0 ; i < columns.size() ; i++){
			values.append(i, getValue(i));
		}

		return values;
	}
}
