package orbin;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import java.io.IOException;
import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

public class Sender {

    // The following two lines depend on the constant orbin.Path.BASE_PATH.
    // It is declared in the file Path.java, which is git-ignored.
    private static final String SET_TIME_PAGE_URL = Path.BASE_PATH + "/set";
    private static final String AUTH_PAGE_URL = Path.BASE_PATH + "/auth/googletoken";
    private static final String TAG = "Server updater";
    private static final int    MAX_TRIES = 5;

    private static boolean sendTimeToServer(long timeToWrite, Context context) throws IOException
    {
        AuthHelper.IdToken idToken = AuthHelper.getAuthHelper().getValidIdToken(context);

        if (idToken == null)
        {
            Log.e(TAG, "Could not send alarm time to server: id token is null");
        }
        else
        {
            String idTokenTokenValue = idToken.getTokenValue();

            HttpClient httpClient = new DefaultHttpClient();
            HttpGet httpGet = new HttpGet(AUTH_PAGE_URL + "?id_token=" + idTokenTokenValue);
            HttpResponse response = httpClient.execute(httpGet);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != HttpStatus.SC_OK)
            {
                if (statusCode == HttpStatus.SC_UNAUTHORIZED)
                {
                    AuthHelper.getAuthHelper().invalidateCurrentIdToken(context);
                }

                Log.e(TAG, "Could not send alarm time to server: failed to authenticate (" + statusCode + ")");
            }
            else
            {
                AuthHelper.getAuthHelper().invalidateCurrentIdToken(context);

                httpGet = new HttpGet(SET_TIME_PAGE_URL + "?id_token=" + idTokenTokenValue + "&time=" + timeToWrite);
                response = httpClient.execute(httpGet);
                statusCode = response.getStatusLine().getStatusCode();

                if (statusCode != HttpStatus.SC_OK)
                    Log.e(TAG, "Could not send alarm time to server: failed to send to server (" + statusCode + ")");
                else
                    return true;
            }
        }

        return false;
    }

    public static void sendTimeToServerAsync(final long timeToWriteMillis, final Context context)
    {
        new AsyncTask<Void, Void, Void> ()
        {
            @Override
            protected Void doInBackground(Void... arg0)
            {
                for (int numOfTries = 0; numOfTries < MAX_TRIES; numOfTries++)
                {
                    try
                    {
                        if (sendTimeToServer(timeToWriteMillis / 1000, context))
                        {
                            Log.i(TAG, "Sent alarm time to server successfully");
                            break;
                        }
                        else
                        {
                            Log.e(TAG, "Could not send alarm time to server (try " + numOfTries + " of " + MAX_TRIES + ")");
                        }
                    }
                    catch (IOException ex)
                    {
                        Log.e(TAG, "Could not send alarm time to server: IOException thrown (try " + numOfTries + " of " + MAX_TRIES + ")");
                    }
                }

                return null;
            }
        }.execute();
    }
}