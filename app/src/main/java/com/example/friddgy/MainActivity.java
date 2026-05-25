package com.example.friddgy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import com.bumptech.glide.Glide;

public class MainActivity extends AppCompatActivity {

    private String selectedImageUriString = null;
    
    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    String savedPath = saveProfileImage(uri);
                    if (savedPath != null) {
                        selectedImageUriString = savedPath;
                        ImageView ivProfile = findViewById(R.id.setup_iv_profile);
                        if (ivProfile != null) {
                            Glide.with(this)
                                    .load(savedPath)
                                    .signature(new com.bumptech.glide.signature.ObjectKey(System.currentTimeMillis()))
                                    .placeholder(android.R.drawable.ic_menu_gallery)
                                    .error(android.R.drawable.ic_menu_gallery)
                                    .into(ivProfile);
                            ivProfile.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            ivProfile.setImageTintList(null); // rimuove la tinta dell'icona fotocamera
                        }
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // controllo se è il primo avvio dell'app
        SharedPreferences prefs = getSharedPreferences("friddgy_custom_prefs", MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean("is_first_launch", true);
        if (!isFirstLaunch) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ThemeManager.ThemePreset theme = ThemeManager.getCurrentTheme(this);

        // l'immagine hero è a schermo intero
        // contenitore tenuto solo per compatibilità
        View heroContainer = findViewById(R.id.hero_image_container);

        // configurazione del tasto inizia e transizione
        Button btnGetStarted = findViewById(R.id.btn_get_started);
        
        View layoutSplash = findViewById(R.id.layout_splash);
        View layoutProfileSetup = findViewById(R.id.layout_profile_setup);

        ThemeManager.applyTouchScaleAnimation(btnGetStarted, () -> {
            if (layoutSplash != null && layoutProfileSetup != null) {
                // transizione fluida
                layoutSplash.animate().alpha(0.0f).translationY(-50f).setDuration(300).withEndAction(() -> {
                    layoutSplash.setVisibility(View.GONE);
                    layoutProfileSetup.setVisibility(View.VISIBLE);
                    layoutProfileSetup.setAlpha(0.0f);
                    layoutProfileSetup.setTranslationY(50f);
                    layoutProfileSetup.animate().alpha(1.0f).translationY(0f).setDuration(350).start();
                }).start();
            }
        });

        // configurazione interfaccia del profilo
        View imageContainer = findViewById(R.id.setup_profile_image_container);
        ImageView ivProfile = findViewById(R.id.setup_iv_profile);
        View btnPickImage = findViewById(R.id.btn_pick_image);
        EditText etName = findViewById(R.id.setup_et_name);
        Button btnConfirmSetup = findViewById(R.id.btn_confirm_setup);

        View layoutApiSetup = findViewById(R.id.layout_api_setup);
        EditText etApiKey = findViewById(R.id.setup_et_api_key);
        TextView btnGetApiKeyLink = findViewById(R.id.btn_get_api_key_link);
        Button btnFinishSetup = findViewById(R.id.btn_finish_setup);
        TextView btnSkipApiKey = findViewById(R.id.btn_skip_api_key);

        if (imageContainer != null) {
            imageContainer.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            imageContainer.setClipToOutline(true);
        }

        if (ivProfile != null) {
            ivProfile.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            ivProfile.setClipToOutline(true);
        }

        if (btnPickImage != null) {
            btnPickImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        }

        if (btnConfirmSetup != null) {
            ThemeManager.applyTouchScaleAnimation(btnConfirmSetup, () -> {
                String name = etName != null ? etName.getText().toString().trim() : "";
                if (name.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Inserisci un nome utente per continuare", Toast.LENGTH_SHORT).show();
                    return;
                }

                // salva nome e avatar nelle impostazioni
                ThemeManager.setUserName(MainActivity.this, name);
                if (selectedImageUriString != null) {
                    ThemeManager.setAvatarUrl(MainActivity.this, selectedImageUriString);
                }

                // passa alla schermata di configurazione delle api
                if (layoutProfileSetup != null && layoutApiSetup != null) {
                    layoutProfileSetup.animate().alpha(0.0f).translationY(-50f).setDuration(300).withEndAction(() -> {
                        layoutProfileSetup.setVisibility(View.GONE);
                        layoutApiSetup.setVisibility(View.VISIBLE);
                        layoutApiSetup.setAlpha(0.0f);
                        layoutApiSetup.setTranslationY(50f);
                        layoutApiSetup.animate().alpha(1.0f).translationY(0f).setDuration(350).start();
                    }).start();
                }
            });
        }

        // configurazione tasti per la chiave api
        if (btnGetApiKeyLink != null) {
            btnGetApiKeyLink.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/"));
                startActivity(intent);
            });
        }

        if (btnFinishSetup != null) {
            ThemeManager.applyTouchScaleAnimation(btnFinishSetup, () -> {
                String apiKey = etApiKey != null ? etApiKey.getText().toString().trim() : "";
                if (apiKey.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Inserisci una API Key o clicca su 'Configura più tardi' per saltare", Toast.LENGTH_LONG).show();
                    return;
                }

                // salva la chiave api
                ThemeManager.setGeminiApiKey(MainActivity.this, apiKey);

                // segna il primo avvio come completato
                prefs.edit().putBoolean("is_first_launch", false).apply();

                // apre la home e chiude questa schermata
                startActivity(new Intent(MainActivity.this, HomeActivity.class));
                finish();
            });
        }

        if (btnSkipApiKey != null) {
            btnSkipApiKey.setOnClickListener(v -> {
                // lascia vuota o pulisci la chiave api
                ThemeManager.setGeminiApiKey(MainActivity.this, "");

                // segna il primo avvio come completato
                prefs.edit().putBoolean("is_first_launch", false).apply();

                // apre la home e chiude questa schermata
                startActivity(new Intent(MainActivity.this, HomeActivity.class));
                finish();
            });
        }

        // configurazione dei selettori di tema per l'onboarding
        LinearLayout setupLayoutThemes = findViewById(R.id.setup_layout_themes);
        final String[] selectedTheme = {ThemeManager.getCurrentTheme(this).name};
        if (setupLayoutThemes != null) {
            setupLayoutThemes.removeAllViews();
            for (ThemeManager.ThemePreset preset : ThemeManager.PRESETS) {
                View swatchView = new View(this);
                int size = (int) (40 * getResources().getDisplayMetrics().density);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMargins(16, 8, 16, 8);
                swatchView.setLayoutParams(lp);

                GradientDrawable gd = new GradientDrawable();
                gd.setShape(GradientDrawable.OVAL);
                gd.setColor(Color.parseColor(preset.accentColor));

                if (preset.name.equals(selectedTheme[0])) {
                    gd.setStroke(6, Color.WHITE);
                } else {
                    gd.setStroke(2, Color.parseColor("#444444"));
                }
                swatchView.setBackground(gd);

                swatchView.setOnClickListener(v -> {
                    selectedTheme[0] = preset.name;
                    ThemeManager.setCurrentTheme(MainActivity.this, preset.name);
                    applySetupAccentTheme();

                    for (int i = 0; i < setupLayoutThemes.getChildCount(); i++) {
                        View child = setupLayoutThemes.getChildAt(i);
                        ThemeManager.ThemePreset p = ThemeManager.PRESETS.get(i);
                        GradientDrawable childGd = (GradientDrawable) child.getBackground();
                        if (childGd != null) {
                            if (p.name.equals(selectedTheme[0])) {
                                childGd.setStroke(6, Color.WHITE);
                            } else {
                                childGd.setStroke(2, Color.parseColor("#444444"));
                            }
                        }
                    }
                });

                setupLayoutThemes.addView(swatchView);
            }
        }

        // applica il tema selezionato ai controlli di configurazione
        applySetupAccentTheme();
    }

    private void applySetupAccentTheme() {
        ThemeManager.ThemePreset theme = ThemeManager.getCurrentTheme(this);
        int accentColor = Color.parseColor(theme.accentColor);

        // evidenzia la parola buoooono nel titolo
        TextView heroTitle = findViewById(R.id.hero_title);
        if (heroTitle != null) {
            String fullText = "Il cibo sano\nè buoooono";
            SpannableString spannableString = new SpannableString(fullText);
            int start = fullText.indexOf("buoooono");
            if (start != -1) {
                int end = start + "buoooono".length();
                spannableString.setSpan(new ForegroundColorSpan(accentColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                heroTitle.setText(spannableString);
            }
        }

        // imposta il colore per il tasto inizia
        Button btnGetStarted = findViewById(R.id.btn_get_started);
        if (btnGetStarted != null) {
            btnGetStarted.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        }

        // imposta il colore per il tasto conferma
        Button btnConfirmSetup = findViewById(R.id.btn_confirm_setup);
        if (btnConfirmSetup != null) {
            btnConfirmSetup.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        }

        // imposta il bordo per l'immagine di profilo
        View imageContainer = findViewById(R.id.setup_profile_image_container);
        if (imageContainer != null) {
            GradientDrawable border = new GradientDrawable();
            border.setShape(GradientDrawable.OVAL);
            border.setColor(accentColor);
            imageContainer.setBackground(border);
        }

        // imposta il colore del badge di modifica
        View setupBadgeContainer = findViewById(R.id.setup_badge_container);
        if (setupBadgeContainer != null) {
            setupBadgeContainer.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        }

        // elementi di configurazione api
        Button btnFinishSetup = findViewById(R.id.btn_finish_setup);
        if (btnFinishSetup != null) {
            btnFinishSetup.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        }

        TextView btnGetApiKeyLink = findViewById(R.id.btn_get_api_key_link);
        if (btnGetApiKeyLink != null) {
            btnGetApiKeyLink.setTextColor(accentColor);
        }

        TextView setupApiTutorialHeader = findViewById(R.id.setup_api_tutorial_header);
        if (setupApiTutorialHeader != null) {
            setupApiTutorialHeader.setTextColor(accentColor);
        }

        TextView setupApiTitle = findViewById(R.id.setup_api_title);
        if (setupApiTitle != null) {
            String fullText = "Configura l'IA";
            SpannableString spannableString = new SpannableString(fullText);
            int start = fullText.indexOf("IA");
            if (start != -1) {
                int end = start + "IA".length();
                spannableString.setSpan(new ForegroundColorSpan(accentColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                setupApiTitle.setText(spannableString);
            }
        }
    }

    private String saveProfileImage(Uri uri) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            File file = new File(getFilesDir(), "profile_picture.jpg");
            out = new FileOutputStream(file);
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            try { if (in != null) in.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                android.graphics.Rect outRect = new android.graphics.Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }
}
