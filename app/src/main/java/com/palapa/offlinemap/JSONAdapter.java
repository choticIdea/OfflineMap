package com.palapa.offlinemap;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Created by Maulana on 13/08/2015.
 */
public class JSONAdapter extends BaseAdapter {
    Context mContext;
    LayoutInflater mInflater;
    JSONArray mJsonArray;
    public JSONAdapter(Context context, LayoutInflater inflater,JSONArray data) {
        mContext = context;
        mInflater = inflater;
        mJsonArray = data;

    }
    @Override
    public int getCount() {
        Log.d("count?",Integer.toString(mJsonArray.length()));
        return mJsonArray.length();
    }

    @Override
    public Object getItem(int position) {
        return mJsonArray.optJSONObject(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
       Holder holder;
        JSONObject obj = (JSONObject)getItem(position);

        if(convertView == null)
        {
            convertView = mInflater.inflate(R.layout.client_list,null);
            holder = new Holder();
            holder.name = (TextView) convertView.findViewById(R.id.nameField);
            holder.latitude=(TextView) convertView.findViewById(R.id.latitude);
            holder.longitude=(TextView) convertView.findViewById(R.id.longitude);
            convertView.setTag(holder);
        }
        else {
            holder = (Holder) convertView.getTag();
        }
        holder.name.setText(obj.optString("Nama"));
        holder.latitude.setText(Double.toString(obj.optDouble("Latitude")));
        holder.longitude.setText(Double.toString(obj.optDouble("Longitude")));
        return convertView;
    }
    public void updateData(JSONArray data){
        Log.d("lel", "Making view nigga");
        mJsonArray = data;
        notifyDataSetChanged();
    }
}
class Holder{
 TextView name;
 TextView latitude;
 TextView longitude;
}
