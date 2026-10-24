package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class StandbyController {
    private static final File PANEL = new File("/proc/msp/panel");

    private StandbyController() {
    }

    static boolean setDisplayPower(boolean on) {
        try (FileOutputStream output = new FileOutputStream(PANEL)) {
            output.write((on ? "power 1\n" : "power 0\n").getBytes(StandardCharsets.US_ASCII));
            output.flush();
            return true;
        } catch (IOException | SecurityException ignored) {
            return false;
        }
    }

    static boolean isDisplayOn() {
        try (BufferedReader reader = new BufferedReader(new FileReader(PANEL))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("power_on")) return line.contains("TRUE");
            }
        } catch (IOException | SecurityException ignored) {
        }
        return true;
    }

    static void openHome(Context context) {
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        context.startActivity(home);
    }
}
