package ml.bmlzootown.hydravion;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.volley.NetworkResponse;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ml.bmlzootown.hydravion.models.Edge;
import ml.bmlzootown.hydravion.models.Edges;
import ml.bmlzootown.hydravion.models.Level;
import ml.bmlzootown.hydravion.models.Live;
import ml.bmlzootown.hydravion.models.Subscription;
import ml.bmlzootown.hydravion.models.Video;
import ml.bmlzootown.hydravion.models.VideoInfo;

import static android.app.Activity.RESULT_OK;

public class MainFragment extends BrowseFragment {
    private static final String TAG = "MainFragment";

    private static final int BACKGROUND_UPDATE_DELAY = 300;
    private static final int GRID_ITEM_WIDTH = 200;
    private static final int GRID_ITEM_HEIGHT = 200;
    private static int NUM_ROWS = 6;
    private static int NUM_COLS = 15;

    private final Handler mHandler = new Handler();
    private Drawable mDefaultBackground;
    private DisplayMetrics mMetrics;
    private Timer mBackgroundTimer;
    private String mBackgroundUri;
    private BackgroundManager mBackgroundManager;

    public static String sailssid;
    public static String cfduid;
    public static String cdn;

    public static List<Subscription> subscriptions = new ArrayList<>();
    public static HashMap<String, Video[]> videos = new HashMap<>();
    private int subCount;
    private int page = 1;

    private int rowSelected;
    private int colSelected;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onActivityCreated(savedInstanceState);

        checkLogin();
        //test();

        //prepareBackgroundManager();

        //setupUIElements();

        //setupEventListeners();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != mBackgroundTimer) {
            Log.d(TAG, "onDestroy: " + mBackgroundTimer.toString());
            mBackgroundTimer.cancel();
        }
    }

    private void checkLogin() {
        boolean gotCookies = loadCredentials();
        if (!gotCookies) {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivityForResult(intent, 42);
            cdn = "edge03-na.floatplane.com";
        } else {
            initialize();
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 42 && resultCode == 1 && data != null) {
            ArrayList<String> cookies = data.getStringArrayListExtra("cookies");
            for (String cookie : cookies) {
                String[] c = cookie.split("=");
                if (c[0].equalsIgnoreCase("sails.sid")) {
                    sailssid = c[1];
                }
                if (c[0].equalsIgnoreCase("__cfduid")) {
                    cfduid = c[1];
                }
            }
            Log.d("MainFragment", cfduid + "; " + sailssid);

            saveCredentials();
            initialize();
        }
    }

    private void initialize() {
        getSubscriptions();
        prepareBackgroundManager();
        setupUIElements();
        setupEventListeners();
    }

    private boolean loadCredentials() {
        SharedPreferences prefs = getActivity().getPreferences(Context.MODE_PRIVATE);
        sailssid = prefs.getString("sails.sid", "default");
        cfduid = prefs.getString("__cfduid", "default");
        cdn = prefs.getString("cdn", "default");
        Log.d("LOGIN", sailssid);
        Log.d("LOGIN", cfduid);
        Log.d("CDN", cdn);
        if (sailssid.equals("default") || cfduid.equals("default") || cdn.equals("default")) {
            Log.d("LOGIN", "Credentials not found!");
            return false;
        } else {
            Log.d("LOGIN", "Credentials found!");
            return true;
        }
    }

    private void logout() {
        sailssid = "default";
        cfduid = "default";
        saveCredentials();
        getActivity().finishAndRemoveTask();
    }

    private void saveCredentials() {
        SharedPreferences prefs = getActivity().getPreferences(Context.MODE_PRIVATE);
        prefs.edit().putString("sails.sid", sailssid).apply();
        prefs.edit().putString("__cfduid", cfduid).apply();
        prefs.edit().putString("cdn", cdn).apply();
    }

    private void getLiveInfo(Subscription sub) {
        String uri = "https://www.floatplane.com/api/cdn/delivery?type=live&creator=" + sub.getCreator();
        String cookies = "__cfduid=" + cfduid + "; sails.sid=" + sailssid;
        RequestTask rt = new RequestTask(getActivity().getApplicationContext());
        rt.sendRequest(uri, cookies, new RequestTask.VolleyCallback() {
            @Override
            public void onSuccess(String string) {
                gotLiveInfo(string, sub);
            }
            @Override
            public void onSuccessCreator(String string, String creatorGUID) {
            }
            @Override
            public void onError(VolleyError error) {
            }
        });
    }

    private void gotLiveInfo(String response, Subscription sub) {
        Gson gson = new Gson();
        Live live = gson.fromJson(response, Live.class);
        String l = live.getCdn() + live.getResource().getUri();
        String pattern = "\\{(.*?)\\}";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(live.getResource().getUri());
        if (m.find()) {
            for (int i = 0; i < m.groupCount(); i++) {
                //Log.d("LIVE", m.group(i));
                String var = m.group(i).substring(1, m.group(i).length()-1);
                if (var.equalsIgnoreCase("token")) {
                    l = l.replaceAll("\\{token\\}", live.getResource().getData().getToken());
                    sub.setStreamUrl(l);
                    Log.d("LIVE", l);
                }
                //Log.d("LIVE", l);
            }
        }
    }

    private void getSubscriptions() {
        String uri = "https://www.floatplane.com/api/user/subscriptions";
        String cookies = "__cfduid=" + cfduid + "; sails.sid=" + sailssid;
        RequestTask rt = new RequestTask(getActivity().getApplicationContext());
        rt.sendRequest(uri, cookies, new RequestTask.VolleyCallback() {
            @Override
            public void onSuccess(String string) {
                gotSubscriptions(string);
            }
            @Override
            public void onSuccessCreator(String string, String creatorGUID) {
            }
            @Override
            public void onError(VolleyError error) {
                NetworkResponse nr = error.networkResponse;
                if (nr != null && nr.statusCode == 403) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle("Session Expired");
                    builder.setMessage("Re-open Hydravion to login again!");
                    builder.setPositiveButton("OK",
                            (dialog, which) -> {
                                dialog.dismiss();
                                logout();
                            });
                    builder.create().show();
                }
            }
        });

        /*CookieManager cm = new CookieManager();
        try {
            URI domain = new URI("floatplane.com");
            HttpCookie c1 = new HttpCookie("__cfduid", MainFragment.cfduid);
            HttpCookie c2 = new HttpCookie("sails.sid", MainFragment.sailssid);
            cm.getCookieStore().add(domain, c1);
            cm.getCookieStore().add(domain, c2);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }*/
    }

    private void gotSubscriptions(String response) {
        JSONObject obj = new JSONObject();
        try {
            obj = new JSONObject(response);
        } catch(IllegalStateException | JSONException e) {
            Log.d("TESTING", "Exception caught!");
        }

        if (obj.has("errors")) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("Session Expired");
            builder.setMessage("Re-open Hydravion to login again!");
            builder.setPositiveButton("OK",
                    (dialog, which) -> {
                        dialog.dismiss();
                        logout();
                    });
            builder.create().show();
            return;
        }

        Gson gson = new Gson();
        Subscription[] subs = gson.fromJson(response, Subscription[].class);

        NUM_ROWS = subs.length;
        List<Subscription> trimmed = new ArrayList<>();
        for (Subscription sub : subs) {
            if (trimmed.size() > 0) {
                if (!containsSub(trimmed, sub)) {
                    trimmed.add(sub);
                }
            } else {
                trimmed.add(sub);
            }
        }
        subscriptions = trimmed;
        for (Subscription sub : subscriptions) {
            getLiveInfo(sub);
            getVideos(sub.getCreator(), 1);
        }
        subCount = trimmed.size();
        Log.d("ROWS", trimmed.size() + "");
    }

    private boolean containsSub(List<Subscription> trimmed, Subscription sub) {
        for (Subscription s : trimmed) {
            if (s.getCreator().equals(sub.getCreator())) {
                return true;
            }
        }
        return false;
    }

    private void getVideos(String creatorGUID, int page) {
        String uri = "https://www.floatplane.com/api/creator/videos?creatorGUID=" + creatorGUID + "&fetchAfter=" + ((page-1)*20);
        String cookies = "__cfduid=" + cfduid + "; sails.sid=" + sailssid;
        RequestTask rt = new RequestTask(getActivity().getApplicationContext());
        rt.sendRequest(uri, cookies, creatorGUID, new RequestTask.VolleyCallback() {
            @Override
            public void onSuccess(String string) {
            }
            @Override
            public void onSuccessCreator(String string, String creatorGUID) {
                gotVideos(string, creatorGUID);
            }
            @Override
            public void onError(VolleyError error) {
            }
        });
    }

    private void gotVideos(String response, String creatorGUID) {
        Gson gson = new Gson();
        Video[] vids = gson.fromJson(response, Video[].class);
        if (videos.get(creatorGUID) != null && videos.get(creatorGUID).length > 0) {
            Video[] temp = videos.get(creatorGUID);
            vids = appendVideos(temp, vids);
            videos.put(creatorGUID, vids);
        } else {
            videos.put(creatorGUID, vids);
        }
        /*if (videos.size() == subscriptions.size()) {
            refreshRows();
        }*/
        if (subCount > 1) {
            subCount--;
        } else {
            refreshRows();
            subCount = subscriptions.size();
            setSelectedPosition(rowSelected, false, new ListRowPresenter.SelectItemViewHolderTask(colSelected));
        }
        NUM_COLS = videos.get(creatorGUID).length;
    }

    private Video[] appendVideos(Video[] oldVids, Video[] newVids) {
        int ol = oldVids.length;
        int nl = newVids.length;
        Video[] updated = new Video[ol+nl];
        System.arraycopy(oldVids, 0, updated, 0, ol);
        System.arraycopy(newVids, 0, updated, ol, nl);
        return updated;
    }

    private void refreshRows() {
        List<Subscription> subs = subscriptions;

        ArrayObjectAdapter rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        CardPresenter cardPresenter = new CardPresenter();

        int i;
        for (i = 0; i < subs.size(); i++) {
            ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(cardPresenter);

            Subscription sub = subscriptions.get(i);
            Video[] vs = videos.get(sub.getCreator());
            for (Video v : vs) {
                listRowAdapter.add(v);
            }
            HeaderItem header = new HeaderItem(i, sub.getPlan().getTitle());
            rowsAdapter.add(new ListRow(header, listRowAdapter));
        }

        HeaderItem gridHeader = new HeaderItem(i, "SETTINGS");

        GridItemPresenter mGridPresenter = new GridItemPresenter();
        ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(mGridPresenter);
        gridRowAdapter.add(getResources().getString(R.string.refresh));
        gridRowAdapter.add(getResources().getString(R.string.live_stream));
        gridRowAdapter.add(getResources().getString(R.string.select_server));
        gridRowAdapter.add(getResources().getString(R.string.logout));
        rowsAdapter.add(new ListRow(gridHeader, gridRowAdapter));

        setAdapter(rowsAdapter);
    }

    private void prepareBackgroundManager() {

        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.attach(getActivity().getWindow());

        mDefaultBackground = ContextCompat.getDrawable(getContext(), R.drawable.default_background);
        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void setupUIElements() {
        // setBadgeDrawable(getActivity().getResources().getDrawable(R.drawable.videos_by_google_banner));
        setBadgeDrawable(getActivity().getResources().getDrawable(R.drawable.white_plane));
        //setTitle(getString(R.string.browse_title)); // Badge, when set, takes precedent
        // over title
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        // set fastLane (or headers) background color
        setBrandColor(ContextCompat.getColor(getContext(), R.color.fastlane_background));
        // set search icon color
        //setSearchAffordanceColor(ContextCompat.getColor(getContext(), R.color.search_opaque));
    }

    private void setupEventListeners() {
        /*setOnSearchClickedListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Toast.makeText(getActivity(), "Implement your own in-app search", Toast.LENGTH_LONG)
                        .show();
            }
        });*/

        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
    }

    private void updateBackground(String uri) {
        int width = mMetrics.widthPixels;
        int height = mMetrics.heightPixels;
        Glide.with(getActivity())
                .load(uri)
                .centerCrop()
                .error(mDefaultBackground)
                .transition(
                        new DrawableTransitionOptions().crossFade())
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        mBackgroundManager.setDrawable(resource);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {

                    }
                });
        mBackgroundTimer.cancel();
    }

    private void startBackgroundTimer() {
        if (null != mBackgroundTimer) {
            mBackgroundTimer.cancel();
        }
        mBackgroundTimer = new Timer();
        mBackgroundTimer.schedule(new UpdateBackgroundTask(), BACKGROUND_UPDATE_DELAY);
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Video) {
                getSelectVid(itemViewHolder, item);
            } else if (item instanceof String) {
                if (item.toString().equalsIgnoreCase(getString(R.string.refresh))) {
                    refreshRows();
                } else if (item.toString().equalsIgnoreCase(getString(R.string.logout))) {
                    //sailssid = "default";
                    //cfduid = "default";
                    //saveCredentials();
                    //getActivity().finishAndRemoveTask();
                    logout();
                } else if (item.toString().equalsIgnoreCase(getString(R.string.select_server))) {
                    String uri = "https://www.floatplane.com/api/edges";
                    String cookies = "__cfduid=" + MainFragment.cfduid + "; sails.sid=" + MainFragment.sailssid;
                    RequestTask rt = new RequestTask(getActivity().getApplicationContext());
                    rt.sendRequest(uri, cookies, new RequestTask.VolleyCallback() {
                        @Override
                        public void onSuccess(String string) {
                            Gson gson = new Gson();
                            Edges es = gson.fromJson(string, Edges.class);
                            List<String> servers = new ArrayList<>();
                            if (es != null) {
                                List<Edge> edges = es.getEdges();
                                for (Edge e : edges) {
                                    if (e.getAllowStreaming()) {
                                        servers.add(e.getHostname());
                                    }
                                }
                                CharSequence[] hostnames = servers.toArray(new CharSequence[servers.size()]);
                                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                builder.setTitle("Select CDN Server");
                                builder.setItems(hostnames,
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                String server = servers.get(which);
                                                Log.d("CDN", server);
                                                SharedPreferences prefs = getActivity().getPreferences(Context.MODE_PRIVATE);
                                                prefs.edit().putString("cdn", server).apply();
                                            }
                                        });
                                builder.create().show();
                            }
                        }
                        @Override
                        public void onSuccessCreator(String string, String creatorGUID) {
                        }
                        @Override
                        public void onError(VolleyError error) {
                        }
                    });
                } else if (item.toString().equalsIgnoreCase(getString(R.string.live_stream))) {
                    List<String> subs = new ArrayList<>();
                    for (Subscription s : subscriptions) {
                        subs.add(s.getPlan().getTitle());
                    }
                    CharSequence[] s = subs.toArray(new CharSequence[subs.size()]);
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle("Play livestream?");
                    builder.setItems(s,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String stream = subscriptions.get(which).getStreamUrl();
                                    Log.d("LIVE", stream);
                                    Video live = new Video();
                                    live.setVidUrl(stream);
                                    Intent intent = new Intent(getActivity(), PlaybackActivity.class);
                                    intent.putExtra(DetailsActivity.Video, (Serializable) live);
                                    startActivity(intent);
                                }
                            });
                    builder.create().show();
                }
            }
        }
    }

    private void getSelectVid(final Presenter.ViewHolder itemViewHolder, final Object item) {
        String cookies = "__cfduid=" + cfduid + "; sails.sid=" + sailssid;
        final Video video = (Video) item;
        String uri = "https://www.floatplane.com/api/video/url?guid=" + video.getGuid() + "&quality=1080";
        RequestTask rt = new RequestTask(getActivity().getApplicationContext());
        rt.sendRequest(uri, cookies, new RequestTask.VolleyCallback() {
            @Override
            public void onSuccess(String string) {
                Video vid = video;
                //vid.setGuid(string.replaceAll("\"", ""));
                vid.setVidUrl(string.replaceAll("\"", ""));

                Log.d(TAG, "Item: " + vid.toString());
                Intent intent = new Intent(getActivity(), DetailsActivity.class);
                intent.putExtra(DetailsActivity.Video, vid);

                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        DetailsActivity.SHARED_ELEMENT_NAME)
                        .toBundle();
                getActivity().startActivity(intent, bundle);
            }
            @Override
            public void onSuccessCreator(String string, String creatorGUID) {
            }
            @Override
            public void onError(VolleyError error) {
            }
        });
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(
                Presenter.ViewHolder itemViewHolder,
                Object item,
                RowPresenter.ViewHolder rowViewHolder,
                Row row) {
            if (item instanceof Video) {
                mBackgroundUri = ((Video) item).getThumbnail().getPath();
                startBackgroundTimer();
                final ListRow listRow = (ListRow) row;
                final ArrayObjectAdapter current = (ArrayObjectAdapter) listRow.getAdapter();
                int selected = current.indexOf(item);
                colSelected = selected;
                for (Subscription s : subscriptions) {
                    if (((Video) item).getCreator().equals(s.getCreator())) {
                        rowSelected = subscriptions.indexOf(s);
                    }
                }
                if (selected != -1 && (current.size() - 1) == selected) {
                    for (Subscription sub : subscriptions) {
                        getVideos(sub.getCreator(), page+1);
                    }
                    page++;
                }
            }
        }
    }

    private class UpdateBackgroundTask extends TimerTask {

        @Override
        public void run() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateBackground(mBackgroundUri);
                }
            });
        }
    }

    private class GridItemPresenter extends Presenter {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            TextView view = new TextView(parent.getContext());
            view.setLayoutParams(new ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT));
            view.setFocusable(true);
            view.setFocusableInTouchMode(true);
            view.setBackgroundColor(
                    ContextCompat.getColor(getContext(), R.color.default_background));
            view.setTextColor(Color.WHITE);
            view.setGravity(Gravity.CENTER);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            ((TextView) viewHolder.view).setText((String) item);
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
        }
    }

}
