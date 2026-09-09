package com.cloudforge.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private WebView webView;

    private static final int PICK_CLOUD_FILE = 1001;

    private String supabaseUrl = "";
    private String supabaseKey = "";
    private String accessToken = "";
    private String userId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        immersive();

        webView = new WebView(this);

        WebSettings s = webView.getSettings();

        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(false);
        s.setDatabaseEnabled(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        webView.clearCache(true);
        webView.clearHistory();

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );

        webView.loadUrl(
                "file:///android_asset/index.html"
        );

        setContentView(webView);
    }

    private void immersive() {
        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void setCloudSession(
                String url,
                String key,
                String token,
                String uid
        ) {
            supabaseUrl = url == null ? "" : url;
            supabaseKey = key == null ? "" : key;
            accessToken = token == null ? "" : token;
            userId = uid == null ? "" : uid;
        }

        @JavascriptInterface
        public void clearCloudSession() {
            supabaseUrl = "";
            supabaseKey = "";
            accessToken = "";
            userId = "";
        }

        @JavascriptInterface
        public void pickCloudFile() {

            runOnUiThread(() -> {

                Intent intent =
                        new Intent(Intent.ACTION_OPEN_DOCUMENT);

                intent.addCategory(
                        Intent.CATEGORY_OPENABLE
                );

                intent.setType("*/*");

                startActivityForResult(
                        intent,
                        PICK_CLOUD_FILE
                );
            });
        }

        @JavascriptInterface
        public void openBrowser(String url) {

            if (url == null || url.trim().isEmpty()) {
                return;
            }

            runOnUiThread(() -> {

                try {
                    Intent intent =
                            new Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(url)
                            );

                    startActivity(intent);

                } catch (Exception ignored) {
                }
            });
        }

        @JavascriptInterface
        public void manageApp(String packageName) {

            if (packageName == null ||
                    packageName.trim().isEmpty()) {
                return;
            }

            runOnUiThread(() -> {

                try {
                    Intent intent =
                            new Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            );

                    intent.setData(
                            Uri.parse(
                                    "package:" + packageName
                            )
                    );

                    startActivity(intent);

                } catch (Exception ignored) {
                }
            });
        }

        @JavascriptInterface
        public void uninstallApp(String packageName) {

            if (packageName == null ||
                    packageName.trim().isEmpty()) {
                return;
            }

            runOnUiThread(() -> {

                try {
                    Intent intent =
                            new Intent(
                                    Intent.ACTION_DELETE
                            );

                    intent.setData(
                            Uri.parse(
                                    "package:" + packageName
                            )
                    );

                    startActivity(intent);

                } catch (Exception ignored) {
                }
            });
        }

        @JavascriptInterface
        public void openSettings() {

            runOnUiThread(() -> {

                try {
                    Intent intent =
                            new Intent(
                                    Settings.ACTION_SETTINGS
                            );

                    startActivity(intent);

                } catch (Exception ignored) {
                }
            });
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != PICK_CLOUD_FILE ||
                resultCode != RESULT_OK ||
                data == null ||
                data.getData() == null) {

            return;
        }

        Uri uri = data.getData();

        new Thread(() -> uploadCloudFile(uri))
                .start();
    }

    private void uploadCloudFile(Uri uri) {

        if (supabaseUrl.isEmpty() ||
                supabaseKey.isEmpty() ||
                accessToken.isEmpty() ||
                userId.isEmpty()) {

            sendUploadResult(
                    false,
                    "Faça login no CloudForge primeiro."
            );

            return;
        }

        HttpURLConnection connection = null;

        try {

            String fileName =
                    "arquivo_" +
                    System.currentTimeMillis();

            String mime =
                    getContentResolver()
                            .getType(uri);

            if (mime == null) {
                mime =
                        "application/octet-stream";
            }

            InputStream input =
                    getContentResolver()
                            .openInputStream(uri);

            if (input == null) {
                throw new Exception(
                        "Não foi possível abrir o arquivo."
                );
            }

            ByteArrayOutputStream buffer =
                    new ByteArrayOutputStream();

            byte[] data =
                    new byte[8192];

            int count;

            while ((count = input.read(data)) != -1) {
                buffer.write(data, 0, count);
            }

            input.close();

            byte[] bytes =
                    buffer.toByteArray();

            buffer.close();

            String objectName =
                    userId +
                    "/" +
                    System.currentTimeMillis() +
                    "_" +
                    fileName;

            URL url =
                    new URL(
                            supabaseUrl +
                            "/storage/v1/object/cloudforge/" +
                            objectName
                    );

            connection =
                    (HttpURLConnection)
                            url.openConnection();

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            connection.setRequestProperty(
                    "apikey",
                    supabaseKey
            );

            connection.setRequestProperty(
                    "Authorization",
                    "Bearer " + accessToken
            );

            connection.setRequestProperty(
                    "Content-Type",
                    mime
            );

            connection.setRequestProperty(
                    "x-upsert",
                    "false"
            );

            connection
                    .getOutputStream()
                    .write(bytes);

            int response =
                    connection.getResponseCode();

            if (response >= 200 &&
                    response < 300) {

                sendUploadResult(
                        true,
                        objectName
                );

            } else {

                sendUploadResult(
                        false,
                        "Erro no upload: " + response
                );
            }

        } catch (Exception e) {

            sendUploadResult(
                    false,
                    e.getMessage() == null
                            ? "Erro no upload."
                            : e.getMessage()
            );

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void sendUploadResult(
            boolean success,
            String message
    ) {

        String safe =
                message == null
                        ? ""
                        : message
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", " ");

        runOnUiThread(() ->

                webView.evaluateJavascript(

                        "if(window.cloudUploadResult){" +
                        "window.cloudUploadResult(" +
                        success +
                        ",'" +
                        safe +
                        "');" +
                        "}",

                        null
                )
        );
    }

    @Override
    public void onBackPressed() {

        if (webView != null &&
                webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        immersive();
    }
}        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
