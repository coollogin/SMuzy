package io.github.coollogin.smuzy.glide;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;
import io.github.coollogin.smuzy.util.ClientCertManager;
import io.github.coollogin.smuzy.util.ServerHeaders;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class IPv6StringLoader implements ModelLoader<String, InputStream> {
    private static final int DEFAULT_TIMEOUT_MS = 2500;
    private static final int MAX_REMEMBERED_PLACEHOLDERS = 500;
    // URLs already known to return a server placeholder, so periodic loaders stop
    // re-requesting artwork the server will never provide.
    private static final Set<String> KNOWN_PLACEHOLDER_URLS = new LinkedHashSet<>();

    private final OkHttpClient client;

    private IPv6StringLoader(OkHttpClient client) {
        this.client = client;
    }

    private static synchronized boolean isKnownPlaceholderUrl(String model) {
        return KNOWN_PLACEHOLDER_URLS.contains(model);
    }

    private static synchronized void rememberPlaceholderUrl(String model) {
        if (KNOWN_PLACEHOLDER_URLS.size() >= MAX_REMEMBERED_PLACEHOLDERS) {
            Iterator<String> oldest = KNOWN_PLACEHOLDER_URLS.iterator();
            oldest.next();
            oldest.remove();
        }
        KNOWN_PLACEHOLDER_URLS.add(model);
    }

    @Override
    public boolean handles(@NonNull String model) {
        return model.startsWith("http://") || model.startsWith("https://");
    }

    @Override
    public LoadData<InputStream> buildLoadData(@NonNull String model, int width, int height, @NonNull Options options) {
        if (!handles(model)) {
            return null;
        }
        return new LoadData<>(new ObjectKey(model), new IPv6StreamFetcher(client, model));
    }

    private static class IPv6StreamFetcher implements DataFetcher<InputStream> {
        private final OkHttpClient client;
        private final String model;
        private Call call;
        private Response response;

        IPv6StreamFetcher(OkHttpClient client, String model) {
            this.client = client;
            this.model = model;
        }

        @Override
        public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
            try {
                if (isKnownPlaceholderUrl(model)) {
                    callback.onLoadFailed(new IOException("Server returned a placeholder image"));
                    return;
                }
                call = client.newCall(new Request.Builder().url(model).build());
                response = call.execute();
                if (!response.isSuccessful()) {
                    callback.onLoadFailed(new IOException("Request failed with status code: " + response.code()));
                    return;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    callback.onLoadFailed(new IOException("Image response has no body"));
                    return;
                }

                if (isServerPlaceholder()) {
                    rememberPlaceholderUrl(model);
                    callback.onLoadFailed(new IOException("Server returned a placeholder image"));
                    return;
                }

                if (NavidromePlaceholderDetector.shouldBuffer(body.contentLength())) {
                    byte[] bytes = body.bytes();
                    if (NavidromePlaceholderDetector.isKnownPlaceholder(bytes)) {
                        rememberPlaceholderUrl(model);
                        callback.onLoadFailed(new IOException("Server returned a placeholder image"));
                        return;
                    }
                    callback.onDataReady(new ByteArrayInputStream(bytes));
                    return;
                }

                callback.onDataReady(body.byteStream());
            } catch (IOException | IllegalArgumentException e) {
                callback.onLoadFailed(e);
            }
        }

        private boolean isServerPlaceholder() {
            return NavidromePlaceholderDetector.isPlaceholderResponse(
                    response.request().url().encodedPath(),
                    response.header("Cache-Control"),
                    response.header("ETag"),
                    response.header("Last-Modified"),
                    response.header("Content-Type"));
        }

        @Override
        public void cleanup() {
            if (response != null) {
                response.close();
            }
        }

        @Override
        public void cancel() {
            if (call != null) {
                call.cancel();
            }
        }

        @NonNull
        @Override
        public Class<InputStream> getDataClass() {
            return InputStream.class;
        }

        @NonNull
        @Override
        public DataSource getDataSource() {
            return DataSource.REMOTE;
        }
    }

    private static OkHttpClient buildClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .addNetworkInterceptor(ServerHeaders.createInterceptor());
        if (ClientCertManager.INSTANCE.getSslSocketFactory() != null) {
            builder.sslSocketFactory(ClientCertManager.INSTANCE.getSslSocketFactory(),
                    ClientCertManager.INSTANCE.getTrustManager());
        }
        return builder.build();
    }

    private static boolean isHttp(Uri uri) {
        String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    /** Opens local (content/file/resource) {@link Uri} models through the ContentResolver. */
    private static class ContentUriFetcher implements DataFetcher<InputStream> {
        private final Context context;
        private final Uri uri;
        private InputStream stream;

        ContentUriFetcher(Context context, Uri uri) {
            this.context = context;
            this.uri = uri;
        }

        @Override
        public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
            try {
                stream = context.getContentResolver().openInputStream(uri);
                if (stream == null) {
                    callback.onLoadFailed(new IOException("No input stream for " + uri));
                    return;
                }
                callback.onDataReady(stream);
            } catch (IOException | SecurityException e) {
                callback.onLoadFailed(e);
            }
        }

        @Override
        public void cleanup() {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
        }

        @Override
        public void cancel() {
            // No-op
        }

        @NonNull
        @Override
        public Class<InputStream> getDataClass() {
            return InputStream.class;
        }

        @NonNull
        @Override
        public DataSource getDataSource() {
            return DataSource.LOCAL;
        }
    }

    public static class Factory implements ModelLoaderFactory<String, InputStream> {
        private final OkHttpClient client;

        public Factory() {
            client = buildClient();
        }

        @NonNull
        @Override
        public ModelLoader<String, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
            return new IPv6StringLoader(client);
        }

        @Override
        public void teardown() {
            // No-op
        }
    }

    /**
     * Handles http(s) {@link Uri} models with the same client as {@link String} models and
     * falls back to the ContentResolver for local Uris. Replaces Glide's default Uri loaders,
     * otherwise Glide retries them after a failed placeholder check and downloads the image.
     */
    public static class UriFactory implements ModelLoaderFactory<Uri, InputStream> {
        private final Context context;
        private final OkHttpClient client;

        public UriFactory(@NonNull Context context) {
            this.context = context.getApplicationContext();
            client = buildClient();
        }

        @NonNull
        @Override
        public ModelLoader<Uri, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
            IPv6StringLoader stringLoader = new IPv6StringLoader(client);
            return new ModelLoader<Uri, InputStream>() {
                @Override
                public LoadData<InputStream> buildLoadData(@NonNull Uri model, int width, int height, @NonNull Options options) {
                    if (isHttp(model)) {
                        return stringLoader.buildLoadData(model.toString(), width, height, options);
                    }
                    return new LoadData<>(new ObjectKey(model), new ContentUriFetcher(context, model));
                }

                @Override
                public boolean handles(@NonNull Uri model) {
                    return true;
                }
            };
        }

        @Override
        public void teardown() {
            // No-op
        }
    }
}
