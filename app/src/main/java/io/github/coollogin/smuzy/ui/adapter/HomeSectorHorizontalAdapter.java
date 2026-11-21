package io.github.coollogin.smuzy.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import io.github.coollogin.smuzy.databinding.ItemHorizontalHomeSectorBinding;
import io.github.coollogin.smuzy.databinding.ItemHorizontalPlaylistDialogTrackBinding;
import io.github.coollogin.smuzy.glide.CustomGlideRequest;
import io.github.coollogin.smuzy.model.HomeSector;
import io.github.coollogin.smuzy.subsonic.models.Child;
import io.github.coollogin.smuzy.util.Constants;
import io.github.coollogin.smuzy.util.MusicUtil;

import java.util.Collections;
import java.util.List;

public class HomeSectorHorizontalAdapter extends RecyclerView.Adapter<HomeSectorHorizontalAdapter.ViewHolder> {
    private List<HomeSector> sectors;

    public HomeSectorHorizontalAdapter() {
        this.sectors = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHorizontalHomeSectorBinding view = ItemHorizontalHomeSectorBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        HomeSector sector = sectors.get(position);

        holder.item.homeSectorTitleCheckBox.setText(sector.getSectorTitle());
        holder.item.homeSectorTitleCheckBox.setChecked(sector.isVisible());
    }

    @Override
    public int getItemCount() {
        return sectors.size();
    }

    public List<HomeSector> getItems() {
        return this.sectors;
    }

    public void setItems(List<HomeSector> sectors) {
        this.sectors = sectors;
        notifyDataSetChanged();
    }

    public HomeSector getItem(int id) {
        return sectors.get(id);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHorizontalHomeSectorBinding item;

        ViewHolder(ItemHorizontalHomeSectorBinding item) {
            super(item.getRoot());

            this.item = item;

            this.item.homeSectorTitleCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> onCheck(isChecked));
        }

        private void onCheck(boolean isChecked) {
            sectors.get(getBindingAdapterPosition()).setVisible(isChecked);
        }
    }
}
