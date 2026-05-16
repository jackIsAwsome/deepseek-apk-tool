package com.apktool.helper.ui;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apktool.helper.R;
import com.apktool.helper.viewmodel.ApkTaskViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HomeFragment extends Fragment {

    private ApkTaskViewModel viewModel;
    private ActivityResultLauncher<String[]> openApkFile;
    private TextView tvSelectedFile;
    private AppListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(ApkTaskViewModel.class);

        tvSelectedFile = root.findViewById(R.id.tv_selected_file);
        Button btnSelect = root.findViewById(R.id.btn_select_file);
        RecyclerView rvApps = root.findViewById(R.id.rv_installed_apps);

        rvApps.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AppListAdapter();
        rvApps.setAdapter(adapter);

        openApkFile = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        String path = copyUriToWorkDir(uri);
                        viewModel.setSelectedApkPath(path);
                        tvSelectedFile.setText("Selected: " + path);
                    }
                });

        btnSelect.setOnClickListener(v -> openApkFile.launch(new String[]{
                "application/vnd.android.package-archive", "*/*"}));

        loadInstalledApps();

        String current = viewModel.getSelectedApkPath().getValue();
        if (current != null) {
            tvSelectedFile.setText("Selected: " + current);
        }

        return root;
    }

    private void loadInstalledApps() {
        PackageManager pm = requireContext().getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<AppEntry> entries = new ArrayList<>();

        for (ApplicationInfo info : apps) {
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            String sourceDir = info.sourceDir;
            if (sourceDir != null && new File(sourceDir).exists()) {
                entries.add(new AppEntry(
                        info.loadLabel(pm).toString(),
                        info.packageName,
                        sourceDir,
                        info.loadIcon(pm)
                ));
            }
        }

        Collections.sort(entries, (a, b) -> a.appName.compareToIgnoreCase(b.appName));
        adapter.setEntries(entries);
    }

    private String copyUriToWorkDir(Uri uri) {
        File workDir = new File(requireContext().getExternalFilesDir(null), "apktool/input");
        workDir.mkdirs();
        File dest = new File(workDir, "input.apk");
        try {
            java.io.InputStream is = requireContext().getContentResolver().openInputStream(uri);
            java.io.OutputStream os = new java.io.FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
            is.close();
            os.close();
        } catch (Exception e) {
            return uri.getPath();
        }
        return dest.getAbsolutePath();
    }

    private class AppEntry {
        final String appName;
        final String packageName;
        final String apkPath;
        final android.graphics.drawable.Drawable icon;

        AppEntry(String appName, String packageName, String apkPath,
                 android.graphics.drawable.Drawable icon) {
            this.appName = appName;
            this.packageName = packageName;
            this.apkPath = apkPath;
            this.icon = icon;
        }
    }

    private class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
        private List<AppEntry> entries = new ArrayList<>();

        void setEntries(List<AppEntry> entries) {
            this.entries = entries;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_installed_app, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppEntry entry = entries.get(position);
            holder.ivIcon.setImageDrawable(entry.icon);
            holder.tvName.setText(entry.appName);
            holder.tvPackage.setText(entry.packageName);
            holder.itemView.setOnClickListener(v -> {
                viewModel.setSelectedApkPath(entry.apkPath);
                tvSelectedFile.setText("Selected: " + entry.apkPath);
            });
        }

        @Override
        public int getItemCount() { return entries.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName, tvPackage;
            ViewHolder(View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_app_icon);
                tvName = itemView.findViewById(R.id.tv_app_name);
                tvPackage = itemView.findViewById(R.id.tv_package_name);
            }
        }
    }
}
