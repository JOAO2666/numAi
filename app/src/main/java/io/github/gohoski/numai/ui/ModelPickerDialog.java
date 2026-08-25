package io.github.gohoski.numai.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import io.github.gohoski.numai.R;
import io.github.gohoski.numai.model.ModelInfo;
import io.github.gohoski.numai.util.ModelCatalog;

/** Compact, searchable selector shared by chat and reasoning model fields. */
public final class ModelPickerDialog {
    private ModelPickerDialog() {}

    public interface Callback {
        void onModelSelected(ModelInfo model);
        void onRefreshRequested();
    }

    public static void show(final Context context, String title,
            final List<ModelInfo> allModels, final String selected,
            final Callback callback) {
        final EditText search = new EditText(context);
        search.setSingleLine(true);
        search.setHint(R.string.search_models_hint);
        search.setTextSize(13);

        final CheckBox freeOnly = new CheckBox(context);
        freeOnly.setText(R.string.only_free_models);
        freeOnly.setTextSize(12);

        final ListView list = new ListView(context);
        list.setFastScrollEnabled(true);
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                context, android.R.layout.simple_list_item_1, new ArrayList<String>());
        list.setAdapter(adapter);
        final ArrayList<ModelInfo> visible = new ArrayList<ModelInfo>();

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (8 * context.getResources().getDisplayMetrics().density + 0.5f);
        box.setPadding(padding, padding, padding, 0);
        box.addView(search, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(freeOnly, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT, 0, 1));

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(box)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.refresh_models, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface ignored, int which) {
                        if (callback != null) callback.onRefreshRequested();
                    }
                })
                .create();

        final Runnable refresh = new Runnable() {
            public void run() {
                List<ModelInfo> filtered = ModelCatalog.filter(allModels,
                        search.getText().toString(), freeOnly.isChecked());
                visible.clear();
                visible.addAll(filtered);
                adapter.clear();
                for (int i = 0; i < filtered.size(); i++) {
                    ModelInfo model = filtered.get(i);
                    adapter.add((model.isFree() ? "[FREE] " : "") + model.getId());
                }
                adapter.notifyDataSetChanged();
            }
        };

        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh.run();
            }
            public void afterTextChanged(android.text.Editable s) {}
        });
        freeOnly.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { refresh.run(); }
        });
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> parent, View view,
                    int position, long id) {
                if (position >= 0 && position < visible.size()) {
                    if (callback != null) callback.onModelSelected(visible.get(position));
                    dialog.dismiss();
                }
            }
        });
        list.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view,
                    int position, long id) {
                if (position >= 0 && position < visible.size()) {
                    new AlertDialog.Builder(context)
                            .setMessage(visible.get(position).getId())
                            .setPositiveButton(android.R.string.ok, null).show();
                }
                return true;
            }
        });
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface ignored) {
                refresh.run();
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                }
            }
        });
        dialog.show();
    }
}
