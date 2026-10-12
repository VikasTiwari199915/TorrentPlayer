package com.vikas.torrentplayer.ui.downloads;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.vikas.torrentplayer.R;
import com.vikas.torrentplayer.api.models.SearchResult;
import com.vikas.torrentplayer.api.models.TorrentItem;
import com.vikas.torrentplayer.db.AppDatabase;
import com.vikas.torrentplayer.db.Favorites;
import com.vikas.torrentplayer.db.TorBoxEntity;
import com.vikas.torrentplayer.torbox.TorBoxManager;
import com.vikas.torrentplayer.ui.detail.DetailActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DownloadUiTest {
    @Test public void favoriteIsVisibleAndPersistsAddRemove() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SearchResult result = new SearchResult();
        result.id = 987654321; result.title = "Favorite UI test"; result.contentType = "movie";
        result.torrents = Collections.singletonList(new TorrentItem());
        AppDatabase.get(context).favoriteDao().delete(Favorites.key(result));
        Intent intent = new Intent(context, DetailActivity.class).putExtra("extra_result", result);
        try (ActivityScenario<DetailActivity> screen = ActivityScenario.launch(intent)) {
            waitUntil(() -> {
                AtomicBoolean visible = new AtomicBoolean();
                screen.onActivity(a -> {
                    View button = a.findViewById(R.id.action_favorite);
                    visible.set(button != null && button.isShown());
                });
                return visible.get();
            });
            screenshot("favorite-action");
            screen.onActivity(a -> ((MaterialToolbar) a.findViewById(R.id.toolbar)).getMenu()
                    .performIdentifierAction(R.id.action_favorite, 0));
            waitUntil(() -> AppDatabase.get(context).favoriteDao().getAll().stream().anyMatch(f -> Favorites.matches(f, result)));
            waitUntil(() -> {
                AtomicBoolean checked = new AtomicBoolean();
                screen.onActivity(a -> checked.set(((MaterialToolbar) a.findViewById(R.id.toolbar))
                        .getMenu().findItem(R.id.action_favorite).isChecked()));
                return checked.get();
            });
            screen.onActivity(a -> ((MaterialToolbar) a.findViewById(R.id.toolbar)).getMenu()
                    .performIdentifierAction(R.id.action_favorite, 0));
            waitUntil(() -> AppDatabase.get(context).favoriteDao().getAll().stream().noneMatch(f -> Favorites.matches(f, result)));
        } finally { AppDatabase.get(context).favoriteDao().delete(Favorites.key(result)); }
    }

    @Test public void groupedDetailsShowsOnlyRelevantTabsAndExpandableFiles() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        long id = 987654322;
        File first = new File(context.getCacheDir(), "torbox-ui-test-one.txt");
        File second = new File(context.getCacheDir(), "torbox-ui-test-two.txt");
        try {
            try (FileOutputStream out = new FileOutputStream(first)) { out.write(new byte[45]); }
            try (FileOutputStream out = new FileOutputStream(second)) { out.write(new byte[10]); }
            for (int index = 1; index <= 2; index++) {
                TorBoxEntity file = new TorBoxEntity();
                file.key = "tb:" + id + ":" + index; file.torrentId = id; file.fileId = index;
                file.title = file.torrentName = "Example season";
                file.fileName = "Season 1/Subtitles/Episode 0" + index + " complete descriptive filename.en.srt";
                file.filePath = (index == 1 ? first : second).getAbsolutePath();
                file.sizeBytes = index == 1 ? 90 : 10;
                file.lastState = index == 1 ? TorBoxManager.State.PAUSED.ordinal() : TorBoxManager.State.DONE.ordinal();
                AppDatabase.get(context).torBoxDao().upsert(file);
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> TorBoxManager.get().init(context));
            waitUntil(() -> {
                AtomicBoolean found = new AtomicBoolean();
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> found.set(
                        TorBoxManager.get().torrentDownloads().getValue().stream().anyMatch(g -> g.id == id
                                && g.files.size() == 2 && g.percent == 55 && g.state == TorBoxManager.State.PAUSED)));
                return found.get();
            });
            try (ActivityScenario<TorBoxDownloadDetailsActivity> screen = ActivityScenario.launch(
                    new Intent(context, TorBoxDownloadDetailsActivity.class).putExtra("torbox_id", id))) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                screenshot("torbox-info");
                screen.onActivity(a -> {
                    assertEquals(2, ((TabLayout) a.findViewById(R.id.tabs)).getTabCount());
                    ((ViewPager2) a.findViewById(R.id.pager)).setCurrentItem(1, false);
                });
                waitUntil(() -> {
                    AtomicBoolean ready = new AtomicBoolean();
                    screen.onActivity(a -> ready.set(findFiles(a.findViewById(R.id.root)) != null));
                    return ready.get();
                });
                screenshot("torbox-files");
                screen.onActivity(a -> {
                    RecyclerView list = findFiles(a.findViewById(R.id.root));
                    assertEquals(4, list.getAdapter().getItemCount());
                    list.findViewHolderForAdapterPosition(0).itemView.performClick();
                });
                waitUntil(() -> {
                    AtomicBoolean collapsed = new AtomicBoolean();
                    screen.onActivity(a -> collapsed.set(findFiles(a.findViewById(R.id.root)).getAdapter().getItemCount() == 1));
                    return collapsed.get();
                });
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> TorBoxManager.get().removeTorrent(id));
            AppDatabase.get(context).torBoxDao().deleteByKey("tb:" + id + ":1");
            AppDatabase.get(context).torBoxDao().deleteByKey("tb:" + id + ":2");
            first.delete(); second.delete();
        }
    }
    private static RecyclerView findFiles(View view) {
        if (view instanceof RecyclerView && ((RecyclerView) view).getAdapter() instanceof TorBoxFilesAdapter
                && ((RecyclerView) view).getChildCount() > 0) return (RecyclerView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                RecyclerView found = findFiles(group.getChildAt(i)); if (found != null) return found;
            }
        }
        return null;
    }
    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) Thread.sleep(50);
        assertTrue("Timed out waiting for UI/data", condition.getAsBoolean());
    }
    private static void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Thread.sleep(400); // Let the activity enter animation finish before capturing.
        android.graphics.Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        File directory = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "ui-test-screenshots");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Screenshot directory unavailable");
        try (FileOutputStream out = new FileOutputStream(new File(directory, name + ".png"))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        } finally { bitmap.recycle(); }
    }
}
