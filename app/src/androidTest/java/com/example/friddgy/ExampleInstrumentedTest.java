package com.example.friddgy;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * test strumentato che viene eseguito su un dispositivo android.
 *
 * @see <a href="http://d.android.com/tools/testing">documentazione dei test</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // contesto dell'app sotto test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.example.friddgy", appContext.getPackageName());
    }
}