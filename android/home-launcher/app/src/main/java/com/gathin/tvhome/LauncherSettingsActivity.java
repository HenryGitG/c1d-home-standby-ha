package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private static final int PICK_WALLPAPER = 43;
    private static final int READ_IMAGES = 44;
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private int columns;
    private int rowsBelowDock;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        findViewById(R.id.weatherCityChoice).setOnClickListener(v -> showWeatherCityPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            setResult(RESULT_OK);
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_SETTINGS)));
        refresh();
        columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 2];
        labels[0] = getString(R.string.wallpaper_choose_image);
        for (int i = 0; i < images.size(); i++) labels[i + 1] = images.get(i).getName();
        labels[images.size() + 1] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int imageIndex = current == null ? -1 : indexOf(images, current);
        int checked = "user".equals(current) ? 0 : "none".equals(current) ? images.size() + 1
                : imageIndex < 0 ? -1 : imageIndex + 1;
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        try {
                            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                                    .addCategory(Intent.CATEGORY_OPENABLE);
                            startActivityForResult(pick, PICK_WALLPAPER);
                        } catch (ActivityNotFoundException e) {
                            showLocalImagePicker();
                        }
                        return;
                    }
                    if (which == images.size() + 1) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which - 1).getName()).apply();
                    }
                    setResult(RESULT_OK);
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_WALLPAPER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        importWallpaper(data.getData());
    }

    private void showLocalImagePicker() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_IMAGES);
            return;
        }
        showImageDirectory(Environment.getExternalStorageDirectory(), Environment.getExternalStorageDirectory());
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != READ_IMAGES) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) showLocalImagePicker();
        else Toast.makeText(this, R.string.wallpaper_permission_needed, Toast.LENGTH_LONG).show();
    }

    private void showImageDirectory(File root, File directory) {
        File[] entries = directory.listFiles(file -> !file.getName().startsWith(".")
                && (file.isDirectory() || isImage(file)));
        if (entries == null) entries = new File[0];
        Arrays.sort(entries, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            int priority = folderPriority(a.getName()) - folderPriority(b.getName());
            return priority != 0 ? priority : a.getName().compareToIgnoreCase(b.getName());
        });
        boolean hasParent = !directory.equals(root);
        String[] names = new String[entries.length + (hasParent ? 1 : 0)];
        if (hasParent) names[0] = "..";
        for (int i = 0; i < entries.length; i++) {
            names[i + (hasParent ? 1 : 0)] = entries[i].isDirectory()
                    ? getString(R.string.wallpaper_folder, entries[i].getName()) : entries[i].getName();
        }
        File[] selectable = entries;
        new AlertDialog.Builder(this)
                .setTitle(directory.equals(root) ? getString(R.string.wallpaper_label) : directory.getName())
                .setItems(names, (dialog, which) -> {
                    if (hasParent && which == 0) {
                        showImageDirectory(root, directory.getParentFile());
                        return;
                    }
                    File selected = selectable[which - (hasParent ? 1 : 0)];
                    if (selected.isDirectory()) showImageDirectory(root, selected);
                    else importWallpaper(Uri.fromFile(selected));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static boolean isImage(File file) {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        return file.isFile() && (name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".png") || name.endsWith(".webp"));
    }

    private static int folderPriority(String name) {
        if (name.equalsIgnoreCase("Download")) return 0;
        if (name.equalsIgnoreCase("Pictures")) return 1;
        if (name.equalsIgnoreCase("DCIM")) return 2;
        return 3;
    }

    private void importWallpaper(Uri uri) {
        new Thread(() -> {
            boolean saved = false;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(input, null, options);
                }
                if (options.outWidth > 0 && options.outHeight > 0) {
                    options.inJustDecodeBounds = false;
                    options.inPreferredConfig = Bitmap.Config.RGB_565;
                    options.inSampleSize = 1;
                    while (options.outWidth / options.inSampleSize > 2560
                            || options.outHeight / options.inSampleSize > 1440) options.inSampleSize *= 2;
                    Bitmap bitmap;
                    try (InputStream input = getContentResolver().openInputStream(uri)) {
                        bitmap = BitmapFactory.decodeStream(input, null, options);
                    }
                    if (bitmap != null) {
                        File target = new File(getFilesDir(), Config.USER_WALLPAPER);
                        File temp = new File(getFilesDir(), Config.USER_WALLPAPER + ".tmp");
                        try (FileOutputStream output = new FileOutputStream(temp)) {
                            saved = bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output);
                        }
                        bitmap.recycle();
                        if (saved) saved = temp.renameTo(target);
                    }
                }
            } catch (Exception | OutOfMemoryError ignored) { saved = false; }
            boolean ok = saved;
            runOnUiThread(() -> {
                if (ok) {
                    choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "user").apply();
                    setResult(RESULT_OK);
                    refresh();
                }
                Toast.makeText(this, ok ? R.string.wallpaper_applied : R.string.wallpaper_import_failed,
                        Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void showWeatherCityPicker() {
        android.widget.EditText city = new android.widget.EditText(this);
        city.setSingleLine(true);
        city.setHint(R.string.weather_city_hint);
        city.setText(getSharedPreferences("weather", MODE_PRIVATE).getString(WeatherClient.CITY_KEY, ""));
        new AlertDialog.Builder(this).setTitle(R.string.weather_city_label).setView(city)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    getSharedPreferences("weather", MODE_PRIVATE).edit()
                            .putString(WeatherClient.CITY_KEY, city.getText().toString().trim()).apply();
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNeutralButton(R.string.weather_auto, (dialog, which) -> {
                    getSharedPreferences("weather", MODE_PRIVATE).edit().remove(WeatherClient.CITY_KEY).apply();
                    setResult(RESULT_OK);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : "user".equals(wallpaperCurrent) ? getString(R.string.wallpaper_user)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        ((TextView) findViewById(R.id.weatherCityChoice)).setText(getString(R.string.weather_city_state,
                getSharedPreferences("weather", MODE_PRIVATE).getString(WeatherClient.CITY_KEY, "").isEmpty()
                        ? getString(R.string.weather_auto)
                        : getSharedPreferences("weather", MODE_PRIVATE).getString(WeatherClient.CITY_KEY, "")));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
