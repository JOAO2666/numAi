package io.github.gohoski.numai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import io.github.gohoski.numai.api.ApiCallback;
import io.github.gohoski.numai.api.ApiError;
import io.github.gohoski.numai.api.ApiManager;
import io.github.gohoski.numai.api.ApiService;
import io.github.gohoski.numai.data.ConfigManager;
import io.github.gohoski.numai.ui.SettingsHelper;
import io.github.gohoski.numai.util.ModelSelector;
import io.github.gohoski.numai.util.SSLDisabler;

public class FirstTimeActivity extends Activity {
    private ViewFlipper viewFlipper;
    private String apiKey = "";
    private ApiService apiService;
    private ConfigManager apiConfig;
    private Context context;

    private Spinner providerGuideSpinner;
    private TextView guideTitleText;
    private TextView guideContentText;

    private Spinner providerKeySpinner;
    private EditText keyText;

    private boolean modelsLoaded = false;

    private final List<String> rawProviderNames = new ArrayList<String>();
    private final List<String> displayProviderNames = new ArrayList<String>();
    private final List<Integer> guideResources = new ArrayList<Integer>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firsttime);

        apiService = new ApiService(this);
        apiConfig = ConfigManager.getInstance(this);
        SSLDisabler.disableSSLCertificateChecking();
        context = this;

        viewFlipper = (ViewFlipper) findViewById(R.id.view_flipper);
        setupNavigation();

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("api_key")) {
                apiKey = savedInstanceState.getString("api_key");
                if (keyText != null) {
                    keyText.setText(apiKey);
                }
            }
            if (providerGuideSpinner != null && savedInstanceState.containsKey("guide_spinner_pos")) {
                providerGuideSpinner.setSelection(savedInstanceState.getInt("guide_spinner_pos"));
            }
            if (providerKeySpinner != null && savedInstanceState.containsKey("key_spinner_pos")) {
                providerKeySpinner.setSelection(savedInstanceState.getInt("key_spinner_pos"));
            }
            modelsLoaded = savedInstanceState.getBoolean("models_loaded", false);
            if (savedInstanceState.containsKey("displayed_child")) {
                int child = savedInstanceState.getInt("displayed_child");
                viewFlipper.setDisplayedChild(child);
            }
        }

        runScreenSpecificCode();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (viewFlipper != null) {
            outState.putInt("displayed_child", viewFlipper.getDisplayedChild());
        }
        if (keyText != null) {
            outState.putString("api_key", keyText.getText().toString());
        }
        if (providerGuideSpinner != null) {
            outState.putInt("guide_spinner_pos", providerGuideSpinner.getSelectedItemPosition());
        }
        if (providerKeySpinner != null) {
            outState.putInt("key_spinner_pos", providerKeySpinner.getSelectedItemPosition());
        }
        outState.putBoolean("models_loaded", modelsLoaded);
    }

    private int getSdkVersion() {
        try {
            return Integer.parseInt(Build.VERSION.SDK);
        } catch (Exception e) {
            return 1;
        }
    }

    private void setupNavigation() {
        // Welcome
        View screen1 = viewFlipper.getChildAt(0);
        Button skipButton = (Button) screen1.findViewById(R.id.skip);
        Button nextButton1 = (Button) screen1.findViewById(R.id.next);

        skipButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                forceShowScreen(2);
            }
        });

        nextButton1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showNextScreen();
            }
        });

        // provider guide Selection
        View screen2 = viewFlipper.getChildAt(1);
        Button backButton2 = (Button) screen2.findViewById(R.id.back);
        Button nextButton2 = (Button) screen2.findViewById(R.id.next);

        providerGuideSpinner = (Spinner) screen2.findViewById(R.id.provider_guide_spinner);
        guideTitleText = (TextView) screen2.findViewById(R.id.guide_title);
        guideContentText = (TextView) screen2.findViewById(R.id.guide_text);

        rawProviderNames.clear();
        displayProviderNames.clear();
        guideResources.clear();
        boolean isDonut = getSdkVersion() >= 4;
        addProviderOption("VoidAI", isDonut, R.string.guide_voidai);
        addProviderOption("Ollama Cloud", !isDonut, R.string.guide_ollama_cloud);
        addProviderOption("OpenCode Zen", isDonut, R.string.guide_opencode_zen);
        int recommendedIndex = isDonut ? 0 : 1;
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                context,
                android.R.layout.simple_spinner_item,
                displayProviderNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerGuideSpinner.setAdapter(adapter);
        if (providerKeySpinner != null && recommendedIndex < providerKeySpinner.getCount()) {
            providerKeySpinner.setSelection(recommendedIndex);
        }
        providerGuideSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateGuideContent(position);
            } @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        providerGuideSpinner.setSelection(recommendedIndex);

        backButton2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showPreviousScreen();
            }
        });
        nextButton2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int selectedIndex = providerGuideSpinner.getSelectedItemPosition();
                if (providerKeySpinner != null && selectedIndex >= 0 && selectedIndex < providerKeySpinner.getCount()) {
                    providerKeySpinner.setSelection(selectedIndex);
                }
                showNextScreen();
            }
        });

        // API Key Setup
        View screen3 = viewFlipper.getChildAt(2);
        Button backButton3 = (Button) screen3.findViewById(R.id.back);
        Button nextButton3 = (Button) screen3.findViewById(R.id.next);
        keyText = (EditText) screen3.findViewById(R.id.apiKey);
        providerKeySpinner = (Spinner) screen3.findViewById(R.id.provider_spinner);

        SettingsHelper.setupApiSpinner(context, providerKeySpinner, apiConfig, new SettingsHelper.ApiSelectionCallback() {
            @Override
            public void onApiSelected(String api) {
                System.out.println(api);
            }
        });
        if (recommendedIndex < providerKeySpinner.getCount()) {
            providerKeySpinner.setSelection(recommendedIndex);
        }

        backButton3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showPreviousScreen();
            }
        });
        nextButton3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                keyText.clearFocus();
                if (getSdkVersion() >= 3) {
                    try {
                        Class<?> immClass = Class.forName("android.view.inputmethod.InputMethodManager");
                        Object imm = getSystemService(Context.INPUT_METHOD_SERVICE);
                        Method hideMethod = immClass.getMethod(
                                "hideSoftInputFromWindow",
                                android.os.IBinder.class,
                                Integer.TYPE
                        );
                        hideMethod.invoke(imm, keyText.getWindowToken(), 0);
                    } catch (Exception e) {
                        Log.e("FirstTimeActivity", "Failed to hide keyboard via reflection", e);
                    }
                }
                showNextScreen();
            }
        });

        keyText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                apiKey = keyText.getText().toString();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        screen3.findViewById(R.id.from_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("text/plain");
                startActivityForResult(Intent.createChooser(intent, getString(R.string.select_txt)), 2);
            }
        });
    }

    private void addProviderOption(String providerName, boolean isRecommended, int guideResource) {
        rawProviderNames.add(providerName);
        guideResources.add(guideResource);
        if (isRecommended) {
            displayProviderNames.add(getString(R.string.recommended, providerName));
        } else {
            displayProviderNames.add(providerName);
        }
    }

    private void updateGuideContent(int position) {
        guideTitleText.setText(rawProviderNames.get(position));
        guideContentText.setText(guideResources.get(position));
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

    private void showNextScreen() {
        viewFlipper.showNext();
        runScreenSpecificCode();
    }

    private void showPreviousScreen() {
        viewFlipper.showPrevious();
        runScreenSpecificCode();
    }

    private void forceShowScreen(int screen) {
        viewFlipper.setDisplayedChild(screen);
        runScreenSpecificCode();
    }

    private void runScreenSpecificCode() {
        switch (viewFlipper.getDisplayedChild()) {
            case 0:
            case 1:
            case 2:
                break;
            case 3:
                if (modelsLoaded) {
                    ProgressBar loading = (ProgressBar) findViewById(R.id.progress_loader);
                    if (loading != null) loading.setVisibility(View.GONE);

                    TextView title = (TextView) findViewById(R.id.title);
                    if (title != null) {
                        title.setText(R.string.hello_user);
                        title.setTextSize(25f);
                    }
                    ImageView bugdroid = (ImageView) findViewById(R.id.bugdroid);
                    if (bugdroid != null) bugdroid.setVisibility(View.VISIBLE);

                    Button startButton = (Button) findViewById(R.id.startChatting);
                    if (startButton != null) {
                        startButton.setVisibility(View.VISIBLE);
                        startButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Intent intent = new Intent(context, MainActivity.class);
                                startActivity(intent);
                                finish();
                            }
                        });
                    }
                } else {
                    apiConfig.updateApiKey(apiKey);
                    apiConfig.updateBaseUrl(ApiManager.getUrlByName(providerKeySpinner.getSelectedItem().toString()));
                    apiService.getModels(new ApiCallback<ArrayList<String>>() {
                        @Override
                        public void onSuccess(ArrayList<String> models) {
                            try {
                                modelsLoaded = true;
                                apiConfig.updateChatModel(ModelSelector.selectChatModel(models));
                                apiConfig.updateThinkingModel(ModelSelector.selectThinkingModel(models));

                                ProgressBar loading = (ProgressBar) findViewById(R.id.progress_loader);
                                if (loading != null) loading.setVisibility(View.GONE);

                                TextView title = (TextView) findViewById(R.id.title);
                                if (title != null) {
                                    title.setText(R.string.hello_user);
                                    title.setTextSize(25f);
                                }
                                ImageView bugdroid = (ImageView) findViewById(R.id.bugdroid);
                                if (bugdroid != null) bugdroid.setVisibility(View.VISIBLE);

                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        Button startButton = (Button) findViewById(R.id.startChatting);
                                        if (startButton != null) {
                                            Animation fadeIn = new AlphaAnimation(0.0f, 1.0f);
                                            fadeIn.setDuration(1000);
                                            fadeIn.setInterpolator(new AccelerateInterpolator());
                                            startButton.setVisibility(View.VISIBLE);
                                            startButton.startAnimation(fadeIn);
                                            startButton.setOnClickListener(new View.OnClickListener() {
                                                @Override
                                                public void onClick(View view) {
                                                    Intent intent = new Intent(context, MainActivity.class);
                                                    startActivity(intent);
                                                    finish();
                                                }
                                            });
                                        }
                                    }
                                }, 1000);
                            } catch(Exception e) {
                                e.printStackTrace();
                                finish();
                            }
                        }

                        @Override
                        public void onError(ApiError error) {
                            apiConfig.updateApiKey("");
                            error.printStackTrace();
                            Toast.makeText(context, getString(R.string.api_key_error) + " " + error.getMessage(), Toast.LENGTH_LONG).show();
                            showPreviousScreen();
                        }
                    });
                }
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return keyCode != KeyEvent.KEYCODE_HOME || super.onKeyDown(keyCode, event);
    }
}