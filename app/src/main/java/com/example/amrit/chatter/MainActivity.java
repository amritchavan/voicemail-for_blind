package com.example.amrit.chatter;

import android.Manifest;
import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.graphics.Point;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.amrit.chatter.helper.InternetDetector;
import com.example.amrit.chatter.helper.Utils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.client.util.StringUtils;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    String mailTo, mailFrom, mailBody, mailSubject, start_speech = "tap anywhere on the screen to speak", voice_input, voice_output, action;
    Toolbar toolbar;
    Boolean speak = false;
    private WebView nammaInputWebView;
    private WebView botResponseWebView;
    SharedPreferences preferences;
    String chat = "Hey there!";
    int numberOfClicks = 0;
    GoogleAccountCredential mCredential;
    private Integer n = 0;
    ProgressBar mProgress;
    private final int REQ_CODE_SPEECH_INPUT = 100;
    Button mic_btn;
    public int flag = 0;
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {
            GmailScopes.MAIL_GOOGLE_COM
    };
    private TextToSpeech tts;
    private InternetDetector internetDetector;
    private final int SELECT_PHOTO = 1;
    public String fileName = "";
    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tts = new TextToSpeech(this, this);
        preferences = getSharedPreferences("preference", MODE_PRIVATE);
        if (!(preferences.contains("uid"))) {
            long randomUID = System.currentTimeMillis();
            preferences.edit().putLong("uid", randomUID).commit();
        }
        voice_output = start_speech;
        speakOut(voice_output);
        init();
        mic_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptSpeechInput();
            }
        });

        findViewById(R.id.attachment).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Utils.checkPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                    photoPickerIntent.setType("image/*");
                    startActivityForResult(photoPickerIntent, SELECT_PHOTO);
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, SELECT_PHOTO);
                }
            }
        });

        findViewById(R.id.changeAccount).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Utils.checkPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    startActivityForResult(mCredential.newChooseAccountIntent(), Utils.REQUEST_ACCOUNT_PICKER);
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, SELECT_PHOTO);
                }
            }
        });
    }

    private void init() {


        // Initializing Internet Checker
        internetDetector = new InternetDetector(getApplicationContext());

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        botResponseWebView = (WebView) findViewById(R.id.botResponseWebView);
        botResponseWebView.setBackgroundColor(0);
        nammaInputWebView = (WebView) findViewById(R.id.nammaInputWebView);
        nammaInputWebView.setBackgroundColor(0);
        mic_btn = findViewById(R.id.mic_btn);
        mProgress = (ProgressBar) findViewById(R.id.progressBar);
    }

    @Override
    protected void onStart() {
        speakOut(start_speech);
        numberOfClicks = 0;
        action = null;
        super.onStart();
    }

    private void promptSpeechInput() {
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.speech_prompt));
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.speech_not_supported),
                    Toast.LENGTH_SHORT).show();
            System.out.print("done");
        }
    }

    // Check if the device is connected to the internet
    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo net = cm.getActiveNetworkInfo();
        return net != null && net.isAvailable() && net.isConnected();
    }


    // Exit the app
    private void exit() {
        parseHTMLSetWebView(getString(R.string.exit_message), 1);
        Handler mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 2000);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case Utils.REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    speakOut("This app requires Google Play Services. Please install " +
                            "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi(mic_btn);
                }
                break;
            case Utils.REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi(mic_btn);
                    }
                }
                break;
            case Utils.REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi(mic_btn);
                }
                break;
            case SELECT_PHOTO:
                if (resultCode == RESULT_OK) {
                    final Uri imageUri = data.getData();
                    fileName = getPathFromURI(imageUri);
                }
                break;
            case REQ_CODE_SPEECH_INPUT:
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if(result.get(0).contentEquals("clear")){
                        mailTo = null;
                        mailBody = null;
                        mailSubject = null;
                    }
                    else if (result.get(0).contentEquals("exit")) {
                        numberOfClicks = 0;
                        exitFromApp();
                    } else if (result.get(0).contentEquals("send mail")) {
                        action = "send";
                        speakOut("Enter address to whom you want to send the mail");
                        numberOfClicks++;
                    } else if (action == "send" && numberOfClicks == 1) {
                        mailTo = result.get(0);
                        speakOut("Enter subject");
                        numberOfClicks++;
                    } else if (action == "send" && numberOfClicks == 2) {
                        mailSubject = result.get(0);
                        speakOut("Enter body");
                        numberOfClicks++;
                    } else if (action == "send" && numberOfClicks == 3) {
                        mailBody = result.get(0);
                        numberOfClicks = 0;
                        mailTo = mailTo.toLowerCase().replace(" ", "");
                        speakOut("YOu are sending the mail to" + mailTo + "and the subject is " + mailSubject + "having body" + mailBody + "Do you confirm? Say yes or no");
                        numberOfClicks = 100;
                    }else if(action == "send" && numberOfClicks == 100){
                        if(result.get(0).contentEquals("yes")){
                            getResultsFromApi(mic_btn);
                        }else if(result.get(0).contentEquals("no")){
                            mailTo = null;
                            mailBody = null;
                            mailSubject = null;
                            speakOut("Enter address to whom you want to send the mail");
                            numberOfClicks = 1;
                        }else{
                            speakOut("invalid option");
                            numberOfClicks = 0;
                        }
                    } else if (result.get(0).contentEquals("read mail")) {
                        action = "read";
                        voice_output = "How many mails do you want to read?";
                        speakOut(voice_output);
                        numberOfClicks = 99;
                    } else if (action == "read" && numberOfClicks == 99) {
                        n = Integer.valueOf(result.get(0));
                        getResultsFromApi(mic_btn);
                    }else if(action == "read" && numberOfClicks == 200){
                        if(result.get(0).contentEquals("yes")){
                            mailTo = mailFrom;
                            speakOut("Enter subject");
                            action = "send";
                            numberOfClicks = 2;
                        }
                    } else{
                        chat = result.get(0);
                        parseHTMLSetWebView(chat, 0);
                        new NetworkAsyncTask().execute(chat);
                    }
                }
                break;
        }
    }

    // Show alert dialog with apps listed in it for  user to choose
    private void showAlertDialog(final ArrayList<ApplicationInfo> appList) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose an application");
        builder.setItems(getAppNames(appList), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //Log.v(LOG_TAG, appList.get(which).packageName);
                startActivity(getPackageManager().getLaunchIntentForPackage(appList.get(which).packageName));
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(alertDialog.getWindow().getAttributes());
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        lp.height = (size.y) * 2 / 3;
        alertDialog.getWindow().setAttributes(lp);
    }

    // Get app names string array from a list of ApplicationInfo
    private String[] getAppNames(List<ApplicationInfo> appList) {
        ArrayList<String> appNames = new ArrayList<>();
        for (ApplicationInfo appInfo : appList) {
            appNames.add(getPackageManager().getApplicationLabel(appInfo).toString());
        }
        return appNames.toArray(new String[appNames.size()]);
    }

    // Retrieve the apps which match the given app name
    private ArrayList<ApplicationInfo> retrieveMatchedAppInfos(List<ApplicationInfo> appList, String requestedAppName) {
        ArrayList<ApplicationInfo> matchingAppNames = new ArrayList<>();
        for (ApplicationInfo appInfo : appList) {
            String appName = getPackageManager().getApplicationLabel(appInfo).toString();
            if (appName.toLowerCase().contains(requestedAppName.toLowerCase())) {
                matchingAppNames.add(appInfo);
            }
        }
        return matchingAppNames;
    }
    private class NetworkAsyncTask extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            mProgress.setProgress(0);
            mProgress.setVisibility(View.INVISIBLE);
            botResponseWebView.setVisibility(View.INVISIBLE);
        }

        @Override
        protected String doInBackground(String... talkWord) {
            if (talkWord == null || talkWord[0].isEmpty()) return null;
            NetworkParseResponse botChat = new NetworkParseResponse();
            botChat.appendProgressBar(mProgress);
            botChat.formURL(talkWord[0], preferences.getLong("uid", 0));
            botChat.establishConnectionGetResponse();
            return botChat.getBotResponse();
        }

        @Override
        protected void onPostExecute(String botResponse) {
            mProgress.setVisibility(View.INVISIBLE);
            if (botResponse != null) {
                parseHTMLSetWebView(botResponse, 1);
            }
            botResponseWebView.setVisibility(View.INVISIBLE);
        }
    }

    private void parseHTMLSetWebView(String text, int n) {
        String html;
        html = "<html><head>"
                + "<style type=\"text/css\">";
        if (n == 1) {
            html += "body { color: #FFFFFF; font-size:x-large; text-shadow: 2px 2px 8px #ff8600;}";
            voice_output = text;
            speakOut(voice_output);
        } else
            html += "body { color: #FFFFFF; font-size:x-large; text-shadow: 2px 2px 8px #2450cf31;}";
        html += "</style></head>"
                + "<body>"
                + text
                + "</body></html>";
        if (n == 1)
            botResponseWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        else
            nammaInputWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private class openApp extends AsyncTask<Void, Void, List<ApplicationInfo>> {

        @Override
        protected List<ApplicationInfo> doInBackground(Void[] params) {
            StringBuilder requestedAppName = new StringBuilder(chat.substring(chat.indexOf("open ") + 5));

            List<ApplicationInfo> appList = getPackageManager().getInstalledApplications(0), matchingAppsList;
            matchingAppsList = retrieveMatchedAppInfos(appList, requestedAppName.toString());

        /* If the matchingAppList size be 0, then check Apps with words at the end removed too.
        For example, if "WhatsApp Pro" yields 0 results, then try searching "WhatsApp" too! */
            while (matchingAppsList.size() == 0 && requestedAppName.length() > 0) {
                if (requestedAppName.toString().endsWith(" "))
                    requestedAppName.deleteCharAt(requestedAppName.lastIndexOf(" "));
                int index = requestedAppName.length() - 1;

                for (Character curChar = requestedAppName.charAt(index);
                     !curChar.equals(' ') && !curChar.toString().isEmpty() && index >= 0; ) {
                    curChar = requestedAppName.charAt(index);
                    requestedAppName.deleteCharAt(index);
                    index--;
                }
                //The above for loop removes the word at the end of the requested app name
                //Log.v(LOG_TAG, requestedAppName.toString());
                matchingAppsList = retrieveMatchedAppInfos(appList, requestedAppName.toString());
            }
            return matchingAppsList;
        }

        @Override
        protected void onPostExecute(List<ApplicationInfo> matchingAppsList) {
            if (matchingAppsList.size() == 0)
                parseHTMLSetWebView(getString(R.string.app_not_found), 1);
            else if (matchingAppsList.size() == 1) {
                parseHTMLSetWebView(getString(R.string.app_opened), 1);
                startActivity(getPackageManager().getLaunchIntentForPackage(matchingAppsList.get(0).packageName));
            } else {
                parseHTMLSetWebView(getString(R.string.app_opened), 1);
                showAlertDialog((ArrayList<ApplicationInfo>) matchingAppsList);
            }
        }
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        private void exitFromApp() {
            this.finishAffinity();
        }


        private void getResultsFromApi(View view) {
            if (!isGooglePlayServicesAvailable()) {
                acquireGooglePlayServices();
            } else if (mCredential.getSelectedAccountName() == null) {
                chooseAccount(view);
            } else if (!internetDetector.checkMobileInternetConn()) {
                speakOut("No network connection available.");
            } else if (mailTo == null && action == "send") {
                speakOut("To address Required");
            } else if (mailSubject == null && action == "send") {
                speakOut("Subject Required");
            } else if (mailBody == null && action == "send") {
                speakOut("Message Required");
            } else {
                new MakeRequestTask(this, mCredential, action).execute();
            }
        }

        // Method for Checking Google Play Service is Available
        private boolean isGooglePlayServicesAvailable() {
            GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
            int connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this);
            return connectionStatusCode == ConnectionResult.SUCCESS;
        }

        // Method to Show Info, If Google Play Service is Not Available.
        private void acquireGooglePlayServices() {
            GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
            int connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this);
            if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
                showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
            }
        }

        // Method for Google Play Services Error Info
        void showGooglePlayServicesAvailabilityErrorDialog(final int connectionStatusCode) {
            GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
            Dialog dialog = apiAvailability.getErrorDialog(
                    MainActivity.this,
                    connectionStatusCode,
                    Utils.REQUEST_GOOGLE_PLAY_SERVICES);
            dialog.show();
        }

        // Storing Mail ID using Shared Preferences
        private void chooseAccount(View view) {
            if (Utils.checkPermission(getApplicationContext(), Manifest.permission.GET_ACCOUNTS)) {
                String accountName = getPreferences(Context.MODE_PRIVATE).getString(PREF_ACCOUNT_NAME, null);
                if (accountName != null) {
                    mCredential.setSelectedAccountName(accountName);
                    getResultsFromApi(view);
                } else {
                    // Start a dialog from which the user can choose an account
                    startActivityForResult(mCredential.newChooseAccountIntent(), Utils.REQUEST_ACCOUNT_PICKER);
                }
            } else {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.GET_ACCOUNTS}, Utils.REQUEST_PERMISSION_GET_ACCOUNTS);
            }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
            switch (requestCode) {
                case Utils.REQUEST_PERMISSION_GET_ACCOUNTS:
                    chooseAccount(mic_btn);
                    break;
                case SELECT_PHOTO:
                    Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                    photoPickerIntent.setType("image/*");
                    startActivityForResult(photoPickerIntent, SELECT_PHOTO);
                    break;
            }
        }

        public String getPathFromURI(Uri contentUri) {
            String res = null;
            String[] proj = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(contentUri, proj, "", null, "");
            assert cursor != null;
            if (cursor.moveToFirst()) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                res = cursor.getString(column_index);
            }
            cursor.close();
            return res;
        }

        @Override
        public void onInit(int status) {
            if (status == TextToSpeech.SUCCESS) {

                int result = tts.setLanguage(Locale.US);

                if (result == TextToSpeech.LANG_MISSING_DATA
                        || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "This Language is not supported");
                } else {
                    speakOut(voice_output);

                }
            } else {
                Log.e("TTS", "Initilization Failed!");
            }
        }

        @Override
        protected void onStop() {
            super.onStop();
            if (tts != null) {
                tts.stop();
            }
        }

        private void speakOut(String voice) {

            String text = voice;
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            while (tts.isSpeaking()) {
            }
        }

        @Override
        protected void onDestroy() {
            if (tts != null) {
                tts.shutdown();
            }
            super.onDestroy();
        }

        // Async Task or sending Mail using GMail OAuth
        private class MakeRequestTask extends AsyncTask<Void, Void, String> {

            private com.google.api.services.gmail.Gmail mService = null;
            private Exception mLastError = null;
            private MainActivity activity;
            private String action;

            MakeRequestTask(MainActivity activity, GoogleAccountCredential credential, String task) {
                HttpTransport transport = AndroidHttp.newCompatibleTransport();
                JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
                action = task;
                mService = new com.google.api.services.gmail.Gmail.Builder(
                        transport, jsonFactory, credential)
                        .setApplicationName(getResources().getString(R.string.app_name))
                        .build();
                this.activity = activity;
            }

            @Override
            protected String doInBackground(Void... params) {
                try {
                    if (action == "send") {
                        getDataFromApi();
                    } else if (action == "read") {
                        getMailDataFromApi();
                    }
                } catch (Exception e) {
                    mLastError = e;
                    cancel(true);
                }
                return null;
            }

            private void getMailDataFromApi() throws IOException {
                String user = "me";
                try {
                    receiveMessage(mService, user);
                } catch (MessagingException e) {
                    e.printStackTrace();
                }
            }

            private void getDataFromApi() throws IOException {
                // getting Values for to Address, from Address, Subject and Body
                String user = "me";
                mailFrom = mCredential.getSelectedAccountName();
                MimeMessage mimeMessage;
                String response = "";
                try {
                    mimeMessage = createEmail(mailTo, mailFrom, mailSubject, mailBody);
                    response = sendMessage(mService, user, mimeMessage);
                    System.out.println(response);
                } catch (MessagingException e) {
                    e.printStackTrace();
                }
            }


            // Method to send email
            private String sendMessage(Gmail service,
                                       String userId,
                                       MimeMessage email)
                    throws MessagingException, IOException {
                Message message = createMessageWithEmail(email);
                // GMail's official method to send email with oauth2.0
                message = service.users().messages().send(userId, message).execute();
                System.out.println("Message id: " + message.getId());
                System.out.println(message.toPrettyString());
                return message.getId();
            }

            public List<Message> receiveMessage(Gmail service, String userId) throws MessagingException, IOException {
                //ListMessagesResponse response = service.users().messages().list(userId).setQ("in:inbox -category:{primary} AND is:unread").setMaxResults((long) 1).execute();
                ListMessagesResponse response = service.users().messages().list(userId).setQ("in:inbox is:unread -category:(promotions)").setMaxResults((long) n).execute();
                List<Message> messages = new ArrayList<>();
                messages.addAll(response.getMessages());
                for (Message msg : messages) {
                    Message message = service.users().messages().get(userId, msg.getThreadId()).execute();
                    MimeMessage mail = getMimeMessage(service, userId, msg.getThreadId());
                    mailFrom = message.getPayload().getHeaders().get(16).getValue();
                    mailSubject = mail.getSubject();
                    mailBody = StringUtils.newStringUtf8(Base64.decodeBase64(message.getPayload().getParts().get(0).getBody().getData()));
                    speakOut("The mail is sent from" + mailFrom + "with subject" + mailSubject + "and the body is" + mailBody);
                }
                if(n == 1){
                    speakOut("Do you want to reply? Say yes or no");
                    numberOfClicks = 200;
                }
                return messages;
            }

            public MimeMessage getMimeMessage(Gmail service, String userId, String messageId)
                    throws IOException, MessagingException {
                Message message = service.users().messages().get(userId, messageId).setFormat("raw").execute();

                Base64 base64Url = new Base64(true);
                byte[] emailBytes = base64Url.decodeBase64(message.getRaw());

                Properties props = new Properties();
                Session session = Session.getDefaultInstance(props, null);

                MimeMessage email = new MimeMessage(session, new ByteArrayInputStream(emailBytes));

                return email;
            }

            // Method to create email Params
            private MimeMessage createEmail(String to,
                                            String from,
                                            String subject,
                                            String bodyText) throws MessagingException {
                Properties props = new Properties();
                Session session = Session.getDefaultInstance(props, null);

                MimeMessage email = new MimeMessage(session);
                InternetAddress tAddress = new InternetAddress(to);
                InternetAddress fAddress = new InternetAddress(from);

                email.setFrom(fAddress);
                email.addRecipient(javax.mail.Message.RecipientType.TO, tAddress);
                email.setSubject(subject);

                // Create Multipart object and add MimeBodyPart objects to this object
                Multipart multipart = new MimeMultipart();

                // Changed for adding attachment and text
                // email.setText(bodyText);

                BodyPart textBody = new MimeBodyPart();
                textBody.setText(bodyText);
                multipart.addBodyPart(textBody);

                if (!(activity.fileName.equals(""))) {
                    // Create new MimeBodyPart object and set DataHandler object to this object
                    MimeBodyPart attachmentBody = new MimeBodyPart();
                    String filename = activity.fileName; // change accordingly
                    DataSource source = new FileDataSource(filename);
                    attachmentBody.setDataHandler(new DataHandler(source));
                    attachmentBody.setFileName(filename);
                    multipart.addBodyPart(attachmentBody);
                }

                //Set the multipart object to the message object
                email.setContent(multipart);
                return email;
            }

            private Message createMessageWithEmail(MimeMessage email)
                    throws MessagingException, IOException {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                email.writeTo(bytes);
                String encodedEmail = Base64.encodeBase64URLSafeString(bytes.toByteArray());
                Message message = new Message();
                message.setRaw(encodedEmail);
                return message;
            }

            @Override
            protected void onPreExecute() {
                // mProgress.show();
                super.onPreExecute();
            }

            @Override
            protected void onPostExecute(String s) {
                //  mProgress.hide();
                super.onPostExecute(s);
            }

            @Override
            protected void onCancelled() {
                // mProgress.hide();
                if (mLastError != null) {
                    if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                        showGooglePlayServicesAvailabilityErrorDialog(
                                ((GooglePlayServicesAvailabilityIOException) mLastError)
                                        .getConnectionStatusCode());
                    } else if (mLastError instanceof UserRecoverableAuthIOException) {
                        startActivityForResult(
                                ((UserRecoverableAuthIOException) mLastError).getIntent(),
                                Utils.REQUEST_AUTHORIZATION);
                    } else {
                        speakOut("The following error occurred:\n" + mLastError);
                        Log.v("Error", mLastError + "");
                    }
                } else {
                    speakOut("Request Cancelled.");
                }
            }
        }
    }
