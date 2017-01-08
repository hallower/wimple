package kr.blogspot.charlie0301.wimple.impl.util;

import android.content.Context;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import java.util.ArrayList;
import java.util.List;


public class ChartUtils {

	// TODO : refactoring
	public static PieChart makeChart(Context context,
									 double[] dataValues,
									 String[] legendValues){

		int[] barColorValues = new int[dataValues.length];
		for(int i = 0; i < dataValues.length; i++){
			barColorValues[i] = WidgetItem.predefinedColors[i%9];
		}

		List<PieEntry> entries = new ArrayList<>();
		for(int i = 0;i<dataValues.length;i++)
		{
			float value = Float.parseFloat(Double.valueOf(dataValues[i]).toString());
			entries.add(new PieEntry(value, legendValues[i]));
		}

		PieDataSet dataSet = new PieDataSet(entries, "");
		dataSet.setSliceSpace(2);
		dataSet.setColors(barColorValues);
		PieData data = new PieData(dataSet);

		PieChart chart = new PieChart(context);
		chart.setUsePercentValues(true);
		chart.setData(data);
		chart.invalidate();
		return chart;
	}

}