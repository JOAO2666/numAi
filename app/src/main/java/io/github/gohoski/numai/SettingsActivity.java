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
import android.widget.ArrayAdapter;
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
import io.github.gohoski.numai.data.ConfigManager;
import io.github.gohoski.numai.model.Config;
import io.github.gohoski.numai.ui.Loading;
import io.github.gohoski.numai.ui.SettingsHelper;
import io.github.gohoski.numai.util.ModelSelector;

public class SettingsActivity extends Activity {
    Context context;
    ConfigManager config;
    ApiService api;
    Spinner apiSpinner, chatSpinner, thinkSpinner;
    EditText keyText;
    boolean fetched = false;
    ArrayList<String> fetchedModels = null;
    CheckBox shrinkThink, webSearch, webFetch;
    String systemPrompt;
    EditText updateDelay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        context = this;
        config = ConfigManager.getInstance();
        final Config conf = config.getConfig();
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
                fetched = false;
                fetchedModels = null;
                System.out.println(api);
            }
        });

        keyText = (EditText) findViewById(R.id.apiKey);
        keyText.setText(conf.getApiKey());

        chatSpinner = (Spinner) findViewById(R.id.chat_spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{conf.getChatModel()}
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        chatSpinner.setAdapter(adapter);

        thinkSpinner = (Spinner) findViewById(R.id.think_spinner);
        adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{conf.getThinkingModel()}
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        thinkSpinner.setAdapter(adapter);

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

        shrinkThink = (CheckBox) findViewById(R.id.shrinkThinking);
        shrinkThink.setChecked(conf.getShrinkThink());

        webSearch = (CheckBox) findViewById(R.id.webSearch);
        webSearch.setChecked(conf.isWebSearchEnabled());

        webFetch = (CheckBox) findViewById(R.id.webFetch);
        webFetch.setChecked(conf.isWebFetchEnabled());

        updateDelay = (EditText) findViewById(R.id.update_delay);
        updateDelay.setText(conf.getUpdateDelay()+"");

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
            if (savedInstanceState.containsKey("shrink_think") && shrinkThink != null) {
                shrinkThink.setChecked(savedInstanceState.getBoolean("shrink_think"));
            }
            if (savedInstanceState.containsKey("web_search") && webSearch != null) {
                webSearch.setChecked(savedInstanceState.getBoolean("web_search"));
            }
            if (savedInstanceState.containsKey("web_fetch") && webFetch != null) {
                webFetch.setChecked(savedInstanceState.getBoolean("web_fetch"));
            }
            if (savedInstanceState.containsKey("api_spinner_pos") && apiSpinner != null) {
                apiSpinner.setSelection(savedInstanceState.getInt("api_spinner_pos"));
            }

            fetched = savedInstanceState.getBoolean("fetched", false);
            if (fetched && savedInstanceState.containsKey("fetched_models")) {
                fetchedModels = savedInstanceState.getStringArrayList("fetched_models");
                if (fetchedModels != null) {
                    ArrayAdapter<String> fetchedAdapter = new ArrayAdapter<String>(
                            context,
                            android.R.layout.simple_spinner_item,
                            fetchedModels
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
                final String urlByName = ApiManager.getUrlByName(apiSpinner.getSelectedItem().toString());
                if (conf.getBaseUrl().equals(urlByName)) {
                    config.setConfig(new Config(urlByName,
                            keyText.getText().toString(),
                            chatSpinner.getSelectedItem().toString(),
                            thinkSpinner.getSelectedItem().toString(),
                            shrinkThink.isChecked(),
                            systemPrompt,
                            Integer.parseInt(updateDelay.getText().toString()),
                            webSearch.isChecked(),
                            conf.getSearchEngine(),
                            webFetch.isChecked()));
                    Intent intent = new Intent(context, MainActivity.class);
                    startActivity(intent);
                    finish();
                } else if (!fetched) {
                    final Loading loading = new Loading(context);
                    final String orig = config.getConfig().getBaseUrl();
                    config.updateBaseUrl(urlByName);
                    api.getModels(new ApiCallback<ArrayList<String>>() {
                        @Override
                        public void onSuccess(ArrayList<String> models) {
                            config.setConfig(new Config(urlByName,
                                    keyText.getText().toString(),
                                    ModelSelector.selectChatModel(models),
                                    ModelSelector.selectThinkingModel(models),
                                    shrinkThink.isChecked(),
                                    systemPrompt,
                                    Integer.parseInt(updateDelay.getText().toString()),
                                    webSearch.isChecked(),
                                    conf.getSearchEngine(),
                                    webFetch.isChecked()));
                            loading.dismiss();
                            Intent intent = new Intent(context, MainActivity.class);
                            startActivity(intent);
                            finish();
                        }

                        @Override
                        public void onError(ApiError error) {
                            error.printStackTrace();
                            Toast.makeText(context, error.getMessage(), Toast.LENGTH_LONG).show();
                            loading.dismiss();
                            config.updateBaseUrl(orig);
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
        if (shrinkThink != null) {
            outState.putBoolean("shrink_think", shrinkThink.isChecked());
        }
        if (webSearch != null) {
            outState.putBoolean("web_search", webSearch.isChecked());
        }
        if (webFetch != null) {
            outState.putBoolean("web_fetch", webFetch.isChecked());
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

    private void loadModels(final Spinner spinner) {
        if (fetched) {
            spinner.performClick();
            return;
        }
        final Loading loading = new Loading(context);
        api.getModels(new ApiCallback<ArrayList<String>>() {
            @Override
            public void onSuccess(ArrayList<String> result) {
                fetchedModels = result;
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                        context,
                        android.R.layout.simple_spinner_item,
                        result
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                chatSpinner.setAdapter(adapter);
                thinkSpinner.setAdapter(adapter);
                Config conf = config.getConfig();
                chatSpinner.setSelection(result.indexOf(conf.getChatModel()));
                thinkSpinner.setSelection(result.indexOf(conf.getThinkingModel()));
                loading.dismiss();
                fetched = true;
                spinner.performClick();
            }

            @Override
            public void onError(ApiError error) {
                error.printStackTrace();
                loading.dismiss();
                Toast.makeText(context, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}