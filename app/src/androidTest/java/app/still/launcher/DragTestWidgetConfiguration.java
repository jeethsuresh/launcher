package app.still.launcher;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
public class DragTestWidgetConfiguration extends Activity {
  @Override public void onCreate(Bundle state) {
    super.onCreate(state); setResult(RESULT_CANCELED);
    int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
    LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
    Button finish = new Button(this); finish.setText("Finish widget setup");
    finish.setOnClickListener(v -> {
      AppWidgetManager.getInstance(this).updateAppWidget(id, new RemoteViews(getPackageName(), app.still.launcher.test.R.layout.drag_test_widget));
      setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish();
    });
    layout.addView(finish);
    Button cancel = new Button(this); cancel.setText("Cancel widget setup"); cancel.setOnClickListener(v -> finish());
    layout.addView(cancel); setContentView(layout);
  }
}
