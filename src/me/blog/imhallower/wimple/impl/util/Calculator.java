package me.blog.imhallower.wimple.impl.util;

import android.util.Log;

public class Calculator {

	private static final String LOG_TAG = "Calculator";

	private enum OPERATOR { NONE, PLUS, MIN, MUL, DIV };

	private OPERATOR op = OPERATOR.NONE;
	private Double left = 0.0;
	private Double right = 0.0;

	private static final int NUMBER_SIZE = 20;
	private int[] numbers = new int[NUMBER_SIZE];
	private int pointPosition = -1;
	private int insertingPosition = 0;

	private boolean isPointInserting = false;

	public Calculator(){
		init();
	}

	private void init(){
		op = OPERATOR.NONE;
		left = 0.0;
		right = 0.0;

		resetStackedValue();

		pointPosition = -1;
		insertingPosition = 0;
	}

	private void resetStackedValue(){
		for(int i = 0; i< numbers.length; i++){
			numbers[i] = 0;
		}
	}

	public Double shift(int value){

		return doShift(value, 1);		 
	}

	public Double zero(){
		if(getStackedValue() == 0.0){
			return getStackedValue();
		}
		return doShift(0, 1);
	}

	public Double zeroTwice(){
		return doShift(0, 2);
	}

	private Double getStackedValue(){
		Double res = 0.0;

		if(pointPosition >= 0){

			for(int idx=0, mul=pointPosition ; idx <= pointPosition ; idx++, mul--){
				if(mul <= 0){
					res += numbers[idx];	
				}else{
					res += numbers[idx] * Math.pow(10, mul);
				}

				Log.d(LOG_TAG, "res=" + res + ", idx=" + idx + ", numbers[idx]=" + numbers[idx]);
			}

			for(int idx=(pointPosition+1), mul=1 ; idx < insertingPosition ; idx++, mul++){
				res += numbers[idx] * (1 / Math.pow(10, mul));
				Log.d(LOG_TAG, "res=" + res + ", idx=" + idx + ", numbers[idx]=" + numbers[idx]);
			}

		}else{

			for(int idx=0, mul=insertingPosition - 1 ; idx < insertingPosition ; idx++, mul--){
				if(mul == 0){
					res += numbers[idx];	
				}else{
					res += numbers[idx] * Math.pow(10, mul);
				}

				Log.d(LOG_TAG, "res=" + res + ", idx=" + idx + ", numbers[idx]=" + numbers[idx]);
			}
		}

		return res;
	}

	private Double doShift(int value, int shift) {

		if(insertingPosition >= NUMBER_SIZE ){
			return Double.NaN;
		}
		numbers[insertingPosition] = value;
		insertingPosition += shift;
		return getStackedValue();
	}

	private void resetPointInserting(){
		isPointInserting = false;
	}

	private void calculate(){
		right = getStackedValue();
		resetStackedValue();

		Log.d(LOG_TAG, "" + left + op.toString() + right);
		if(right == 0.0){
			Log.d(LOG_TAG, "Stacked value is 0, skip!!!");
			return;
		}

		switch(op){
		case NONE:
			left = right;
			break;
		case PLUS:
			left += right;
			break;
		case MIN:
			left -= right;
			break;
		case MUL:
			left *= right;
			break;
		case DIV:
			left /= right;
			break;
		}
		Log.d(LOG_TAG, " ==> " + left);
		right = 0.0;
	}

	public Double plus(){
		resetPointInserting();
		calculate();
		this.op = OPERATOR.PLUS;
		return left;
	}

	public Double minus(){
		resetPointInserting();
		calculate();
		this.op = OPERATOR.MIN;
		return left;
	}

	public Double multiply(){
		resetPointInserting();
		calculate();
		this.op = OPERATOR.MUL;
		return left;
	}

	public Double divide(){
		resetPointInserting();
		calculate();
		this.op = OPERATOR.DIV;
		return left;
	}

	public Double eq(){
		resetPointInserting();
		calculate();
		this.op = OPERATOR.NONE;
		return left;
	}

	public Double point(){
		if(isPointInserting){
			return getStackedValue();
		}

		resetPointInserting();
		isPointInserting = true;
		
		if((insertingPosition -1 ) < 0){
			pointPosition = 0;
			insertingPosition += 1;
		}else{
			pointPosition = insertingPosition - 1;
		}

		return getStackedValue();
	}

	public Double clear(){
		resetPointInserting();
		init();
		return getStackedValue();
	}

	public Double shiftBack(){
		if(insertingPosition <= 0){
			return getStackedValue();
		}

		Log.d(LOG_TAG, "BEFORE inserting=" + insertingPosition + ", pointPos=" + pointPosition);
		insertingPosition -= 1;
		numbers[insertingPosition] = 0;

		if(pointPosition >= insertingPosition){
			pointPosition = -1;
			resetPointInserting();
		}
		Log.d(LOG_TAG, "AFTER inserting=" + insertingPosition + ", pointPos=" + pointPosition);
		return getStackedValue();
	}
}
