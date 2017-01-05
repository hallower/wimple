
package kr.blogspot.charlie0301.wimple.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;

public class ItemListView extends ListView {

	private OnItemSelectionListener selectionListener;

	public ItemListView(Context context) {
		super(context);

		init();
	}

	public ItemListView(Context context, AttributeSet attrs) {
		super(context, attrs);

		init();
	}

	private void init() {
		setOnItemClickListener(new OnItemClickAdapter());
	}

	public void setAdapter(BaseAdapter adapter) {
		super.setAdapter(adapter);
	}


	public void setOnDataSelectionListener(OnItemSelectionListener listener) {
		this.selectionListener = listener;
	}

	public OnItemSelectionListener getOnDataSelectionListener() {
		return selectionListener;
	}

	class OnItemClickAdapter implements OnItemClickListener {

		public OnItemClickAdapter() {

		}

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			if (selectionListener == null) {
				return;
			}

			selectionListener.onDataSelected(parent, view, position, id);
		}
	}

}
