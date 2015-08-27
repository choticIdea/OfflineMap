package com.palapa.offlinemap;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

/**
 * Created by Maulana on 11/08/2015.
 */
public class ClientPick extends Activity implements ListView.OnItemClickListener,View.OnClickListener {
    private JSONArray clients;
    private JSONArray searchResult;
    private EditText search;
    private ListView listView;
    private JSONAdapter adapter;
    private Button searchButton;
    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        setContentView(R.layout.location_pick);
        listView = (ListView) findViewById(R.id.listView);
       searchButton = (Button) findViewById(R.id.searchButton);
        searchButton.setOnClickListener(this);
        Log.d("ClientPick", "Activity started");
        search = (EditText) findViewById(R.id.search);
        adapter = new JSONAdapter(this,getLayoutInflater(),new JSONArray());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);

        searchResult = new JSONArray();
        //loading data
        try {
            File data = new File(Environment.getExternalStorageDirectory(), "eletrouting/clients/example.json");
            FileInputStream dataStream = new FileInputStream(data);
            String jsonString = null;
            FileChannel fc = dataStream.getChannel();
            MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            jsonString = Charset.defaultCharset().decode(bb).toString();
            dataStream.close();
            JSONObject mainNode= new JSONObject(jsonString);
            clients = mainNode.getJSONObject("data").getJSONArray("client");

          /*    TODO:Load this guy when app start, who knows it will be long?
                for (int i = 0; i < clients.length(); i++) {
                JSONObject it = clients.getJSONObject(i);
                Log.i("Client Name", it.getString("Nama"));
                Log.i("Client Name", Double.toString(it.getDouble("Latitude")));
                Log.i("Client Name", Double.toString(it.getDouble("Longitude")));
            }*/
            Toast.makeText(this, "Client data loaded", Toast.LENGTH_SHORT).show();
       // adapter.updateData(clients);
        }catch(Exception e){
            Log.d("JSON error",e.toString());
        }

        Toast.makeText(this, "Client data ready", Toast.LENGTH_SHORT).show();




    }
    private void searchClient(String token) throws JSONException {
        //Special note, make sure the token is lowercased before passed here and make sure the
        //keys are lower cased too.
        int num= 0;
        JSONObject client = null;
       for(int i =0;i<clients.length();i++)
       {
           client = clients.getJSONObject(i);
           if(client.optString("name").toLowerCase().contains(token))
           {
               //populate result array
               searchResult.put(num,client);
               num++;
               Log.d("search",client.optString("name"));
           }
       }
        adapter.updateData(searchResult);
    }
    @Override
    protected  void onResume()
    {
        super.onResume();
        Log.d("ClientPick","Resumed");
    }




    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        JSONObject client = (JSONObject)adapter.getItem(position);

     /*   for(JSONObject z: MainActivity.clientID)
        {
            if(z.equals(client))
                return;
        }*/
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("test",client.toString());
        i.putExtra("Latitude",client.optDouble("Latitude"));
        i.putExtra("Longitude",client.optDouble("Longitude"));
        i.putExtra("ClientName",client.optString("Nama"));
        startActivity(i);
       finish();
    }

    @Override
    public void onClick(View v) {
        if(v == searchButton)
        {
            try {
                searchClient(search.getText().toString().toLowerCase());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
