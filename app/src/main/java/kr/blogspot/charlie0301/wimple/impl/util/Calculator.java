package kr.blogspot.charlie0301.wimple.impl.util;


public class Calculator {

    public interface CalculatorResultListener {
        void OnResultUpdate(double amount);
    }

    //private static final String LOG_TAG = "Calculator";

    private enum OPERATOR {NONE, PLUS, MIN, MUL, DIV}

    ;

    private OPERATOR op = OPERATOR.NONE;
    private Double left = 0.0;
    private Double right = 0.0;
    private CalculatorResultListener listener;

    private static final int NUMBER_SIZE = 20;
    private int[] numbers = new int[NUMBER_SIZE];
    private int pointPosition = -1;
    private int insertingPosition = 0;

    private boolean isPointInserting = false;

    public Calculator() {
        listener = null;
        init();
    }

    private void init() {
        op = OPERATOR.NONE;
        left = 0.0;
        right = 0.0;

        resetStackedValue();
    }

    private void resetStackedValue() {
        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = 0;
        }

        pointPosition = -1;
        insertingPosition = 0;
    }

    public Double shift(int value) {

        return doShift(value, 1);
    }

    public Double zero(int len) {
        Double ret;
        if (op == OPERATOR.NONE &&
                insertingPosition == 0) {
            ret = left = 0.0;
            resetStackedValue();
        } else {
            ret = doShift(0, len);
        }
        listener.OnResultUpdate(ret);
        return ret;
    }

    public Double zero() {
        return zero(1);
    }

    public Double zeroTwice() {
        return zero(2);
    }

    private Double getStackedValue() {
        Double res = 0.0;
        int minusMultiply = 1;
        int startPos = 0;
        int adjMul = 0;

        if (numbers[0] == '-') {
            minusMultiply = -1;
            startPos = 1;
            adjMul = -1;
        }

        if (pointPosition >= 0) {

            for (int idx = startPos, mul = pointPosition + adjMul; idx <= pointPosition; idx++, mul--) {

                if (mul <= 0) {
                    res += numbers[idx];
                } else {
                    res += numbers[idx] * Math.pow(10, mul);
                }
            }

            for (int idx = (pointPosition + 1), mul = 1; idx < insertingPosition; idx++, mul++) {
                res += numbers[idx] * (1 / Math.pow(10, mul));
            }

        } else {

            for (int idx = startPos, mul = insertingPosition - 1 + adjMul; idx < insertingPosition && idx < NUMBER_SIZE; idx++, mul--) {
                if (mul == 0) {
                    res += numbers[idx];
                } else {
                    res += numbers[idx] * Math.pow(10, mul);
                }
            }
        }

        return res * minusMultiply;
    }

    private void setStackedValue(Double number) {
        String value = number.toString();
        int length = value.length();

        if ((number % 1) == 0) {
            length = value.indexOf(".");
        }
        insertingPosition = length;

        for (int idx = 0, wPos = 0; idx < length; idx++) {
            if (idx < length) {
                if (value.charAt(idx) == '.') {
                    pointPosition = idx - 1;
                    insertingPosition -= 1;
                } else if (value.charAt(idx) == '-') {
                    numbers[wPos++] = value.charAt(idx);
                } else {
                    numbers[wPos++] = value.charAt(idx) - '0';
                }
            }
        }
    }

    private Double doShift(int value, int shift) {

        if (insertingPosition >= NUMBER_SIZE) {
            listener.OnResultUpdate(Double.NaN);
            return Double.NaN;
        }
        numbers[insertingPosition] = value;
        insertingPosition += shift;
        Double ret = getStackedValue();
        listener.OnResultUpdate(ret);
        return ret;
    }

    private void resetPointInserting() {
        isPointInserting = false;
        pointPosition = -1;
    }

    private void calculate() {
        right = getStackedValue();
        resetStackedValue();

        if (right == 0.0) {
            return;
        }

        switch (op) {
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
        listener.OnResultUpdate(left);
    }

    public Double plus() {
        calculate();
        resetPointInserting();
        this.op = OPERATOR.PLUS;
        return left;
    }

    public Double minus() {
        calculate();
        resetPointInserting();
        this.op = OPERATOR.MIN;
        return left;
    }

    public Double multiply() {
        calculate();
        resetPointInserting();
        this.op = OPERATOR.MUL;
        return left;
    }

    public Double divide() {
        calculate();
        resetPointInserting();
        this.op = OPERATOR.DIV;
        return left;
    }

    public Double eq() {
        calculate();
        resetPointInserting();
        this.op = OPERATOR.NONE;
        setStackedValue(left);
        return left;
    }

    public Double point() {
        if (isPointInserting) {
            return getStackedValue();
        }

        resetPointInserting();
        isPointInserting = true;

        if ((insertingPosition - 1) < 0) {
            pointPosition = 0;
            insertingPosition += 1;
        } else {
            pointPosition = insertingPosition - 1;
        }

        Double ret = getStackedValue();
        listener.OnResultUpdate(ret);
        return ret;
    }

    public Double clear() {
        resetPointInserting();
        init();
        Double ret = getStackedValue();
        listener.OnResultUpdate(ret);
        return ret;
    }

    public Double shiftBack() {
        if (insertingPosition <= 0) {
            return getStackedValue();
        }

        insertingPosition -= 1;
        numbers[insertingPosition] = 0;

        if (pointPosition >= insertingPosition) {
            pointPosition = -1;
            resetPointInserting();
        }
        Double ret = getStackedValue();
        listener.OnResultUpdate(ret);
        return ret;
    }

    public Double setValue(Double value) {
        resetPointInserting();
        init();
        left = value;
        listener.OnResultUpdate(value);
        return value;
    }

    public void setListener(CalculatorResultListener listener) {
        this.listener = listener;
        listener.OnResultUpdate(left);
    }
}
