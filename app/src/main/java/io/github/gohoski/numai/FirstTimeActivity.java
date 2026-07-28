package io.github.gohoski.numai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import java.util.Scanner;

import io.github.gohoski.numai.api.ApiCallback;
import io.github.gohoski.numai.api.ApiError;
import io.github.gohoski.numai.api.ApiManager;
import io.github.gohoski.numai.api.ApiService;
import io.github.gohoski.numai.data.ConfigManager;
import io.github.gohoski.numai.ui.Loading;
import io.github.gohoski.numai.ui.SettingsHelper;
import io.github.gohoski.numai.util.ModelSelector;
import io.github.gohoski.numai.util.SSLDisabler;

public class FirstTimeActivity extends Activity {
    private ViewFlipper viewFlipper;
    private String apiKey="";
    private ApiService apiService;
    private ConfigManager apiConfig;
    private Context context;
    private Spinner spinner;
    private EditText keyText;

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
        runScreenSpecificCode();
    }

    private void setupNavigation() {
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

        View screen2 = viewFlipper.getChildAt(1);
        Button backButton2 = (Button) screen2.findViewById(R.id.back);
        Button nextButton2 = (Button) screen2.findViewById(R.id.next);

        backButton2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showPreviousScreen();
            }
        });
        nextButton2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showNextScreen();
            }
        });


        View screen3 = viewFlipper.getChildAt(2);
        Button backButton3 = (Button) screen3.findViewById(R.id.back);
        Button nextButton3 = (Button) screen3.findViewById(R.id.next);
        keyText = (EditText) screen3.findViewById(R.id.apiKey);
        spinner = (Spinner) findViewById(R.id.provider_spinner);
        SettingsHelper.setupApiSpinner(context, spinner, apiConfig, new SettingsHelper.ApiSelectionCallback() {
            @Override
            public void onApiSelected(String api) {
                System.out.println(api);
            }
        });

        backButton3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showPreviousScreen();
            }
        });
        nextButton3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                keyText.clearFocus();
                if (Integer.parseInt(Build.VERSION.SDK) >= 3) {
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
            case 2:
                break;
            case 1:
                if (Integer.parseInt(Build.VERSION.SDK) < 9) {
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.cloudflare_title)
                        .setMessage(R.string.cloudflare_msg)
                        .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {}
                        })
                        .show();
                }
                break;
            case 3:
                apiConfig.updateApiKey(apiKey);
                apiConfig.updateBaseUrl(ApiManager.getUrlByName(spinner.getSelectedItem().toString()));
                apiService.getModels(new ApiCallback<ArrayList<String>>() {
                    @Override
                    public void onSuccess(ArrayList<String> models) {
                        try {
                            apiConfig.updateChatModel(ModelSelector.selectChatModel(models));
                            apiConfig.updateThinkingModel(spinner.getSelectedItem().toString().equals("VoidAI") ? "gemini-3-flash-preview" : ModelSelector.selectThinkingModel(models));

                            ProgressBar loading = (ProgressBar) findViewById(R.id.progress_loader);
                            loading.setVisibility(View.GONE);

                            TextView title = (TextView) findViewById(R.id.title);
                            title.setText(R.string.hello_user);
                            title.setTextSize(25f);
                            ImageView bugdroid = (ImageView) findViewById(R.id.bugdroid);
                            bugdroid.setVisibility(View.VISIBLE);

                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    Button startButton = (Button) findViewById(R.id.startChatting);
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
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return keyCode!=KeyEvent.KEYCODE_HOME || super.onKeyDown(keyCode, event);
    }
}
