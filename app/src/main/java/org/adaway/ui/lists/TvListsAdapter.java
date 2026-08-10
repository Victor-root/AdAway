package org.adaway.ui.lists;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.adaway.R;
import org.adaway.db.entity.HostListItem;

/**
 * The {@link RecyclerView.Adapter} for {@link TvListsActivity}'s host list.
 * <p>
 * Deliberately not {@link org.adaway.ui.lists.type.ListsAdapter}: that one is built around a
 * checkbox to toggle an entry and a long-press to edit/delete/copy it, both touch gestures with no
 * good D-pad equivalent. Here every row is a single, plain click, and {@link TvListsActivity}
 * decides what that click means (open the edit/delete/copy/enable dialog, or just copy) the same
 * way {@link org.adaway.ui.log.TvLogAdapter} already does for the DNS log.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class TvListsAdapter extends PagingDataAdapter<HostListItem, TvListsAdapter.ViewHolder> {
    private static final DiffUtil.ItemCallback<HostListItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<HostListItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull HostListItem oldItem, @NonNull HostListItem newItem) {
                    return oldItem.getHost().equals(newItem.getHost());
                }

                @Override
                public boolean areContentsTheSame(@NonNull HostListItem oldItem, @NonNull HostListItem newItem) {
                    return oldItem.equals(newItem);
                }
            };

    interface OnItemClickListener {
        void onItemClick(HostListItem item);
    }

    private final OnItemClickListener onItemClickListener;

    TvListsAdapter(OnItemClickListener onItemClickListener) {
        super(DIFF_CALLBACK);
        this.onItemClickListener = onItemClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tv_item_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HostListItem item = getItem(position);
        if (item == null) {
            // Not loaded yet (Paging3 placeholder): nothing to show or react to until it is.
            holder.hostTextView.setText("");
            holder.redirectionTextView.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(null);
            return;
        }
        holder.hostTextView.setText(item.getHost());
        String redirection = item.getRedirection();
        if (redirection == null || redirection.isEmpty()) {
            holder.redirectionTextView.setVisibility(View.GONE);
        } else {
            holder.redirectionTextView.setVisibility(View.VISIBLE);
            holder.redirectionTextView.setText(redirection);
        }
        // A disabled entry (mobile shows this as an unchecked checkbox) is dimmed rather than
        // hidden or specially iconed: consistent with how the rest of this app marks something
        // present-but-off, and it does not compete with the enabled/disabled state for an icon
        // the way a checkmark-vs-cross pair would.
        holder.itemView.setAlpha(item.isEnabled() ? 1f : 0.5f);
        holder.itemView.setOnClickListener(v -> this.onItemClickListener.onItemClick(item));
    }

    /**
     * Whether an item is the user's own, as opposed to coming from a subscribed block-list
     * source: only the user's own entries can be toggled, edited or deleted, exactly like on
     * mobile ({@link org.adaway.ui.lists.type.ListsAdapter#onBindViewHolder}).
     */
    static boolean isEditable(HostListItem item) {
        return item.getSourceId() == USER_SOURCE_ID;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView hostTextView;
        final TextView redirectionTextView;

        ViewHolder(View itemView) {
            super(itemView);
            this.hostTextView = itemView.findViewById(R.id.tv_list_item_host);
            this.redirectionTextView = itemView.findViewById(R.id.tv_list_item_redirection);
        }
    }
}
