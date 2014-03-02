package kr.blogspot.charlie0301.widget;

import org.achartengine.chart.DoughnutChart;
import org.achartengine.model.MultipleCategorySeries;
import org.achartengine.renderer.DefaultRenderer;
import org.achartengine.renderer.SimpleSeriesRenderer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;


public class DoughnutChartView extends View {

	private DoughnutChart chart = null;
	private DefaultRenderer renderer = new DefaultRenderer();
	@SuppressWarnings("unused")
	private final Context parent;
	private final Paint p = new Paint();
	
	private double[] dataValues;
	private int[] barColorValues;
	private String[] legendValues;

	public DoughnutChartView(Context context) {
		super(context);
		setFocusable(true);
		parent = context;

	}

	public DoughnutChartView(Context context, AttributeSet attrs) {
		super(context,attrs);
		setFocusable(true);
		parent = context;
	}

	public DoughnutChartView(Context context, AttributeSet attrs, int defaultStyle) {
		super(context, attrs, defaultStyle);
		setFocusable(true);
		parent = context;
	}



	public double[] getDataValues() {
		return dataValues;
	}

	public int[] getBarColorValues() {
		return barColorValues;
	}

	public String[] getLegendValues() {
		return legendValues;
	}

	public void setDataValues(double[] dataValues) {
		this.dataValues = dataValues;
	}

	public void setBarColorValues(int[] barColorValues) {
		this.barColorValues = barColorValues;
	}

	public void setLegendValues(String[] legendValues) {
		this.legendValues = legendValues;
	}

	public void setDisplayLabels(boolean display){
		renderer.setShowLabels(display);
	}
	public void makeChart(){

		//double[] values = new double[] {10,20,30,40};
		//int[] colors = new int[]{Color.CYAN, Color.MAGENTA,  Color.YELLOW, Color.GREEN};
		//String[] texts = new String[] {"SAMPLE1", "SAMPEL2", "SAMPLE3", "SAMPLE4" };

		renderer.setShowLegend(false);	        
		renderer.setDisplayValues(true);
		//renderer.setStartAngle(0);
		renderer.setLabelsTextSize(30);
		//renderer.setScale(0.8f);
		//renderer.setZoomEnabled(true);
		for(int color : barColorValues){
			SimpleSeriesRenderer ssr = new SimpleSeriesRenderer();
			ssr.setColor(color);
			renderer.addSeriesRenderer(ssr);
		}

		MultipleCategorySeries series = new MultipleCategorySeries("");
		series.add(legendValues, dataValues);	        

		chart = new DoughnutChart(series, renderer);
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		int width = getMeasuredWidth(); 
		int height = getMeasuredHeight();
		Log.i("ChartView","onDraw->Width:"+width+"/height:"+height);

		if (chart != null) {	        	
			chart.draw(canvas, 0, 0, width, height, p);
		}	        
	}
}