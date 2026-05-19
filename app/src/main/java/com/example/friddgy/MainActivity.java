package com.example.friddgy;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

        TextView heroTitle = findViewById(R.id.hero_title);
        String fullText = "Healthy food\nis goooood";
        SpannableString spannableString = new SpannableString(fullText);

        int start = fullText.indexOf("goooood");
        int end = start + "goooood".length();
        spannableString.setSpan(new ForegroundColorSpan(accentColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        heroTitle.setText(spannableString);

        Button btnGetStarted = findViewById(R.id.btn_get_started);
        btnGetStarted.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
        
        ThemeManager.applyTouchScaleAnimation(btnGetStarted, () -> {
            startActivity(new Intent(MainActivity.this, HomeActivity.class));
            finish();
        });
    }
}
