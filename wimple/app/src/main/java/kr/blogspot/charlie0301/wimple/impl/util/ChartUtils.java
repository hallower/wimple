package kr.blogspot.charlie0301.wimple.impl.util;

import android.content.Context;
import android.util.Log;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.Utils;

import java.util.ArrayList;
import java.util.List;


public class ChartUtils {

    public static PieChart makeChart(Context context,
                                     double[] dataValues,
                                     String[] legendValues,
                                     double maxValue) {
        Utils.init(context);

        int cnt;
        List<PieEntry> entries = new ArrayList<>();

        for (cnt = 0; cnt < dataValues.length; cnt++) {
            if (dataValues[cnt] <= maxValue * 0.1)
                continue;

            Log.d("csk", dataValues[cnt] + ", " + legendValues[cnt] + ", " + maxValue * 0.03);
            entries.add(new PieEntry(Float.parseFloat(Double.valueOf(dataValues[cnt]).toString()), legendValues[cnt]));
        }

        int[] barColorValues = new int[cnt];
        for (int i = 0; i < cnt; i++) {
            barColorValues[i] = WidgetItem.predefinedColors[i % 9];
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setSliceSpace(3);
        dataSet.setColors(barColorValues);
        dataSet.setValueTextSize(18f);
        PieData data = new PieData(dataSet);

        PieChart chart = new PieChart(context);


        Legend l = chart.getLegend();
        l.setWordWrapEnabled(true);
        l.setForm(Legend.LegendForm.CIRCLE);
        /*
		l.setFormSize(10f);
		l.setForm(Legend.LegendForm.CIRCLE);
		l.setPosition(Legend.LegendPosition.BELOW_CHART_LEFT);
		l.setTextSize(12f);
		l.setTextColor(Color.BLACK);
		l.setXEntrySpace(5f);
		l.setYEntrySpace(5f);
		*/

        chart.setUsePercentValues(true);
        chart.setHoleRadius(40f);
        chart.setData(data);
        chart.invalidate();
        return chart;
    }

}