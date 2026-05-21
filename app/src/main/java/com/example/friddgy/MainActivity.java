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
        int accentColor = Color.parseColor(theme.accentColor);

        // 2. Set hero title and highlight "buoooono"
        TextView heroTitle = findViewById(R.id.hero_title);
        String fullText = "Il cibo sano\nè buoooono";
        SpannableString spannableString = new SpannableString(fullText);

        int start = fullText.indexOf("buoooono");
        int end = start + "buoooono".length();
        spannableString.setSpan(new ForegroundColorSpan(accentColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        heroTitle.setText(spannableString);

        // 3. The hero image is now full-screen (no circular clipping needed)
        // hero_image_container is a zero-size placeholder kept for code compatibility
        View heroContainer = findViewById(R.id.hero_image_container);

        // 4. Iniziamo Button setup and transition
        Button btnGetStarted = findViewById(R.id.btn_get_started);
        btnGetStarted.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        
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

        if (imageContainer != null) {
            imageContainer.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            imageContainer.setClipToOutline(true);

            GradientDrawable border = new GradientDrawable();
            border.setShape(GradientDrawable.OVAL);
            border.setColor(accentColor);
            imageContainer.setBackground(border);
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
            btnConfirmSetup.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
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

                // Mark first launch as complete
                prefs.edit().putBoolean("is_first_launch", false).apply();

                // Open home and finish
                startActivity(new Intent(MainActivity.this, HomeActivity.class));
                finish();
            });
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
