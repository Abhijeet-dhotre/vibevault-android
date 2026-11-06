package com.muse.vibevault;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Simple list adapter for the download history (newest first). */
public class HistoryAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final java.text.DateFormat dateFormat;
    private List<DownloadRecord> records = new ArrayList<>();

    public HistoryAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
        this.dateFormat = DateFormat.getDateFormat(context);
    }

    public void submit(List<DownloadRecord> newRecords) {
        records = newRecords == null ? new ArrayList<>() : newRecords;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return records.size();
    }

    @Override
    public DownloadRecord getItem(int position) {
        return records.get(position);
    }

    @Override
    public long getItemId(int position) {
        return records.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_history, parent, false);
            holder = new ViewHolder();
            holder.title = convertView.findViewById(R.id.tv_item_title);
            holder.meta = convertView.findViewById(R.id.tv_item_meta);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        DownloadRecord record = getItem(position);
        holder.title.setText(record.title == null || record.title.isEmpty()
                ? record.fileName : record.title);
        String date = record.createdAtMs > 0
                ? dateFormat.format(new java.util.Date(record.createdAtMs)) : "";
        holder.meta.setText(String.format("%s  •  %s  •  %s",
                record.presetLabel,
                FormatUtils.humanBytes(record.sizeBytes),
                date));
        return convertView;
    }

    private static class ViewHolder {
        TextView title;
        TextView meta;
    }
}
