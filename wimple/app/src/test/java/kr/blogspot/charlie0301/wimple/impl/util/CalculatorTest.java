package kr.blogspot.charlie0301.wimple.impl.util;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class CalculatorTest {

    private Calculator calculator;
    private List<Double> updates;

    @Before
    public void setUp() {
        calculator = new Calculator();
        updates = new ArrayList<>();
        calculator.setListener(updates::add);
    }

    @Test
    public void plusAndEquals_accumulatesOperands() {
        calculator.shift(1);
        calculator.shift(2);
        calculator.plus();
        calculator.shift(3);

        assertEquals(15.0, calculator.eq(), 0.0001);
        assertEquals(15.0, updates.get(updates.size() - 1), 0.0001);
    }

    @Test
    public void point_buildsFractionalNumber() {
        calculator.shift(1);
        calculator.point();
        calculator.shift(2);
        calculator.shift(5);

        assertEquals(1.25, updates.get(updates.size() - 1), 0.0001);
    }

    @Test
    public void shiftBack_removesLastDigit() {
        calculator.shift(1);
        calculator.shift(2);
        calculator.shift(3);

        assertEquals(12.0, calculator.shiftBack(), 0.0001);
    }
}
