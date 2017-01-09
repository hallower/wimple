package kr.blogspot.charlie0301.wimple.impl.util;

import android.content.Context;
import android.graphics.Color;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import java.util.ArrayList;
import java.util.List;


public class ChartUtils {

	// TODO : refactoring
	public static PieChart makeChart(Context context,
									 double[] dataValues,
									 String[] legendValues,
									 double maxValue){

		List<PieEntry> entries = new ArrayList<>();
		int cnt;
		float fMaxValue = Float.parseFloat(Double.valueOf(maxValue).toString());
		for(cnt = 0;cnt<dataValues.length;cnt++)
		{
			float value = Float.parseFloat(Double.valueOf(dataValues[cnt]).toString());
			if(value <= (fMaxValue * 0.01))
				continue;
			entries.add(new PieEntry(value, legendValues[cnt]));
		}

		int[] barColorValues = new int[cnt];
		for(int i = 0; i < cnt; i++){
			barColorValues[i] = WidgetItem.predefinedColors[i%9];
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