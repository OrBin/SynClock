package orbin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.text.format.DateUtils;
import android.util.Log;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;

import orbin.deskclock.R;
import orbin.deskclock.Utils;

public class AuthHelper implements GoogleApiClient.OnConnectionFailedListener
{
    private static AuthHelper authHelperInstance;

    private static final String ID_TOKEN_KEY = "id_token";
    private static final String ID_TOKEN_EXPIRY_TIME_MILLIS_KEY = "id_token_expiry_time_millis";
    private static final String REFRESH_TOKEN_KEY = "refresh_token";
    private static final String TAG = "AuthHelper";

    public class IdToken
    {
        private String token;
        private long expiryTimeMillis;

        public IdToken(String tokenValue, long expiryTimeMillis)
        {
            this.token = tokenValue;
            this.expiryTimeMillis = expiryTimeMillis;
        }

        public String getTokenValue()
        {
            return this.token;
        }

        public long getExpiryTimeMillis()
        {
            return this.expiryTimeMillis;
        }

        public boolean isValid()
        {
            return this.expiryTimeMillis > System.currentTimeMillis();
        }

        @Override
        public String toString()
        {
            return this.token;
        }
    }

    private AuthHelper() {}

    public static AuthHelper getAuthHelper()
    {
        if (authHelperInstance == null)
        {
            authHelperInstance = new AuthHelper();
        }

        return authHelperInstance;
    }
    public String getRefreshToken(Context context)
    {
        SharedPreferences sharedPrefs = Utils.getDefaultSharedPreferences(context);
        return sharedPrefs.getString(REFRESH_TOKEN_KEY, null);
    }

    public void signInAndGetRefreshToken (FragmentActivity callerActivity, final int requestCode)
    {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(callerActivity.getString(R.string.server_client_id))
                .requestServerAuthCode(callerActivity.getString(R.string.server_client_id), true)
                .requestEmail()
                .build();

        // Build GoogleAPIClient with the Google Sign-In API and the above options.
        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(callerActivity)
                .enableAutoManage(callerActivity /* Activity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        callerActivity.startActivityForResult(signInIntent, requestCode);
    }

    public void handleSignInResult(final Activity callerActivity, Intent data)
    {
        GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);

        if (result.isSuccess())
        {
            GoogleSignInAccount acct = result.getSignInAccount();

            final String serverAuthCode = acct.getServerAuthCode();

            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... arg0)
                {
                    try
                    {
                        HttpClient httpClient = new DefaultHttpClient();
                        HttpPost httpPost = new HttpPost("https://www.googleapis.com/oauth2/v4/token");

                        String postBody = "code=" + URLEncoder.encode(serverAuthCode, "UTF-8") + "&" +
                                "client_id=" + URLEncoder.encode(callerActivity.getString(R.string.server_client_id), "UTF-8") + "&" +
                                "client_secret=" + URLEncoder.encode(callerActivity.getString(R.string.client_secret), "UTF-8") + "&" +
                                "redirect_uri=" + URLEncoder.encode(callerActivity.getString(R.string.redirect_uri), "UTF-8") + "&" +
                                "grant_type=authorization_code";

                        HttpEntity entity = new StringEntity(postBody);//ByteArrayEntity(postBodyBytes);
                        httpPost.setEntity(entity);

                        httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
                        httpPost.setHeader("Accept", "application/json");

                        HttpResponse response = httpClient.execute(httpPost);
                        long tokenTimeMillis = System.currentTimeMillis();
                        String responseBody = EntityUtils.toString(response.getEntity());

                        JSONObject jsonResponseBody = new JSONObject(responseBody);
                        long expiresInSec = jsonResponseBody.getLong("expires_in");
                        String newIdToken = jsonResponseBody.getString("id_token");
                        String refreshToken = jsonResponseBody.getString("refresh_token");

                        SharedPreferences.Editor sharedPrefsEditor = Utils.getDefaultSharedPreferences(callerActivity).edit();
                        sharedPrefsEditor.putString(ID_TOKEN_KEY, newIdToken);
                        sharedPrefsEditor.putString(REFRESH_TOKEN_KEY, refreshToken);
                        sharedPrefsEditor.putLong(ID_TOKEN_EXPIRY_TIME_MILLIS_KEY, tokenTimeMillis + expiresInSec*DateUtils.SECOND_IN_MILLIS);
                        sharedPrefsEditor.commit();
                    }
                    catch (JSONException exc)
                    {
                        // TODO show a notification
                        Log.e(TAG, "Could not save login data: failed to parse JSON");
                    }
                    catch (IOException exc)
                    {
                        // TODO show a notification
                        Log.e(TAG, "Could not save login data: IOException thrown");
                    }

                    return null;
                }
            }.execute();
        }
        else
        {
            // TODO show a notification
            Log.e(TAG, "Could not save login data: login was unsuccessful");
        }
    }

    public IdToken getValidIdToken (Context context)
    {
        SharedPreferences sharedPrefs = Utils.getDefaultSharedPreferences(context);
        long expiryTimeMillis = sharedPrefs.getLong(ID_TOKEN_EXPIRY_TIME_MILLIS_KEY, 0);
        String idToken = sharedPrefs.getString(ID_TOKEN_KEY, null);

        if ((expiryTimeMillis > System.currentTimeMillis() + DateUtils.SECOND_IN_MILLIS) ||
            (idToken == null))
        {
            // If no token is saved, or the token have expired or is about to expire, get a new token
            String refreshToken = sharedPrefs.getString(REFRESH_TOKEN_KEY, null);
            if (refreshToken == null)
            {
                // TODO show a notification?
                Log.e(TAG, "Could not get a new id token: no refresh token");
                return null;
            }

            try
            {
                return fetchNewIdToken(refreshToken, context);
            }
            catch (JSONException exc)
            {
                // TODO show a notification
                Log.e(TAG, "Could not get a new id token: failed to parse JSON");
                return null;
            }
            catch (IOException exc)
            {
                // TODO show a notification
                Log.e(TAG, "Could get a new id token: IOException thrown");
                return null;
            }
        }
        else
        {
            return new IdToken(idToken, expiryTimeMillis);
        }
    }

    public IdToken fetchNewIdToken(String refreshToken, Context context) throws IOException, JSONException
    {
        HttpClient httpClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost("https://www.googleapis.com/oauth2/v4/token");

        String postBody = "refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8") + "&" +
                "client_id=" + URLEncoder.encode(context.getString(R.string.server_client_id), "UTF-8") + "&" +
                "client_secret=" + URLEncoder.encode(context.getString(R.string.client_secret), "UTF-8") + "&" +
                "grant_type=refresh_token";

        HttpEntity entity = new StringEntity(postBody);
        httpPost.setEntity(entity);

        httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");

        HttpResponse response = httpClient.execute(httpPost);
        long responseTimeMillis = System.currentTimeMillis();
        String responseBody = EntityUtils.toString(response.getEntity());

        JSONObject jsonResponseBody = new JSONObject(responseBody);
        long expiresInSec = jsonResponseBody.getLong("expires_in");
        String newIdToken = jsonResponseBody.getString("id_token");


        long tokenExpiryTimeMillis = responseTimeMillis + expiresInSec*DateUtils.SECOND_IN_MILLIS;

        SharedPreferences.Editor sharedPrefsEditor = Utils.getDefaultSharedPreferences(context).edit();
        sharedPrefsEditor.putString(ID_TOKEN_KEY, newIdToken);
        sharedPrefsEditor.putString(REFRESH_TOKEN_KEY, refreshToken);
        sharedPrefsEditor.putLong(ID_TOKEN_EXPIRY_TIME_MILLIS_KEY, tokenExpiryTimeMillis);
        sharedPrefsEditor.commit();

        return new IdToken(newIdToken, tokenExpiryTimeMillis);
    }

    public void invalidateCurrentIdToken(Context context)
    {
        SharedPreferences.Editor sharedPrefsEditor = Utils.getDefaultSharedPreferences(context).edit();
        sharedPrefsEditor.putLong(ID_TOKEN_EXPIRY_TIME_MILLIS_KEY, 0);
        sharedPrefsEditor.commit();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult)
    {
        // TODO show a notification
        Log.e(TAG, "Connection failed");
    }
}
