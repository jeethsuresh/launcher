package app.still.launcher;
import android.appwidget.*;
import android.content.Context;
import android.widget.RemoteViews;
public class DragTestWidgetProvider extends AppWidgetProvider {
  @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
    for (int id : ids) manager.updateAppWidget(id, new RemoteViews(context.getPackageName(), app.still.launcher.test.R.layout.drag_test_widget));
  }
}
