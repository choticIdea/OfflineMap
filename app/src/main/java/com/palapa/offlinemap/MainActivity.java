package com.palapa.offlinemap;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.util.Constants;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;

import org.json.JSONException;
import org.json.JSONObject;
import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.MapPosition;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.LayerManager;
import org.mapsforge.map.layer.Layers;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapDataStore;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.ExternalRenderTheme;
import org.mapsforge.map.rendertheme.InternalRenderTheme;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener,LocationListener,ListView.OnItemClickListener{

    public  static ArrayList<JSONObject> clientID;
    private ImageButton clientRouting;
    private ListView clientListView;
    private ClientRoutingAdapter adapter;
    private LayerManager layManager;
    private LocationManager locManager;
    private File mapsFolder;
    private LatLong start;
    private Marker startMarker;
    private LatLong end;
    private LinearLayout infoLayout;
    private LinearLayout mapViewLayout;
    private GraphHopper hopper;
    private MapView mapView;
    private RelativeLayout infoBar;
    private String INDONESIA = "indonesia";
    private TextView textFrom;
    private TextView textTo;
    private TileCache tileCache;
    private TileRendererLayer tileRendererLayer;
    private volatile boolean prepareInProgress = false;
    private volatile boolean shortestPathRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("MainActivity", "First created");
        setContentView(R.layout.activity_main);
        mapViewLayout = (LinearLayout)findViewById(R.id.mapViewLayout);
        clientRouting = (ImageButton) findViewById(R.id.clientsRouting);
        clientID = new ArrayList<>();
        clientListView = (ListView) findViewById(R.id.clientLocation);
//        infoBar.setVisibility(View.GONE);
        adapter = new ClientRoutingAdapter(this,getLayoutInflater(),clientID);
        AndroidGraphicFactory.createInstance(getApplication());
        clientListView.setAdapter(adapter);
        clientListView.setOnItemClickListener(this);
        mapView = new MapView(this);
        mapView.setClickable(true);
        mapView.setBuiltInZoomControls(false);

        layManager = mapView.getLayerManager();
        locManager = (LocationManager)this.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

            alertGPSoff();
        tileCache = AndroidUtil.createTileCache(this, getClass().getSimpleName(), mapView.getModel().displayModel.getTileSize(),
                1f, mapView.getModel().frameBufferModel.getOverdrawFactor());

        boolean greaterOrEqKitkat = Build.VERSION.SDK_INT >= 19;
        //maybe i should place another folder to setup
        if (greaterOrEqKitkat)
        {
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            {
                // TODO ??? show error:"GraphHopper is not usable without an external storage!"
                //some device spare the space in the internal storage for external usage
                return;
            }
            mapsFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "/eletrouting/maps/");
        } else
            mapsFolder = new File(Environment.getExternalStorageDirectory(), "/eletrouting/maps/");

        if (!mapsFolder.exists())
            mapsFolder.mkdirs();


        final File mapFile = new File(mapsFolder, INDONESIA + "-gh");
        if (mapFile.exists())
        {
            log("eksis");
            loadMap(mapFile);
        }
        else
        {
            log("ga eksis");
            // TODO show error:"Folder not found: " mapFile
        }
    }
    void alertGPSoff(){
        AlertDialog a = new AlertDialog.Builder(this)
                .setTitle("GPS tidak menyala")
                .setMessage("Aplikasi ini membutuhkan GPS untuk berjalan optimal")
                .setPositiveButton("Oke", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(i);

                    }
                }).create();

        if(!locManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            a.show();
        a.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                Log.d("Dialog","Exited or..?");
            }
        });
    }
    void loadMap( File areaFolder )
    {
        //TODO show message: "Loading map"
        MapDataStore mapDataStore = new MapFile(new File(areaFolder, INDONESIA + ".map"));

        mapView.getLayerManager().getLayers().clear();

        tileRendererLayer = new TileRendererLayer(tileCache, mapDataStore,
            mapView.getModel().mapViewPosition, false, true, AndroidGraphicFactory.INSTANCE)
        {
            @Override
            public boolean onLongPress(LatLong tapLatLong, Point layerXY, Point tapXY) {
                // deprecated, implemented later with better function,;;return onMapTap(tapLatLong, layerXY, tapXY);
                return false;
            }
            @Override
            public boolean onTap(LatLong tapLatLong, Point layerXY, Point tapXY){
                if(clientListView.getVisibility() == View.VISIBLE)
                {
                    clientListView.setVisibility(View.GONE);
                    clientRouting.setVisibility(View.VISIBLE);
                }
                return false;
            }

        };
        tileRendererLayer.setTextScale(1.5f);

        try { //spiralhalo
            ExternalRenderTheme renderTheme = new ExternalRenderTheme(new File(areaFolder, "rendertheme.xml"));
            tileRendererLayer.setXmlRenderTheme(renderTheme);
        } catch (FileNotFoundException e) {
            tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);
        }


        mapView.getLayerManager().getLayers().add(tileRendererLayer);

        mapViewLayout.addView(mapView);
        pinpointUser();
        loadGraphStorage();
    }

    void pinpointUser() {
         locManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, null);
         Location best = locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
         if(best != null) {
            mapView.getModel().mapViewPosition.setMapPosition(new MapPosition(new LatLong(best.getLatitude(), best.getLongitude()), (byte) 15));
             start = new LatLong(best.getLatitude(),best.getLongitude());
             startMarker = createMarker(start,R.drawable.ic_place_blue_36dp);
             layManager.getLayers().add(startMarker);
        }

    }

    void loadGraphStorage()
    {
        logUser("loading graph (" + Constants.VERSION + ") ... ");
        new GHAsyncTask<Void, Void, Path>()
        {
            protected Path saveDoInBackground( Void... v ) throws Exception
            {
                GraphHopper tmpHopp = new GraphHopper().forMobile();
                tmpHopp.load(new File(mapsFolder, INDONESIA).getAbsolutePath());
                //debug message: "found graph " + tmpHopp.getGraph().toString() + ", nodes:" + tmpHopp.getGraph().getNodes();
                hopper = tmpHopp;
                return null;
            }

            protected void onPostExecute( Path o )
            {
                if (hasError())
                {
                    //debug message: "An error happend while creating graph:" + getErrorMessage();
                } else
                {
                    logUser("Finished loading graph");
                }

                finishPrepare();
            }
        }.execute();
    }

    private void finishPrepare()
    {
        prepareInProgress = false;
    }

    boolean isReady()
    {
        // only return true if already loaded
        if (hopper != null)
            return true;

        if (prepareInProgress)
        {
            logUser("Preparation still in progress");
            return false;
        }
        logUser("Prepare finished but hopper not ready. This happens when there was an error while loading the files");
        return false;
    }

    //ROUTING COOOOOOODEESSSSSSSS

    private Polyline createPolyline( GHResponse response )
    {
        Paint paintStroke = AndroidGraphicFactory.INSTANCE.createPaint();
        paintStroke.setStyle(Style.STROKE);
        paintStroke.setColor(Color.argb(200, 0, 0xCC, 0x99));
        paintStroke.setStrokeWidth(6);

        // TODO: new mapsforge version wants an mapsforge-paint, not an android paint.
        // This doesn't seem to support transparceny
        //paintStroke.setAlpha(128);
        Polyline line = new Polyline(paintStroke, AndroidGraphicFactory.INSTANCE);
        List<LatLong> geoPoints = line.getLatLongs();
        PointList tmp = response.getPoints();
        for (int i = 0; i < response.getPoints().getSize(); i++)
        {
            geoPoints.add(new LatLong(tmp.getLatitude(i), tmp.getLongitude(i)));
        }

        return line;
    }
   //TODO updating below function, createMarker
    private Marker createMarker( LatLong p, int resource )
    {
        Drawable drawable = getResources().getDrawable(resource);
        Bitmap bitmap = AndroidGraphicFactory.convertToBitmap(drawable);
        return new Marker(p, bitmap, 0, -bitmap.getHeight() / 2);
    }

    public void calcPath( final double fromLat, final double fromLon,
                          final double toLat, final double toLon )
    {

        //TODO show message log("calculating path ...");
        new AsyncTask<Void, Void, GHResponse>()
        {
            float time;

            protected GHResponse doInBackground( Void... v )
            {
                StopWatch sw = new StopWatch().start();
                GHRequest req = new GHRequest(fromLat, fromLon, toLat, toLon).
                        setAlgorithm(AlgorithmOptions.DIJKSTRA_BI);
                req.getHints().
                        put("instructions", "false");
                GHResponse resp = hopper.route(req);
                time = sw.stop().getSeconds();
                return resp;
            }

            protected void onPostExecute( GHResponse resp )
            {
                if (!resp.hasErrors())
                {
                    //debug message "from:" + fromLat + "," + fromLon + " to:" + toLat + ","
//                            + toLon + " found path with distance:" + resp.getDistance()
//                            / 1000f + ", nodes:" + resp.getPoints().getSize() + ", time:"
//                            + time + " " + resp.getDebugInfo())
//                    debug message: "the route is " + (int) (resp.getDistance() / 100) / 10f
//                            + "km long, time:" + resp.getMillis() / 60000f + "min, debug:" + time

                    mapView.getLayerManager().getLayers().add(createPolyline(resp));
//                    setInfoBarVisible(true);
                    //mapView.redraw();
                } else
                {
                    //debug message: "Error:" + resp.getErrors();
                }
                shortestPathRunning = false;
            }
        }.execute();
    }

    //ROUTING COOOOOOODEESSSSSSSS

    private void log( String str )
    {
        Toast.makeText(this,str,Toast.LENGTH_LONG);
        Log.i("GH", str);
    }

    private void log( String str, Throwable t )
    {
        Log.i("GH", str, t);
    }

    private void logUser( String str )
    {
        log(str);
        Toast.makeText(this, str, Toast.LENGTH_LONG).show();
    }

    //interactions

    public boolean onMapTap(LatLong tapLatLong, Point layerXY, Point tapXY)
    {
        if (!isReady())
            return false;

        if (shortestPathRunning)
        {
            logUser("Calculation still in progress");
            return false;
        }
        if(start != null) {
            Layers layers = layManager.getLayers();
            end = tapLatLong;
            shortestPathRunning = true;
            Marker marker = createMarker(tapLatLong, R.drawable.place_red);
            if (marker != null) {
                layers.add(marker);
            }

            calcPath(start.latitude, start.longitude, end.latitude,
                    end.longitude);
            return true;
        }
        else
            return false;


    }

    public void removeLayersExceptMap(){
        Layers layers = layManager.getLayers();
        while (layers.size() > 2)
        {
            layers.remove(layers.size()-1);
        }
    }

    public void setInfoBarVisible(boolean visible){
        if(visible && start != null && end != null) {
            DecimalFormat df = new DecimalFormat("#.######");
           // textFrom.setText(df.format(start.latitude) +", "+ df.format(start.longitude));
            textFrom.setText(df.format(end.latitude) +", "+ df.format(end.longitude));
            infoBar.setVisibility(View.VISIBLE);
        } else {
            infoBar.setVisibility(View.GONE);
        }
    }

    public void onClick(View v){
        switch(v.getId()){
           case R.id.buttonNav:
               Intent i = new Intent(this,ClientPick.class);
               startActivity(i);
               break;
            case R.id.buttonGps:
                pinpointUser();
                ///ahahahha
                break;
            case R.id.controlZoomIn:
                mapView.getModel().mapViewPosition.zoomIn();
                break;
            case R.id.controlZoomOut:
                mapView.getModel().mapViewPosition.zoomOut();
                break;
            case R.id.clientsRouting:
                clientListView.setVisibility(View.VISIBLE);
                clientRouting.setVisibility(View.GONE);
                Log.d("Hmm","button didnt work?");

                break;

        }
    }
    @Override
    public void onResume(){
        super.onResume();

        Log.d("MainActivity","resumed!");
        Bundle extras = getIntent().getExtras();
        if(clientID.size() != 0)
            clientRouting.setVisibility(View.VISIBLE);
        else
            clientRouting.setVisibility(View.GONE);
        if(extras != null)
        {
            try {
                clientID.add(new JSONObject(extras.getString("test")));
                adapter.updateData(clientID);
                Log.d("ListView","Datachanged");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            clientRouting.setVisibility(View.VISIBLE);
            LatLong pos = new LatLong(extras.getDouble("Latitude"),extras.getDouble("Longitude"));
            Marker p = createMarker(pos,R.drawable.place_red);
            mapView.getModel().mapViewPosition.setMapPosition(new MapPosition(pos,(byte) 13));
            layManager.getLayers().add(p);
        }



    }
    @Override
    protected  void onNewIntent(Intent intent){
        setIntent(intent);
    }
    @Override
    public  void onPause(){
        super.onPause();
        Log.d("Main","Mainn is paused");
    }
    @Override
    public  void onStop()
    {
        super.onStop();
        Log.d("main","Main is stopped");
    }



    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
       JSONObject client = (JSONObject)adapter.getItem(position);
        //LatLong l = new LatLong(,client.optDouble("Longitude"));
        calcPath(start.latitude,start.longitude,client.optDouble("Latitude"),client.optDouble("Longitude"));
    }
}
