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
                            ivProfile.setImageURI(Uri.fromFile(new File(savedPath)));
                            ivProfile.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            ivProfile.setImageTintList(null); // Clear camera icon tint
                        }
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. First-launch check
        SharedPreferences prefs = getSharedPreferences("friddgy_custom_prefs", MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean("is_first_launch", true);
        if (!isFirstLaunch) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ThemeManager.ThemePreset theme = ThemeManager.getCurrentTheme(this);

        // 3. The hero image is now full-screen (no circular clipping needed)
        // hero_image_container is a zero-size placeholder kept for code compatibility
        View heroContainer = findViewById(R.id.hero_image_container);

        // 4. Iniziamo Button setup and transition
        Button btnGetStarted = findViewById(R.id.btn_get_started);
        
        View layoutSplash = findViewById(R.id.layout_splash);
        View layoutProfileSetup = findViewById(R.id.layout_profile_setup);

        ThemeManager.applyTouchScaleAnimation(btnGetStarted, () -> {
            if (layoutSplash != null && layoutProfileSetup != null) {
                // Smooth transition
                layoutSplash.animate().alpha(0.0f).translationY(-50f).setDuration(300).withEndAction(() -> {
                    layoutSplash.setVisibility(View.GONE);
                    layoutProfileSetup.setVisibility(View.VISIBLE);
                    layoutProfileSetup.setAlpha(0.0f);
                    layoutProfileSetup.setTranslationY(50f);
                    layoutProfileSetup.animate().alpha(1.0f).translationY(0f).setDuration(350).start();
                }).start();
            }
        });

        // 5. Setup Profile UI styling and dynamic accents
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

                // Save name and avatar url in ThemeManager SharedPreferences
                ThemeManager.setUserName(MainActivity.this, name);
                if (selectedImageUriString != null) {
                    ThemeManager.setAvatarUrl(MainActivity.this, selectedImageUriString);
                }

                // Transition to API Key Setup screen
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

        // Setup API Key listeners
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

                // Save API key
                ThemeManager.setGeminiApiKey(MainActivity.this, apiKey);

                // Mark first launch as complete
                prefs.edit().putBoolean("is_first_launch", false).apply();

                // Open home and finish
                startActivity(new Intent(MainActivity.this, HomeActivity.class));
                finish();
            });
        }

        if (btnSkipApiKey != null) {
            btnSkipApiKey.setOnClickListener(v -> {
                // Clear or leave API key empty
                ThemeManager.setGeminiApiKey(MainActivity.this, "");

                // Mark first launch as complete
                prefs.edit().putBoolean("is_first_launch", false).apply();

                // Open home and finish
                startActivity(new Intent(MainActivity.this, HomeActivity.class));
                finish();
            });
        }

        // 6. Theme swatches setup for onboarding
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

        // Apply initially selected theme accent to setup controls
        applySetupAccentTheme();
    }

    private void applySetupAccentTheme() {
        ThemeManager.ThemePreset theme = ThemeManager.getCurrentTheme(this);
        int accentColor = Color.parseColor(theme.accentColor);

        // 1. Hero title highlight "buoooono"
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

        // 2. Iniziamo Button tint
        Button btnGetStarted = findViewById(R.id.btn_get_started);
        if (btnGetStarted != null) {
            btnGetStarted.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        }

        // 3. Confirm Setup Button tint
        Button btnConfirmSetup = findViewById(R.id.btn_confirm_setup);
        if (btnConfirmSetup != null) {
            btnConfirmSetup.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        }

        // 4. Setup Profile Image Container border color
        View imageContainer = findViewById(R.id.setup_profile_image_container);
        if (imageContainer != null) {
            GradientDrawable border = new GradientDrawable();
            border.setShape(GradientDrawable.OVAL);
            border.setColor(accentColor);
            imageContainer.setBackground(border);
        }

        // 5. Setup Edit Badge Container tint
        View setupBadgeContainer = findViewById(R.id.setup_badge_container);
        if (setupBadgeContainer != null) {
            setupBadgeContainer.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        }

        // 6. API Setup Elements
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
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            File file = new File(getFilesDir(), "profile_picture.jpg");
            OutputStream out = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.close();
            in.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
