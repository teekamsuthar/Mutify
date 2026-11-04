package live.teekamsuthar.mutify;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class MutifySettingsTest {

    private final FakePreferences prefs = new FakePreferences();
    private final MutifySettings settings = new MutifySettings(prefs);
    private final List<String> changedKeys = new ArrayList<>();

    @Test
    public void thePreferencesHoldTheSettingsThemselvesAsTheirListener() {
        // SharedPreferences hold listeners weakly; a separate listener object kept in a field was
        // removed by R8 in release builds, collected, and no setting change arrived any more
        assertEquals(1, prefs.listeners.size());
        assertSame(settings, prefs.listeners.keySet().iterator().next());
    }

    @Test
    public void switchingOffAndOnReachesTheListenersAfterAGarbageCollection() {
        settings.addListener(changedKeys::add);
        settings.setEnabled(true);
        collectGarbage();

        settings.setEnabled(false);
        settings.setEnabled(true);

        assertEquals(listOf(MutifySettings.KEY_ENABLED, MutifySettings.KEY_ENABLED, MutifySettings.KEY_ENABLED),
                changedKeys);
    }

    @Test
    public void anAdVolumeStepChangeReachesTheListenersAfterAGarbageCollection() {
        settings.addListener(changedKeys::add);
        collectGarbage();

        settings.setAdVolumeStep(2);

        assertEquals(listOf(MutifySettings.KEY_AD_VOLUME_STEP), changedKeys);
        assertEquals(2, settings.getAdVolumeStep());
    }

    @Test
    public void otherPreferencesAreNotPassedOn() {
        settings.addListener(changedKeys::add);

        prefs.edit().putBoolean("hide_tips", true).apply();

        assertTrue(changedKeys.isEmpty());
    }

    private static List<String> listOf(String... keys) {
        List<String> list = new ArrayList<>();
        for (String key : keys) {
            list.add(key);
        }
        return list;
    }

    private static void collectGarbage() {
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
    }

    /** Just enough of SharedPreferences, holding its listeners weakly as Android does. */
    private static final class FakePreferences implements SharedPreferences {

        final Map<OnSharedPreferenceChangeListener, Object> listeners = new WeakHashMap<>();
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<>(values);
        }

        @Nullable
        @Override
        public String getString(String key, @Nullable String defValue) {
            return values.containsKey(key) ? (String) values.get(key) : defValue;
        }

        @Nullable
        @Override
        public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getInt(String key, int defValue) {
            return values.containsKey(key) ? (Integer) values.get(key) : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public float getFloat(String key, float defValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return values.containsKey(key) ? (Boolean) values.get(key) : defValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new FakeEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            listeners.put(listener, this);
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
            listeners.remove(listener);
        }

        /** Applies the changes and, like Android, tells the listeners about each changed key. */
        private void write(Map<String, Object> changes) {
            List<String> changed = new ArrayList<>();
            for (Map.Entry<String, Object> change : changes.entrySet()) {
                String key = change.getKey();
                Object value = change.getValue();
                boolean present = values.containsKey(key);
                if (value == null ? present : !present || !Objects.equals(values.get(key), value)) {
                    if (value == null) {
                        values.remove(key);
                    } else {
                        values.put(key, value);
                    }
                    changed.add(key);
                }
            }
            List<OnSharedPreferenceChangeListener> current = new ArrayList<>(listeners.keySet());
            for (String key : changed) {
                for (OnSharedPreferenceChangeListener listener : current) {
                    listener.onSharedPreferenceChanged(this, key);
                }
            }
        }

        private final class FakeEditor implements Editor {

            /** Key to new value; null removes the key. */
            private final Map<String, Object> changes = new HashMap<>();

            @Override
            public Editor putString(String key, @Nullable String value) {
                changes.put(key, value);
                return this;
            }

            @Override
            public Editor putStringSet(String key, @Nullable Set<String> values) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Editor putInt(String key, int value) {
                changes.put(key, value);
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Editor putFloat(String key, float value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                changes.put(key, value);
                return this;
            }

            @Override
            public Editor remove(String key) {
                changes.put(key, null);
                return this;
            }

            @Override
            public Editor clear() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean commit() {
                apply();
                return true;
            }

            @Override
            public void apply() {
                write(changes);
            }
        }
    }
}
