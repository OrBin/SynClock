package orbin;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import java.io.*;
import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import orbin.deskclock.Utils;

public class Sender {

    // This line depends on the constant orbin.Path.BASE_PATH.
    // It is declared in the file Path.java, which is ignored by git.
    private static final String SET_TIME_PAGE_URL = Path.BASE_PATH + "/set";
    private static final String AUTH_PAGE_URL = Path.BASE_PATH + "/auth/googletoken";

    private static boolean writeTimeToServer (long timeToWrite, String idToken, Context context) throws IOException
    {
        //if (idToken == null) // TODO remove
        //    return false;


        HttpClient httpClient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(AUTH_PAGE_URL + "?id_token=" + idToken);
        HttpResponse response = httpClient.execute(httpGet);
        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode != HttpStatus.SC_OK) // TODO find and change to constant
        {
           return false;
        }
        else
        {
            httpGet = new HttpGet(SET_TIME_PAGE_URL + "?id_token=" + idToken + "&time=" + timeToWrite);

            response = httpClient.execute(httpGet);

            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
            {
                return false;
            }
            else
            {
                HttpEntity entity = response.getEntity();

                // Stream content out
                BufferedReader bfrdInput = new BufferedReader(
                        new InputStreamReader(
                                entity.getContent(),
                                "UTF-8"));

                String strResponse = bfrdInput.readLine();

                return ((strResponse != null) && (strResponse.trim().equals("0")));
            }
        }
    }

    public static void sendTimeToServer (final long timeToWriteMillis, final String idToken, final Context context)
    {
        new AsyncTask<Void, Void, Void> ()
        {
            @Override
            protected Void doInBackground(Void... arg0)
            {
                try
                {
                    String myIdToken = Utils.getDefaultSharedPreferences(context).getString("id_token", null);

                    writeTimeToServer(timeToWriteMillis / 1000, myIdToken, context);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
                return null;
            }
        }.execute();
    }
}