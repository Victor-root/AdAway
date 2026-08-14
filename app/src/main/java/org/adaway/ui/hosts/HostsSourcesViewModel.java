package org.adaway.ui.hosts;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostsSourceDao;
import org.adaway.db.entity.HostsSource;
import org.adaway.util.AppExecutors;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * This class is an {@link AndroidViewModel} for the {@link HostsSourcesFragment}.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HostsSourcesViewModel extends AndroidViewModel {
    private static final Executor EXECUTOR = AppExecutors.getInstance().diskIO();
    private final HostsSourceDao hostsSourceDao;
    /**
     * Set on a real user edit only (toggle, or add/edit/delete via {@link #notifyModelChanged()}),
     * unlike {@link #getHostsSources()}: that one is the raw, reactive query result, which
     * re-emits on any write to the table, background syncs included — {@code updateSize}/
     * {@code updateModificationDates} run after every successful sync, user-triggered or not.
     * Driving an "Apply" prompt off it (as this screen used to, on both mobile and TV) means the
     * prompt can appear, and on a TV header button stay stuck showing, from a sync the user never
     * asked to apply, the same way {@link org.adaway.ui.lists.ListsViewModel#getModelChanged()}
     * already avoids for the Lists screen.
     */
    private final MutableLiveData<Boolean> modelChanged;

    public HostsSourcesViewModel(@NonNull Application application) {
        super(application);
        this.hostsSourceDao = AppDatabase.getInstance(application).hostsSourceDao();
        this.modelChanged = new MutableLiveData<>(false);
    }

    public LiveData<List<HostsSource>> getHostsSources() {
        return this.hostsSourceDao.loadAll();
    }

    public LiveData<Boolean> getModelChanged() {
        return this.modelChanged;
    }

    /**
     * Mark a change as pending. For an edit made outside this ViewModel: adding, editing or
     * deleting a source goes through {@code SourceEditActivity}/{@code TvSourceEditActivity}
     * directly, not through here, so the screen that launched one calls this once it reports
     * back that a save or delete actually happened.
     */
    public void notifyModelChanged() {
        this.modelChanged.postValue(true);
    }

    public void toggleSourceEnabled(HostsSource source) {
        EXECUTOR.execute(() -> {
            this.hostsSourceDao.toggleEnabled(source);
            this.modelChanged.postValue(true);
        });
    }
}
