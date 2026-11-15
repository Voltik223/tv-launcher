package ru.tvlauncher.lite;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Минимальный лаунчер для Android TV (Android 7.0+).
 * Только плитки приложений, часы и кнопка системных настроек.
 * Без служб, анимаций и сторонних библиотек.
 */
public class MainActivity extends Activity {

    /**
     * false - показывать все установленные приложения (избранные идут первыми);
     * true  - показывать только приложения из списка PREFERRED.
     */
    private static final boolean ONLY_PREFERRED = false;

    /** Приоритетные приложения (порядок = порядок плиток). */
    private static final String[] PREFERRED = {
            "com.google.android.youtube.tv",
            "com.google.android.youtube",
            "com.spotify.tv.android",
            "com.spotify.music",
            "com.phlox.tvwebbrowser",      // TV Bro (лёгкий браузер)
            "com.cloudmosa.puffinTV",      // Puffin TV Browser
            "org.mozilla.tv.firefox",
            "org.mozilla.firefox",
            "com.android.chrome"
    };

    /** Не показывать (настройки открываются отдельной кнопкой, сам лаунчер - тоже скрыт). */
    private static final String[] HIDDEN = {
            "com.android.settings",
            "com.android.tv.settings"
    };

    private GridView grid;
    private AppAdapter adapter;
    private PackageManager pm;
    private String signature = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pm = getPackageManager();

        grid = findViewById(R.id.grid);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launch(adapter.getItem(position));
            }
        });

        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
        if (!grid.hasFocus()) {
            grid.requestFocus();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Нажатие HOME внутри лаунчера: возвращаем фокус на первую плитку
        if (grid != null && adapter.getCount() > 0) {
            grid.setSelection(0);
            grid.requestFocus();
        }
    }

    /** Кнопка "Назад" на главном экране ничего не делает. */
    @Override
    public void onBackPressed() {
    }

    private void loadApps() {
        List<AppItem> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String self = getPackageName();
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};

        for (String category : categories) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
            for (ResolveInfo ri : found) {
                ActivityInfo ai = ri.activityInfo;
                String pkg = ai.packageName;
                if (pkg.equals(self) || isHidden(pkg) || seen.contains(pkg)) continue;
                if (ONLY_PREFERRED && rank(pkg) >= PREFERRED.length) continue;
                seen.add(pkg);
                AppItem item = new AppItem();
                item.info = ri;
                item.label = String.valueOf(ri.loadLabel(pm));
                item.component = new ComponentName(pkg, ai.name);
                item.rank = rank(pkg);
                list.add(item);
            }
        }

        Collections.sort(list, new Comparator<AppItem>() {
            @Override
            public int compare(AppItem a, AppItem b) {
                if (a.rank != b.rank) return a.rank < b.rank ? -1 : 1;
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        StringBuilder sb = new StringBuilder();
        for (AppItem i : list) sb.append(i.component.flattenToShortString()).append(';');
        String newSig = sb.toString();
        if (newSig.equals(signature)) return; // список не изменился
        signature = newSig;

        adapter.setItems(list);
        if (!list.isEmpty()) grid.setSelection(0);
    }

    private static int rank(String pkg) {
        for (int i = 0; i < PREFERRED.length; i++) {
            if (PREFERRED[i].equals(pkg)) return i;
        }
        return PREFERRED.length;
    }

    private static boolean isHidden(String pkg) {
        for (String h : HIDDEN) {
            if (h.equals(pkg)) return true;
        }
        return false;
    }

    private void launch(AppItem item) {
        if (item == null) return;
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(item.component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.err_launch, Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e1) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName("com.android.tv.settings",
                        "com.android.tv.settings.MainSettings"));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e2) {
                Toast.makeText(this, R.string.err_settings, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class AppItem {
        ResolveInfo info;
        String label;
        ComponentName component;
        Drawable icon;
        int rank;
    }

    private class AppAdapter extends BaseAdapter {
        private List<AppItem> items = new ArrayList<>();

        void setItems(List<AppItem> newItems) {
            items = newItems;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public AppItem getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = getLayoutInflater().inflate(R.layout.item_app, parent, false);
            }
            AppItem item = items.get(position);
            if (item.icon == null) {
                item.icon = item.info.loadIcon(pm); // лениво, только для видимых плиток
            }
            ((ImageView) v.findViewById(R.id.icon)).setImageDrawable(item.icon);
            ((TextView) v.findViewById(R.id.label)).setText(item.label);
            return v;
        }
    }
}
