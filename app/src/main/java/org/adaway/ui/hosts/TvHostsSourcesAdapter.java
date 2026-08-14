package org.adaway.ui.hosts;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.adaway.R;
import org.adaway.db.entity.HostsSource;

import java.time.Duration;
import java.time.ZonedDateTime;

/**
 * The {@link RecyclerView.Adapter} for {@link TvHostsSourcesActivity}'s source list.
 * <p>
 * Deliberately not {@link HostsSourcesAdapter}: that one is built around a checkbox to toggle a
 * source and a plain click to edit it, and here every row is a single click that opens a dialog
 * for both, the same way {@link org.adaway.ui.lists.TvListsAdapter} replaces the equivalent
 * checkbox-plus-click pair on the Lists screen. The status-text and host-count formatting is
 * duplicated from {@code HostsSourcesAdapter} rather than shared (it is private there, and small
 * enough that adding a shared home for it is not worth the extra coupling).
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class TvHostsSourcesAdapter extends ListAdapter<HostsSource, TvHostsSourcesAdapter.ViewHolder> {
    private static final DiffUtil.ItemCallback<HostsSource> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<HostsSource>() {
                @Override
                public boolean areItemsTheSame(@NonNull HostsSource oldSource, @NonNull HostsSource newSource) {
                    return oldSource.getUrl().equals(newSource.getUrl());
                }

                @Override
                public boolean areContentsTheSame(@NonNull HostsSource oldSource, @NonNull HostsSource newSource) {
                    return oldSource.equals(newSource);
                }
            };
    private static final String[] QUANTITY_PREFIXES = new String[]{"k", "M", "G"};

    interface OnItemClickListener {
        void onItemClick(HostsSource source);
    }

    private final Context context;
    private final OnItemClickListener onItemClickListener;

    TvHostsSourcesAdapter(Context context, OnItemClickListener onItemClickListener) {
        super(DIFF_CALLBACK);
        this.context = context;
        this.onItemClickListener = onItemClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tv_item_source, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HostsSource source = getItem(position);
        holder.labelTextView.setText(source.getLabel());
        holder.urlTextView.setText(source.getUrl());
        holder.statusTextView.setText(getUpdateText(source));
        holder.countTextView.setText(getHostCount(source));
        holder.itemView.setAlpha(source.isEnabled() ? 1f : 0.5f);
        holder.itemView.setOnClickListener(v -> this.onItemClickListener.onItemClick(source));
    }

    private String getUpdateText(HostsSource source) {
        if (!source.isEnabled()) {
            return this.context.getString(R.string.hosts_source_disabled);
        }
        boolean lastOnlineModificationDefined = source.getOnlineModificationDate() != null;
        boolean lastLocalModificationDefined = source.getLocalModificationDate() != null;
        if (lastOnlineModificationDefined) {
            String approximateDelay = getApproximateDelay(this.context, source.getOnlineModificationDate());
            if (!lastLocalModificationDefined) {
                return this.context.getString(R.string.hosts_source_last_update, approximateDelay);
            } else if (source.getOnlineModificationDate().isAfter(source.getLocalModificationDate())) {
                return this.context.getString(R.string.hosts_source_need_update, approximateDelay);
            } else {
                return this.context.getString(R.string.hosts_source_up_to_date, approximateDelay);
            }
        } else if (lastLocalModificationDefined) {
            String approximateDelay = getApproximateDelay(this.context, source.getLocalModificationDate());
            return this.context.getString(R.string.hosts_source_installed, approximateDelay);
        } else {
            return this.context.getString(R.string.hosts_source_unknown_status);
        }
    }

    private static String getApproximateDelay(Context context, ZonedDateTime from) {
        Resources resources = context.getResources();
        long delay = Duration.between(from, ZonedDateTime.now()).toMinutes();
        if (delay < 60) {
            return resources.getString(R.string.hosts_source_few_minutes);
        }
        delay /= 60;
        if (delay < 24) {
            int hours = (int) delay;
            return resources.getQuantityString(R.plurals.hosts_source_hours, hours, hours);
        }
        delay /= 24;
        if (delay < 30) {
            int days = (int) delay;
            return resources.getQuantityString(R.plurals.hosts_source_days, days, days);
        }
        int months = (int) delay / 30;
        return resources.getQuantityString(R.plurals.hosts_source_months, months, months);
    }

    private String getHostCount(HostsSource source) {
        int size = source.getSize();
        if (size <= 0 || !source.isEnabled()) {
            return "";
        }
        int length = 1;
        while (size > 10) {
            size /= 10;
            length++;
        }
        int prefixIndex = (length - 1) / 3 - 1;
        size = source.getSize();
        if (prefixIndex < 0) {
            return this.context.getString(R.string.hosts_count, Integer.toString(size));
        } else if (prefixIndex >= QUANTITY_PREFIXES.length) {
            prefixIndex = QUANTITY_PREFIXES.length - 1;
            size = 13;
        }
        size = Math.toIntExact(Math.round(size / Math.pow(10, (prefixIndex + 1) * 3D)));
        return this.context.getString(R.string.hosts_count, size + QUANTITY_PREFIXES[prefixIndex]);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView labelTextView;
        final TextView urlTextView;
        final TextView statusTextView;
        final TextView countTextView;

        ViewHolder(View itemView) {
            super(itemView);
            this.labelTextView = itemView.findViewById(R.id.tv_source_item_label);
            this.urlTextView = itemView.findViewById(R.id.tv_source_item_url);
            this.statusTextView = itemView.findViewById(R.id.tv_source_item_status);
            this.countTextView = itemView.findViewById(R.id.tv_source_item_count);
        }
    }
}
