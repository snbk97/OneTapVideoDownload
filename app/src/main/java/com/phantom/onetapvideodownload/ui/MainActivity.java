package com.phantom.onetapvideodownload.ui;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.folderselector.FolderChooserDialog;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.NativeExpressAdView;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.plus.PlusOneButton;
import com.phantom.onetapvideodownload.AnalyticsApplication;
import com.phantom.onetapvideodownload.R;
import com.phantom.onetapvideodownload.Video.Video;
import com.phantom.onetapvideodownload.Video.YoutubeVideo;
import com.phantom.onetapvideodownload.databasehandlers.VideoDatabase;
import com.phantom.onetapvideodownload.downloader.ProxyDownloadManager;
import com.phantom.onetapvideodownload.ui.downloadoptions.DownloadOptionAdapter;
import com.phantom.onetapvideodownload.ui.downloadoptions.DownloadOptionIds;
import com.phantom.onetapvideodownload.utils.CheckPreferences;
import com.phantom.onetapvideodownload.utils.Global;
import com.phantom.onetapvideodownload.utils.Invokable;
import com.phantom.onetapvideodownload.utils.XposedChecker;
import com.phantom.onetapvideodownload.utils.YoutubeParserProxy;
import com.phantom.onetapvideodownload.utils.enums.AppPermissions;
import com.phantom.onetapvideodownload.utils.enums.MaterialDialogIds;

import net.xpece.android.support.preference.Fixes;

import java.io.File;

public class MainActivity extends AppCompatActivity implements FolderChooserDialog.FolderCallback,
        Invokable<Video, Integer> {
    private final static String TAG = "MainActivity";
    private Toolbar toolbar;
    private Tracker mTracker;
    private MaterialDialogIds folderChooserDialogId;
    private static RecyclerView mDownloadDialogRecyclerView;
    private static final String APP_URL = "https://play.google.com/store/apps/details?id=com.phantom.onetapvideodownload";
    private PlusOneButton mPlusOneButton;
    private ApplicationUpdateNotification mApplicationUpdateNotification;
    private MaterialDialog mProgressDialog;
    private NativeExpressAdView mAdView;

    public final static String ACTION_SHOW_DOWNLOAD_DIALOG = "com.phantom.onetapvideodownload.action.saveurl";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Fixes.updateLayoutInflaterFactory(getLayoutInflater());
        setContentView(R.layout.activity_main);
        MobileAds.initialize(getApplicationContext(), "ca-app-pub-4007739214025921~1601743291");
        mAdView = (NativeExpressAdView)findViewById(R.id.adView);
        if (CheckPreferences.getAdEnabled(this)) {
            showAd();
        } else {
            hideAd();
        }

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(ViewPagerFragmentParent.FRAGMENT_TAG) == null) {
            FragmentTransaction ft = fm.beginTransaction();
            ft.add(R.id.content_frame, new ViewPagerFragmentParent(),
                    ViewPagerFragmentParent.FRAGMENT_TAG);
            ft.commit();
            fm.executePendingTransactions();
        }

        AnalyticsApplication application = (AnalyticsApplication) getApplication();
        mTracker = application.getDefaultTracker();

        mTracker.setScreenName("Activity~" + getClass().getName());
        mTracker.send(new HitBuilders.ScreenViewBuilder().build());
        checkXposedInstallation();
        mApplicationUpdateNotification = new ApplicationUpdateNotification(this);
        mApplicationUpdateNotification.checkForUpdate();
        onNewIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAndRequestPermission(AppPermissions.External_Storage_Permission);
        if (mPlusOneButton != null) {
            mPlusOneButton.initialize(APP_URL, 0);
        }
    }

    private void handleActionShareIntent(Intent intent) {
        String type = intent.getType();
        if (type != null && type.startsWith("text")) {
            String videoUrl = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (videoUrl == null) {
                return;
            }


            Uri uri = Uri.parse(videoUrl);
            String videoParam = null;
            if (videoUrl.contains("youtube")) {
                videoParam = uri.getQueryParameter("v");
            } else if (videoUrl.contains("youtu.be")) {
                videoParam = uri.getLastPathSegment();
            }

            if (videoParam != null) {
                mProgressDialog = new MaterialDialog.Builder(this)
                        .title(R.string.progress_dialog)
                        .content(R.string.please_wait)
                        .progress(true, 0)
                        .show();
                YoutubeParserProxy.startParsing(this, videoParam, this);
            } else {
                Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void displaySnackbar(String text, String actionName, View.OnClickListener action) {
        Snackbar snack = Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG)
                .setAction(actionName, action);

        View v = snack.getView();
        v.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.accent));
        ((TextView) v.findViewById(android.support.design.R.id.snackbar_text)).setTextColor(Color.WHITE);
        ((TextView) v.findViewById(android.support.design.R.id.snackbar_action)).setTextColor(Color.BLACK);

        snack.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem adSwitch = menu.findItem(R.id.adswitch);
        adSwitch.setChecked(CheckPreferences.getAdEnabled(this));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_plus_one:
                showPlusOneDialog();
                break;
            case R.id.menu_rate_my_app :
                openAppInPlayStore();
                break;
            case R.id.menu_donate :
                openDonateActivity();
                break;
            case R.id.menu_translate :
                sendEmailForTranslation();
                break;
            case R.id.menu_require_help :
                sendEmailForHelp();
                break;
            case R.id.menu_about:
                openAboutActivity();
                break;
            case R.id.menu_usage_instruction_title:
                openUsageInstructionActivity();
                break;
            case R.id.adswitch:
                item.setChecked(!item.isChecked());
                CheckPreferences.toggleAdEnabled(this);
                if (item.isChecked()) {
                    Toast.makeText(this, R.string.ad_enabled, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.ad_disabled, Toast.LENGTH_LONG).show();
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void checkXposedInstallation() {
        if (!XposedChecker.isXposedInstalled(this)) {
            XposedChecker.showXposedNotFound(this);
        } else if (!isModuleEnabled()) {
            XposedChecker.showModuleNotEnalbed(this);
        }
    }


    //If self hooking is successful, it will return true.
    public static boolean isModuleEnabled() {
        return false;
    }

    @TargetApi(23)
    public void checkAndRequestPermission(AppPermissions permission) {
        int currentApiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentApiVersion > Build.VERSION_CODES.LOLLIPOP_MR1){
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                showDialogToAccessPermission(permission);
            } else {
                requestPermission(permission);
            }
        }
    }

    private void requestPermission(AppPermissions permission) {
        if (ContextCompat.checkSelfPermission(this, permission.getPermissionName())
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { permission.getPermissionName() },
                    permission.getPermissionCode());
        }
    }

    public void showDialogToAccessPermission(final AppPermissions permission) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getString(R.string.accept_write_permission_title));
        builder.setMessage(getResources().getString(R.string.accept_write_permission_description));

        builder.setPositiveButton("Continue", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            }
        });

        builder.setNegativeButton("Exit", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                finish();
            }
        });

        AlertDialog alert = builder.create();
        alert.setCanceledOnTouchOutside(false);
        alert.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        AppPermissions permission = AppPermissions.getPermission(requestCode);
        switch (permission) {
            case External_Storage_Permission : {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.permission_accepted, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    public void onFolderSelection(@NonNull File directory) {
        switch(folderChooserDialogId) {
            case DefaultDownloadLocation:
                if(directory.canWrite()) {
                    CheckPreferences.setDownloadLocation(this, directory.getPath());
                    SettingsFragment.updatePreferenceSummary();
                } else {
                    Toast.makeText(this, "No write permission on selected directory", Toast.LENGTH_SHORT).show();
                }
                break;
            case VideoDownloadLocation:
                if(directory.canWrite()) {
                    DownloadOptionAdapter downloadOptionAdapter =
                            (DownloadOptionAdapter) mDownloadDialogRecyclerView.getAdapter();
                    downloadOptionAdapter.setDownloadLocation(directory.getPath());
                    SettingsFragment.updatePreferenceSummary();
                } else {
                    Toast.makeText(this, "No write permission on selected directory", Toast.LENGTH_SHORT).show();
                }
        }
    }

    public void setFolderChooserDialogId(MaterialDialogIds id) {
        folderChooserDialogId = id;
    }

    public static DownloadOptionAdapter getDownloadOptionAdapter() {
        return (DownloadOptionAdapter)mDownloadDialogRecyclerView.getAdapter();
    }

    private void showVideoDownloadDialog(final long videoId) {
        VideoDatabase videoDatabase = VideoDatabase.getDatabase(this);
        final Video video = videoDatabase.getVideo(videoId);
        video.setContext(this);

        final MaterialDialog dialog = new MaterialDialog.Builder(this)
                .customView(R.layout.dialog_download_file, false)
                .canceledOnTouchOutside(false)
                .backgroundColorRes(R.color.dialog_background)
                .show();

        View dialogView = dialog.getCustomView();
        mDownloadDialogRecyclerView = (RecyclerView)dialogView.findViewById(R.id.download_option_list);

        // For wrapping content on RecyclerView
        LinearLayoutManager layoutManager = new org.solovyev.android.views.llm.LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mDownloadDialogRecyclerView.setLayoutManager(layoutManager);
        mDownloadDialogRecyclerView.setHasFixedSize(true);
        mDownloadDialogRecyclerView.setAdapter(new DownloadOptionAdapter(this, video.getOptions()));

        ImageView closeButton = (ImageView)dialogView.findViewById(R.id.close);
        assert(closeButton != null);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        Button startDownloadButton = (Button)dialogView.findViewById(R.id.start_download);
        Button downloadLaterButton = (Button) dialogView.findViewById(R.id.download_later);
        Button copyVideoLink = (Button) dialogView.findViewById(R.id.copy_download_link);

        startDownloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                DownloadOptionAdapter downloadOptionAdapter = getDownloadOptionAdapter();
                String filename = downloadOptionAdapter.getOptionItem(DownloadOptionIds.Filename).getOptionValue();
                String downloadLocation = downloadOptionAdapter.getOptionItem(DownloadOptionIds.DownloadLocation).getOptionValue();

                if (video instanceof YoutubeVideo) {
                    Integer itag = YoutubeVideo.getItagForDescription(downloadOptionAdapter.getOptionItem(DownloadOptionIds.Format).getOptionValue());
                    if (itag == -1) {
                        Log.e(TAG, "getItagForDescription returned NULL");
                    }

                    ProxyDownloadManager.startActionYoutubeDownload(getApplicationContext(), videoId, filename, downloadLocation, itag);
                } else {
                    ProxyDownloadManager.startActionBrowserDownload(getApplicationContext(), videoId, filename, downloadLocation);
                }
            }
        });

        downloadLaterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                DownloadOptionAdapter downloadOptionAdapter = getDownloadOptionAdapter();
                String filename = downloadOptionAdapter.getOptionItem(DownloadOptionIds.Filename).getOptionValue();
                String downloadLocation = downloadOptionAdapter.getOptionItem(DownloadOptionIds.DownloadLocation).getOptionValue();

                if (video instanceof YoutubeVideo) {
                    Integer itag = YoutubeVideo.getItagForDescription(downloadOptionAdapter.getOptionItem(DownloadOptionIds.Format).getOptionValue());
                    if (itag == -1) {
                        Log.e(TAG, "getItagForDescription returned NULL");
                    }

                    ProxyDownloadManager.startActionYoutubeInserted(getApplicationContext(), videoId, filename, downloadLocation, itag);
                } else {
                    ProxyDownloadManager.startActionBrowserInserted(getApplicationContext(), videoId, filename, downloadLocation);
                }
            }
        });

        copyVideoLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                String url = video.getUrl();
                Global.copyUrlToClipboard(getApplicationContext(), url);
                Toast.makeText(getApplicationContext(), R.string.video_url_copied, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleActionDownloadDialog(Intent intent) {
        long videoId = intent.getLongExtra("videoId", -1);
        if (videoId != -1) {
            showVideoDownloadDialog(videoId);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        String actionName = intent.getAction();
        if (Intent.ACTION_SEND.equals(actionName)) {
            handleActionShareIntent(intent);
        } else if (ACTION_SHOW_DOWNLOAD_DIALOG.equals(actionName)) {
            handleActionDownloadDialog(intent);
        }
    }

    public void showPlusOneDialog() {
        final MaterialDialog dialog = new MaterialDialog.Builder(this)
                .customView(R.layout.dialog_plus_one, false)
                .canceledOnTouchOutside(true)
                .show();
        View dialogView = dialog.getCustomView();
        if (dialogView != null) {
            mPlusOneButton = (PlusOneButton) dialogView.findViewById(R.id.plus_one_button);
            mPlusOneButton.initialize(APP_URL, 0);
        }
    }

    public void openAppInPlayStore() {
        Uri uri = Uri.parse("market://details?id=" + getPackageName());
        Intent playStoreIntent = new Intent(Intent.ACTION_VIEW, uri);
        playStoreIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY |
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        try {
            startActivity(playStoreIntent);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://play.google.com/store/apps/details?id=" + getPackageName())));
        }
    }

    public void sendEmailForTranslation() {
        String to = Global.getDeveloperEmail();
        String subject = "One Tap Video Download - App Translation";
        String body = "I would like to translate One Tap Video Download to {REPLACE THIS WITH LANGUAGE NAME}";
        Global.sendEmail(this, to, subject, body);
    }

    public void sendEmailForHelp() {
        String to = Global.getDeveloperEmail();
        String subject = "One Tap Video Download - Need Help";
        String body = "Hi, I am experience this issue : {REPLACE THIS WITH YOUR ISSUE}";
        Global.sendEmail(this, to, subject, body);
    }

    public void openDonateActivity() {
        startActivity(DonateActivity.class);
    }

    public void openAboutActivity() {
        startActivity(About.class);
    }

    public void startActivity(Class activityClass) {
        Intent intent = new Intent(this, activityClass);
        startActivity(intent);
    }

    @Override
    public Integer invoke(Video video) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }

        if (video != null) {
            YoutubeVideo youtubeVideo = (YoutubeVideo)video;
            youtubeVideo.setPackageName("com.google.android.youtube");

            VideoDatabase videoDatabase = VideoDatabase.getDatabase(this);
            long videoId = videoDatabase.addOrUpdateVideo(video);

            Intent downloadIntent = new Intent(this, MainActivity.class);
            downloadIntent.putExtra("videoId", videoId);
            downloadIntent.setAction(ACTION_SHOW_DOWNLOAD_DIALOG);
            downloadIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            downloadIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            downloadIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(downloadIntent);
        } else {
            Toast.makeText(this, R.string.unable_to_fetch, Toast.LENGTH_LONG).show();
        }
        return 0;
    }

    public void openUsageInstructionActivity() {
        startActivity(UsageInstruction.class);
    }

    private void hideAd() {
        mAdView.setEnabled(false);
        mAdView.setVisibility(View.GONE);
    }

    private void showAd() {
        mAdView.setEnabled(true);
        mAdView.setVisibility(View.VISIBLE);
        AdRequest request = new AdRequest.Builder()
                .build();
        mAdView.loadAd(request);
    }
}
