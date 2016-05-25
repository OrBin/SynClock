package orbin;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import java.io.*;
import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 * Created by Or Bin on 15/04/2016.
 */
public class Sender {

    private static String getSetTimePageUrl (Context context)
    {
        // This line depends on the constant orbin.Path.BASE_PATH.
        // It is declared in the file Path.java, which is ignored by git.
        return Path.BASE_PATH + "/set_time.php?time=";
    }

    private static boolean writeTimeToServer (long timeToWrite, Context context) throws ClientProtocolException, IOException
    {

        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget = new HttpGet(getSetTimePageUrl(context) + timeToWrite);

        HttpResponse response = httpclient.execute(httpget);

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

            return ((strResponse != null) &&
                    (strResponse.trim().equals("0")));
        }
    }

    public static void sendTimeToServer (long timeToWrite, final Context context)
    {
        new AsyncTask<Long, Void, Boolean> ()
        {
            @Override
            protected Boolean doInBackground(Long... arg0)
            {
                try
                {
                    return writeTimeToServer(arg0[0], context);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                    return false;
                }
            }

        }.execute(timeToWrite / 1000);
    }
}