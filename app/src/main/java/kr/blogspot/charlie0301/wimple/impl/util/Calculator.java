package kr.blogspot.charlie0301.wimple.impl.util;


public class Calculator {

	public interface CalculatorResultListener
	{
		void OnResultUpdate(double amount);
	}

	//private static final String LOG_TAG = "Calculator";

	private enum OPERATOR { NONE, PLUS, MIN, MUL, DIV };

	private OPERATOR op = OPERATOR.NONE;
	private Double left = 0.0;
	private Double right = 0.0;
	private CalculatorResultListener listener;

	private static final int NUMBER_SIZE = 20;
	private int[] numbers = new int[NUMBER_SIZE];
	private int pointPosition = -1;
	private int insertingPosition = 0;

	private boolean isPointInserting = false;

	public Calculator(){
		listener = null;
		init();
	}

	private void init(){
		op = OPERATOR.NONE;
		left = 0.0;
		right = 0.0;

		resetStackedValue();
	}

	private void resetStackedValue(){
		for(int i = 0; i< numbers.length; i++){
			numbers[i] = 0;
		}
		
		pointPosition = -1;
		insertingPosition = 0;
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
			}

			for(int idx=(pointPosition+1), mul=1 ; idx < insertingPosition ; idx++, mul++){
				res += numbers[idx] * (1 / Math.pow(10, mul));
			}

		}else{

			for(int idx=0, mul=insertingPosition - 1 ; idx < insertingPosition && idx < NUMBER_SIZE ; idx++, mul--){
				if(mul == 0){
					res += numbers[idx];	
				}else{
					res += numbers[idx] * Math.pow(10, mul);
				}
			}
		}

		listener.OnResultUpdate(res);
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
		pointPosition = -1;
	}

	private void calculate(){
		right = getStackedValue();
		resetStackedValue();

		if(right == 0.0){
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
		right = 0.0;
	}

	public Double plus(){
		calculate();
		resetPointInserting();
		this.op = OPERATOR.PLUS;
		return left;
	}

	public Double minus(){
		calculate();
		resetPointInserting();
		this.op = OPERATOR.MIN;
		return left;
	}

	public Double multiply(){
		calculate();
		resetPointInserting();
		this.op = OPERATOR.MUL;
		return left;
	}

	public Double divide(){
		calculate();
		resetPointInserting();
		this.op = OPERATOR.DIV;
		return left;
	}

	public Double eq(){
		calculate();
		resetPointInserting();
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

		insertingPosition -= 1;
		numbers[insertingPosition] = 0;

		if(pointPosition >= insertingPosition){
			pointPosition = -1;
			resetPointInserting();
		}
		return getStackedValue();
	}

	public Double setValue(Double value){
		resetPointInserting();
		init();
		left = value;
		listener.OnResultUpdate(value);
		return value;
	}

	public void setListener(CalculatorResultListener listener)
	{
		this.listener = listener;
		listener.OnResultUpdate(left);
	}
}
