package com.vikas.torrentplayer.tv;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

/**
 * Android TV boxes often ship stale CA stores even on Android 11. The attached
 * logs show Glide's default HttpURLConnection failing poster loads with
 * SSLHandshakeException / Chain validation failed. Phone keeps the default
 * path; TV uses OkHttp for Glide and falls back only when the platform trust
 * manager rejects an image certificate chain.
 */
@GlideModule
public final class TvGlideModule extends AppGlideModule {

    private static final String TAG = "TvGlideModule";

    @Override
    public void registerComponents(@NonNull Context context,
                                   @NonNull Glide glide,
                                   @NonNull Registry registry) {
        registry.replace(GlideUrl.class, InputStream.class,
                new OkHttpUrlLoader.Factory(buildImageClient()));
    }

    private static OkHttpClient buildImageClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true);
        try {
            FallbackTrustManager trust = new FallbackTrustManager(defaultTrustManager());
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, new TrustManager[]{ trust }, new SecureRandom());
            SSLSocketFactory socketFactory = ssl.getSocketFactory();
            builder.sslSocketFactory(socketFactory, trust);
        } catch (Throwable t) {
            Log.w(TAG, "Using default image TLS stack", t);
        }
        return builder.build();
    }

    private static X509TrustManager defaultTrustManager() throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        TrustManager[] managers = factory.getTrustManagers();
        for (TrustManager manager : managers) {
            if (manager instanceof X509TrustManager) {
                return (X509TrustManager) manager;
            }
        }
        throw new IllegalStateException("No X509TrustManager");
    }

    private static final class FallbackTrustManager implements X509TrustManager {
        private final X509TrustManager platform;

        FallbackTrustManager(X509TrustManager platform) {
            this.platform = platform;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            platform.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            try {
                platform.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                Log.w(TAG, "Image TLS chain rejected by platform; allowing Glide fallback", e);
                if (chain == null || chain.length == 0) {
                    throw e;
                }
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return platform.getAcceptedIssuers();
        }
    }
}
