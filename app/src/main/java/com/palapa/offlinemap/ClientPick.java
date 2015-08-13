package com.palapa.offlinemap;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
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
public class ClientPick extends Activity implements View.OnKeyListener,ListView.OnItemClickListener {
    private JSONArray clients;
    private EditText search;
    private ListView listView;
    private JSONAdapter adapter;
    @Override
    protected void onCreate(Bundle savedInstance){
        super.onCreate(savedInstance);
        setContentView(R.layout.location_pick);
        listView = (ListView) findViewById(R.id.listView);

        Log.d("ClientPick", "Activity started");
        search = (EditText) findViewById(R.id.search);
        search.setOnKeyListener(this);
        adapter = new JSONAdapter(this,getLayoutInflater(),new JSONArray());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);
        //loading data
        try {
            File data = new File(Environment.getExternalStorageDirectory(), "ClientData.json");
            FileInputStream dataStream = new FileInputStream(data);
            String jsonString = null;
            FileChannel fc = dataStream.getChannel();
            MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            jsonString = Charset.defaultCharset().decode(bb).toString();
            dataStream.close();
            JSONObject mainNode= new JSONObject(jsonString);
            clients = mainNode.getJSONArray("data");
          /*  for (int i = 0; i < clients.length(); i++) {
                JSONObject it = clients.getJSONObject(i);
                Log.i("Client Name", it.getString("Nama"));
                Log.i("Client Name", Double.toString(it.getDouble("Latitude")));
                Log.i("Client Name", Double.toString(it.getDouble("Longitude")));
            }*/
            Toast.makeText(this, "Client data loaded", Toast.LENGTH_SHORT).show();
        adapter.updateData(clients);
        }catch(Exception e){
            Log.d("JSON error",e.toString());
        }

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                //TODO create a plausible search engine
                JSONObject j = null;
                try {
                    for (int i = 0; i < clients.length(); i++) {

                        j = (JSONObject) clients.get(i);
                        if (j.getString("Nama").equals(s.toString())) {
                            Log.d("search attempt", "success");
                            break;
                        } else {
                            Log.d("search attempt fails", j.get("Nama") + " " + s.toString());
                        }

                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });


    }
    @Override
    protected  void onResume()
    {
        super.onResume();
        Log.d("ClientPick","Resumed");
    }


    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Intent i = new Intent(this, MainActivity.class);
        JSONObject client = (JSONObject)adapter.getItem(position);
        i.putExtra("Latitude",client.optDouble("Latitude"));
        i.putExtra("Longitude",client.optDouble("Longitude"));
        startActivity(i);
      //  finish();
    }
}
