package com.apktool.helper.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.apktool.helper.R;
import com.apktool.helper.service.ApkProcessService;
import com.apktool.helper.viewmodel.ApkTaskViewModel;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;

public class DecompileFragment extends Fragment implements ApkProcessService.ProcessCallback {

    private ApkTaskViewModel viewModel;
    private TextInputEditText etApkPath, etOutDir;
    private Button btnDecompile;
    private ProgressBar progressBar;
    private TextView tvLog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_decompile, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(ApkTaskViewModel.class);

        etApkPath = root.findViewById(R.id.et_apk_path);
        etOutDir = root.findViewById(R.id.et_out_dir);
        btnDecompile = root.findViewById(R.id.btn_decompile);
        progressBar = root.findViewById(R.id.progress_bar);
        tvLog = root.findViewById(R.id.tv_log);

        String apk = viewModel.getSelectedApkPath().getValue();
        if (apk != null) etApkPath.setText(apk);
        String out = viewModel.getOutputPath().getValue();
        File defaultOut = new File(requireContext().getExternalFilesDir(null), "apktool/decompiled");
        etOutDir.setText(out != null ? out : defaultOut.getAbsolutePath());

        viewModel.getLogText().observe(getViewLifecycleOwner(), log -> tvLog.setText(log));
        viewModel.getIsProcessing().observe(getViewLifecycleOwner(), processing -> {
            btnDecompile.setEnabled(!processing);
            progressBar.setVisibility(processing ? View.VISIBLE : View.GONE);
        });

        btnDecompile.setOnClickListener(v -> startDecompile());

        return root;
    }

    private void startDecompile() {
        String apkPath = etApkPath.getText().toString();
        String outDir = etOutDir.getText().toString();

        if (apkPath.isEmpty()) {
            viewModel.appendLog("Error: No APK file selected");
            return;
        }

        viewModel.setOutputPath(outDir);
        viewModel.clearLog();
        viewModel.setIsProcessing(true);

        ApkProcessService.setCallback(this);
        Intent intent = new Intent(requireContext(), ApkProcessService.class);
        intent.putExtra("action", ApkProcessService.ACTION_DECOMPILE);
        intent.putExtra(ApkProcessService.EXTRA_INPUT, apkPath);
        intent.putExtra(ApkProcessService.EXTRA_OUTPUT, outDir);
        requireContext().startService(intent);
    }

    @Override
    public void onLog(String message) {
        viewModel.appendLog(message);
    }

    @Override
    public void onComplete(boolean success, String message) {
        viewModel.setIsProcessing(false);
        viewModel.appendLog(success ? "Success: " + message : "Failed: " + message);
        ApkProcessService.clearCallback();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ApkProcessService.clearCallback();
    }
}
