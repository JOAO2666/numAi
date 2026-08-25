package io.github.gohoski.numai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Scanner;

import io.github.gohoski.numai.api.ApiCallback;
import io.github.gohoski.numai.api.ApiError;
import io.github.gohoski.numai.api.ApiManager;
import io.github.gohoski.numai.api.ApiService;
import io.github.gohoski.numai.api.GeminiImageService;
import io.github.gohoski.numai.data.ConfigManager;
import io.github.gohoski.numai.model.Config;
import io.github.gohoski.numai.model.ModelInfo;
import io.github.gohoski.numai.ui.Loading;
import io.github.gohoski.numai.ui.ModelPickerDialog;
import io.github.gohoski.numai.ui.SettingsHelper;
import io.github.gohoski.numai.util.ModelCatalog;
import io.github.gohoski.numai.util.ModelLoadCoordinator;
import io.github.gohoski.numai.util.ModelSelector;

public class SettingsActivity extends Activity {
    Context context;
    ConfigManager config;
    ApiService api;
    Spinner apiSpinner, chatSpinner, thinkSpinner, searchEngineSpinner;
    EditText keyText;
    boolean fetched = false;
    ArrayList<String> fetchedModels = null;
    ArrayList<ModelInfo> fetchedModelInfos = new ArrayList<ModelInfo>();
    String lastChatModel, lastThinkModel;
    String activeProviderId;
    final ModelLoadCoordinator modelLoadCoordinator = new ModelLoadCoordinator();
    CheckBox shrinkThink, webSearch, webFetch, disableToolsImg;
    String systemPrompt;
    EditText updateDelay, geminiImageKey, geminiImageModel;
    Button fetchGeminiImageModels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        context = this;
        config = ConfigManager.getInstance();
        final Config conf = config.getConfig();
        activeProviderId = config.getActiveProviderId();
        modelLoadCoordinator.switchProvider(activeProviderId);
        api = new ApiService(this);
        systemPrompt = conf.getSystemPrompt();

        findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });

        apiSpinner = (Spinner) findViewById(R.id.api_spinner);
        SettingsHelper.setupApiSpinner(context, apiSpinner, config, new SettingsHelper.ApiSelectionCallback() {
            @Override
            public void onApiSelected(String api) {
                switchProviderUi(ApiManager.getUrlByName(api));
            }
        });

        keyText = (EditText) findViewById(R.id.apiKey);
        keyText.setText(conf.getApiKey());

        chatSpinner = (Spinner) findViewById(R.id.chat_spinner);
        lastChatModel = conf.getChatModel();
        setupModelSpinner(chatSpinner, lastChatModel);

        thinkSpinner = (Spinner) findViewById(R.id.think_spinner);
        lastThinkModel = conf.getThinkingModel();
        setupModelSpinner(thinkSpinner, lastThinkModel);

        chatSpinner.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    loadModels(chatSpinner);
                    return true;
                }
                return false;
            }
        });
        thinkSpinner.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    loadModels(thinkSpinner);
                    return true;
                }
                return false;
            }
        });

        if (fetchedModelInfos.isEmpty() &&
                !config.getActiveProviderSettings().getCachedModels().isEmpty()) {
            applyModelInfos(new ArrayList<ModelInfo>(
                    config.getActiveProviderSettings().getCachedModels()));
            fetched = true;
        }

        shrinkThink = (CheckBox) findViewById(R.id.shrinkThinking);
        shrinkThink.setChecked(conf.getShrinkThink());

        webSearch = (CheckBox) findViewById(R.id.webSearch);
        webSearch.setChecked(conf.isWebSearchEnabled());

        webFetch = (CheckBox) findViewById(R.id.webFetch);
        webFetch.setChecked(conf.isWebFetchEnabled());

        disableToolsImg = (CheckBox) findViewById(R.id.disableToolsImg);
        disableToolsImg.setChecked(conf.isDisableToolsWithImage());

        searchEngineSpinner = (Spinner) findViewById(R.id.search_engine_spinner);
        searchEngineSpinner.setSelection("duckduckgo".equalsIgnoreCase(conf.getSearchEngine()) ? 1 : 0);

        updateDelay = (EditText) findViewById(R.id.update_delay);
        updateDelay.setText(conf.getUpdateDelay()+"");

        geminiImageKey = (EditText) findViewById(R.id.gemini_image_api_key);
        geminiImageKey.setText(config.getGeminiImageApiKey());
        geminiImageModel = (EditText) findViewById(R.id.gemini_image_model);
        geminiImageModel.setText(config.getGeminiImageModel());
        fetchGeminiImageModels = (Button) findViewById(R.id.fetch_gemini_image_models);
        fetchGeminiImageModels.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fetchGeminiImageModels();
            }
        });

        // Restore saved instance state on rotation
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("api_key") && keyText != null) {
                keyText.setText(savedInstanceState.getString("api_key"));
            }
            if (savedInstanceState.containsKey("system_prompt")) {
                systemPrompt = savedInstanceState.getString("system_prompt");
            }
            if (savedInstanceState.containsKey("update_delay") && updateDelay != null) {
                updateDelay.setText(savedInstanceState.getString("update_delay"));
            }
            if (savedInstanceState.containsKey("gemini_image_key") && geminiImageKey != null) {
                geminiImageKey.setText(savedInstanceState.getString("gemini_image_key"));
            }
            if (savedInstanceState.containsKey("gemini_image_model") && geminiImageModel != null) {
                geminiImageModel.setText(savedInstanceState.getString("gemini_image_model"));
            }
            if (savedInstanceState.containsKey("shrink_think") && shrinkThink != null) {
                shrinkThink.setChecked(savedInstanceState.getBoolean("shrink_think"));
            }
            if (savedInstanceState.containsKey("web_search") && webSearch != null) {
                webSearch.setChecked(savedInstanceState.getBoolean("web_search"));
            }
            if (savedInstanceState.containsKey("web_fetch") && webFetch != null) {
                webFetch.setChecked(savedInstanceState.getBoolean("web_fetch"));
            }
            if (savedInstanceState.containsKey("disable_tools_img") && disableToolsImg != null) {
                disableToolsImg.setChecked(savedInstanceState.getBoolean("disable_tools_img"));
            }
            if (savedInstanceState.containsKey("search_engine_pos") && searchEngineSpinner != null) {
                searchEngineSpinner.setSelection(savedInstanceState.getInt("search_engine_pos"));
            }
            if (savedInstanceState.containsKey("api_spinner_pos") && apiSpinner != null) {
                apiSpinner.setSelection(savedInstanceState.getInt("api_spinner_pos"));
            }

            fetched = savedInstanceState.getBoolean("fetched", false);
            if (fetched && savedInstanceState.containsKey("fetched_models")) {
                fetchedModels = savedInstanceState.getStringArrayList("fetched_models");
                if (fetchedModels != null) {
                    fetchedModelInfos = new ArrayList<ModelInfo>();
                    for (int m = 0; m < fetchedModels.size(); m++) {
                        fetchedModelInfos.add(new ModelInfo(fetchedModels.get(m)));
                    }
                    ArrayList<String> options = new ArrayList<String>(fetchedModels);
                    options.add(getString(R.string.other));
                    ArrayAdapter<String> fetchedAdapter = new ArrayAdapter<String>(
                            context,
                            android.R.layout.simple_spinner_item,
                            options
                    );
                    fetchedAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    chatSpinner.setAdapter(fetchedAdapter);
                    thinkSpinner.setAdapter(fetchedAdapter);
                    if (savedInstanceState.containsKey("chat_spinner_pos")) {
                        chatSpinner.setSelection(savedInstanceState.getInt("chat_spinner_pos"));
                    }
                    if (savedInstanceState.containsKey("think_spinner_pos")) {
                        thinkSpinner.setSelection(savedInstanceState.getInt("think_spinner_pos"));
                    }
                }
            }
        }

        findViewById(R.id.ok).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveGeminiImageSettings();
                final String urlByName = ApiManager.getUrlByName(apiSpinner.getSelectedItem().toString());
                final String selectedProviderId = ApiManager.getIdByUrl(urlByName);
                config.selectProvider(selectedProviderId, urlByName);
                activeProviderId = selectedProviderId;
                config.updateApiKey(keyText.getText().toString());
                if (fetched || config.getConfig().getChatModel().trim().length() > 0) {
                    config.setConfig(new Config(urlByName,
                            keyText.getText().toString(),
                            lastChatModel == null ? "" : lastChatModel,
                            lastThinkModel == null ? "" : lastThinkModel,
                            shrinkThink.isChecked(),
                            systemPrompt,
                            Integer.parseInt(updateDelay.getText().toString()),
                            webSearch.isChecked(),
                            searchEngineSpinner.getSelectedItem().toString().toLowerCase(),
                            webFetch.isChecked(),
                            disableToolsImg.isChecked()));
                    Intent intent = new Intent(context, MainActivity.class);
                    startActivity(intent);
                    finish();
                } else if (!fetched) {
                    final Loading loading = new Loading(context);
                    final ModelLoadCoordinator.RequestToken token =
                            modelLoadCoordinator.begin(activeProviderId);
                    api.getModelInfos(new ApiCallback<ArrayList<ModelInfo>>() {
                        @Override
                        public void onSuccess(ArrayList<ModelInfo> models) {
                            if (!modelLoadCoordinator.accepts(token)) {
                                loading.dismiss();
                                return;
                            }
                            config.replaceModelCache(activeProviderId, models);
                            String chatModel = ModelCatalog.validSelection("", models, false);
                            String thinkingModel = ModelCatalog.validSelection("", models, true);
                            config.setConfig(new Config(urlByName,
                                    keyText.getText().toString(),
                                    chatModel,
                                    thinkingModel,
                                    shrinkThink.isChecked(),
                                    systemPrompt,
                                    Integer.parseInt(updateDelay.getText().toString()),
                                    webSearch.isChecked(),
                                    searchEngineSpinner.getSelectedItem().toString().toLowerCase(),
                                    webFetch.isChecked(),
                                    disableToolsImg.isChecked()));
                            loading.dismiss();
                            Intent intent = new Intent(context, MainActivity.class);
                            startActivity(intent);
                            finish();
                        }

                        @Override
                        public void onError(ApiError error) {
                            Toast.makeText(context, error.getMessage(), Toast.LENGTH_LONG).show();
                            loading.dismiss();
                        }
                    });
                }
            }
        });

        findViewById(R.id.changeSystemPrompt).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final EditText edittext = new EditText(context);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.FILL_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(20, 10, 20, 10);
                edittext.setLayoutParams(params);
                edittext.setSingleLine(false);
                edittext.setMinLines(4);
                edittext.setTextSize(14);
                edittext.setPadding(10, 10, 10, 10);
                edittext.setText(systemPrompt);
                new AlertDialog.Builder(context)
                        .setTitle(R.string.change_system)
                        .setView(edittext)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                systemPrompt = edittext.getText().toString();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });

        findViewById(R.id.from_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("text/plain");
                startActivityForResult(Intent.createChooser(intent, getString(R.string.select_txt)), 2);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (keyText != null) {
            outState.putString("api_key", keyText.getText().toString());
        }
        if (systemPrompt != null) {
            outState.putString("system_prompt", systemPrompt);
        }
        if (updateDelay != null) {
            outState.putString("update_delay", updateDelay.getText().toString());
        }
        if (geminiImageKey != null) {
            outState.putString("gemini_image_key", geminiImageKey.getText().toString());
        }
        if (geminiImageModel != null) {
            outState.putString("gemini_image_model", geminiImageModel.getText().toString());
        }
        if (shrinkThink != null) {
            outState.putBoolean("shrink_think", shrinkThink.isChecked());
        }
        if (webSearch != null) {
            outState.putBoolean("web_search", webSearch.isChecked());
        }
        if (webFetch != null) {
            outState.putBoolean("web_fetch", webFetch.isChecked());
        }
        if (disableToolsImg != null) {
            outState.putBoolean("disable_tools_img", disableToolsImg.isChecked());
        }
        if (searchEngineSpinner != null) {
            outState.putInt("search_engine_pos", searchEngineSpinner.getSelectedItemPosition());
        }
        if (apiSpinner != null) {
            outState.putInt("api_spinner_pos", apiSpinner.getSelectedItemPosition());
        }
        outState.putBoolean("fetched", fetched);
        if (fetched && fetchedModels != null) {
            outState.putStringArrayList("fetched_models", fetchedModels);
            if (chatSpinner != null) {
                outState.putInt("chat_spinner_pos", chatSpinner.getSelectedItemPosition());
            }
            if (thinkSpinner != null) {
                outState.putInt("think_spinner_pos", thinkSpinner.getSelectedItemPosition());
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 2 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    keyText.setText(new Scanner(is, "UTF-8").useDelimiter("\\A").next());
                    Toast.makeText(this, R.string.key_success, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void switchProviderUi(String baseUrl) {
        String nextProviderId = ApiManager.getIdByUrl(baseUrl);
        if (nextProviderId.equals(activeProviderId) && keyText == null) return;
        if (nextProviderId.equals(activeProviderId) && chatSpinner != null &&
                config.getConfig().getBaseUrl().equals(baseUrl)) return;

        modelLoadCoordinator.switchProvider(nextProviderId);
        config.selectProvider(nextProviderId, baseUrl);
        activeProviderId = nextProviderId;
        fetched = false;
        fetchedModels = null;
        fetchedModelInfos = new ArrayList<ModelInfo>(
                config.getActiveProviderSettings().getCachedModels());

        if (keyText != null) keyText.setText(config.getConfig().getApiKey());
        lastChatModel = config.getConfig().getChatModel();
        lastThinkModel = config.getConfig().getThinkingModel();
        if (chatSpinner != null) setupModelSpinner(chatSpinner, lastChatModel);
        if (thinkSpinner != null) setupModelSpinner(thinkSpinner, lastThinkModel);
        if (!fetchedModelInfos.isEmpty()) {
            applyModelInfos(fetchedModelInfos);
            fetched = true;
        }
    }

    private void setupModelSpinner(final Spinner spinner, final String initialModel) {
        final ArrayList<String> options = new ArrayList<String>();
        if (initialModel != null && initialModel.length() > 0) options.add(initialModel);
        options.add(getString(R.string.other));
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                context,
                android.R.layout.simple_spinner_item,
                options
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String selected = adapterView.getItemAtPosition(i).toString();
                if (selected.equals(getString(R.string.other))) {
                    if (spinner == chatSpinner) {
                        showModelDialog(spinner, lastChatModel);
                    } else {
                        showModelDialog(spinner, lastThinkModel);
                    }
                } else if (spinner == chatSpinner) {
                    lastChatModel = selected;
                } else {
                    lastThinkModel = selected;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    private void applyModelInfos(ArrayList<ModelInfo> models) {
        fetchedModelInfos = new ArrayList<ModelInfo>(models);
        fetchedModels = new ArrayList<String>();
        for (int i = 0; i < models.size(); i++) fetchedModels.add(models.get(i).getId());
        lastChatModel = ModelCatalog.validSelection(lastChatModel, models, false);
        lastThinkModel = ModelCatalog.validSelection(lastThinkModel, models, true);
        setSpinnerModels(chatSpinner, lastChatModel);
        setSpinnerModels(thinkSpinner, lastThinkModel);
    }

    private void setSpinnerModels(Spinner spinner, String selected) {
        if (spinner == null) return;
        ArrayList<String> options = new ArrayList<String>();
        for (int i = 0; i < fetchedModelInfos.size(); i++) {
            options.add(fetchedModelInfos.get(i).getId());
        }
        if (selected != null && selected.length() > 0 && !options.contains(selected)) {
            options.add(selected);
        }
        options.add(getString(R.string.other));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                context, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int position = options.indexOf(selected);
        if (position >= 0) spinner.setSelection(position);
    }

    private void showModelDialog(final Spinner spinner, final String previousModel) {
        final boolean[] accepted = {false};
        final EditText edittext = new EditText(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(20, 10, 20, 10);
        edittext.setLayoutParams(params);
        edittext.setSingleLine();
        edittext.setTextSize(14);
        edittext.setPadding(10, 10, 10, 10);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.other)
                .setMessage(R.string.custom_model)
                .setView(edittext)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String model = edittext.getText().toString().trim();
                        if (model.length() == 0 || model.equals(getString(R.string.other))) {
                            Toast.makeText(context, R.string.bad_model, Toast.LENGTH_SHORT).show();
                            restoreModelSelection(spinner, previousModel);
                            return;
                        }
                        accepted[0] = true;
                        ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
                        if (indexOfItem(adapter, model) < 0) {
                            adapter.add(model);
                        }
                        spinner.setSelection(indexOfItem(adapter, model));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                if (!accepted[0]) {
                    restoreModelSelection(spinner, previousModel);
                }
            }
        });
        dialog.show();
    }

    private void restoreModelSelection(Spinner spinner, String model) {
        ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
        int index = indexOfItem(adapter, model);
        spinner.setSelection(index >= 0 ? index : 0);
    }

    private int indexOfItem(ArrayAdapter adapter, String item) {
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItem(i).toString().equals(item)) {
                return i;
            }
        }
        return -1;
    }

    private void saveGeminiImageSettings() {
        if (geminiImageKey != null) {
            config.updateGeminiImageApiKey(geminiImageKey.getText().toString());
        }
        if (geminiImageModel != null) {
            config.updateGeminiImageModel(geminiImageModel.getText().toString());
        }
    }

    private void fetchGeminiImageModels() {
        // Save the text currently on screen so a newly pasted key can be used immediately.
        saveGeminiImageSettings();
        final Loading loading = new Loading(context);
        GeminiImageService gemini = new GeminiImageService(context);
        gemini.getAvailableImageModels(new ApiCallback<ArrayList<String>>() {
            @Override
            public void onSuccess(final ArrayList<String> models) {
                loading.dismiss();
                if (models == null || models.isEmpty()) {
                    Toast.makeText(context, R.string.gemini_image_models_empty, Toast.LENGTH_LONG).show();
                    return;
                }
                final String[] modelNames = models.toArray(new String[models.size()]);
                new AlertDialog.Builder(context)
                        .setTitle(R.string.choose_gemini_image_model)
                        .setItems(modelNames, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which >= 0 && which < modelNames.length) {
                                    geminiImageModel.setText(modelNames[which]);
                                }
                            }
                        })
                        .show();
            }

            @Override
            public void onError(ApiError error) {
                loading.dismiss();
                Toast.makeText(context, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadModels(final Spinner spinner) {
        if (fetched && !fetchedModelInfos.isEmpty()) {
            showModelPicker(spinner);
            return;
        }
        final ModelLoadCoordinator.RequestToken token =
                modelLoadCoordinator.begin(activeProviderId);
        final Loading loading = new Loading(context);
        api.getModelInfos(new ApiCallback<ArrayList<ModelInfo>>() {
            @Override
            public void onSuccess(ArrayList<ModelInfo> result) {
                loading.dismiss();
                if (!modelLoadCoordinator.accepts(token)) return;
                config.replaceModelCache(activeProviderId, result);
                applyModelInfos(result);
                fetched = true;
                showModelPicker(spinner);
            }

            @Override
            public void onError(ApiError error) {
                loading.dismiss();
                if (!modelLoadCoordinator.accepts(token)) return;
                Toast.makeText(context, getString(R.string.models_load_failed,
                        ApiManager.getNameByUrl(config.getConfig().getBaseUrl()),
                        error.getMessage()), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showModelPicker(final Spinner spinner) {
        boolean thinking = spinner == thinkSpinner;
        ModelPickerDialog.show(context,
                thinking ? getString(R.string.thinking_model) : getString(R.string.chat_model),
                fetchedModelInfos, thinking ? lastThinkModel : lastChatModel,
                new ModelPickerDialog.Callback() {
                    public void onModelSelected(ModelInfo model) {
                        if (spinner == chatSpinner) {
                            lastChatModel = model.getId();
                        } else {
                            lastThinkModel = model.getId();
                        }
                        ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
                        int position = indexOfItem(adapter, model.getId());
                        if (position >= 0) spinner.setSelection(position);
                    }

                    public void onRefreshRequested() {
                        config.invalidateModelCache(activeProviderId);
                        fetched = false;
                        fetchedModelInfos = new ArrayList<ModelInfo>();
                        loadModels(spinner);
                    }
                });
    }
}
