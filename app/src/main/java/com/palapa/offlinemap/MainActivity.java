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
import android.widget.TextView;
import android.widget.Toast;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.util.Constants;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;

import org.json.JSONArray;
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener,LocationListener,ListView.OnItemClickListener {

    //public  static ArrayList<JSONObject> clientID;
    // private ImageButton clientRouting;
    //private ListView clientListView;
    // private ClientRoutingAdapter adapter;
    private boolean routingMode = false;
    private LatLong debugStartPoint;
    private ImageButton navButton;
    private ImageButton medicButton;
    private LayerManager layManager;
    private LocationManager locManager;
    private File mapsFolder;
    private LatLong start;
    private Marker startMarker;
    private Marker navMarker;
    private LatLong end;
    private LinearLayout infoLayout;
    private JSONArray hosps;
    private LinearLayout mapViewLayout;
    private GraphHopper hopper;
    private MapView mapView;
    private LinearLayout infoBar;
    private String INDONESIA = "indonesia";
    private TextView hospitalNameView;
    private String hospitalName = "Rumah Sakit ABC";
    private double hospitalDistance =0f;
    private TextView distanceView;
    private TileCache tileCache;
    private Boolean debugMode = false;
    private Boolean inited = false;
    private TileRendererLayer tileRendererLayer;
    private LinearLayout controlLayout;
    private volatile boolean prepareInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        Log.d("MainActivity", "First created");

        setContentView(R.layout.activity_main);
        controlLayout = (LinearLayout) findViewById(R.id.controlLayout);
        navButton = (ImageButton) findViewById(R.id.buttonNav);
        infoBar = (LinearLayout) findViewById(R.id.infobar);
        hospitalNameView = (TextView) findViewById(R.id.hospitalName);
        distanceView = (TextView) findViewById(R.id.distance);
        debugStartPoint = new LatLong(-6.972713, 107.635101);
        mapViewLayout = (LinearLayout) findViewById(R.id.mapViewLayout);
        medicButton = (ImageButton) findViewById(R.id.medicButton);
        locManager = (LocationManager) this.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

        loadHospital();

        alertGPSoff();//TODO fixing this damn GPS
        initMap();//dont call this function when the GPS isn't okay

    }
    void loadHospital()
    {
        try {
            File data = new File(Environment.getExternalStorageDirectory(), "eletrouting/clients/ClientData.json");
            FileInputStream dataStream = new FileInputStream(data);
            String jsonString = null;
            FileChannel fc = dataStream.getChannel();
            MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            jsonString = Charset.defaultCharset().decode(bb).toString();
            dataStream.close();
            JSONObject mainNode= new JSONObject(jsonString);
            hosps = mainNode.getJSONObject("data").getJSONArray("client");


            Toast.makeText(this, "Client data loaded", Toast.LENGTH_SHORT).show();

        }catch(Exception e){
            Log.d("JSON error",e.toString());
        }
        Log.d("Load Hospital","Hospital loaded");
    }

    void initMap() {
        if (!locManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            return;
        AndroidGraphicFactory.createInstance(getApplication());
        mapView = new MapView(this);
        mapView.setClickable(true);
        mapView.setBuiltInZoomControls(false);
        layManager = mapView.getLayerManager();

        tileCache = AndroidUtil.createTileCache(this, getClass().getSimpleName(), mapView.getModel().displayModel.getTileSize(),
                1f, mapView.getModel().frameBufferModel.getOverdrawFactor());

        boolean greaterOrEqKitkat = Build.VERSION.SDK_INT >= 19;
        //maybe i should place another folder to setup
        if (greaterOrEqKitkat) {
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
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
        //extract the file and give user some info about the installation


        final File mapFile = new File(mapsFolder, INDONESIA + "-gh");
        if (mapFile.exists()) {
            log("eksis");
            loadMap(mapFile);
        } else {
            log("ga eksis");
            // TODO show error:"Folder not found: " mapFile
        }
        inited = true;
    }

    void alertGPSoff() {
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

        if (!locManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            a.show();
        a.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                Log.d("Dialog", "Exited or..?");
            }
        });
    }

    void findNearestHospital() {
        medicButton.setVisibility(View.GONE);
        controlLayout.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Mencari Rumah Sakit.....", Toast.LENGTH_LONG).show();
         calcPath(start.latitude, start.longitude);
        locManager.removeUpdates(this);
        routingMode  = true;
        locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 100, this);

    }
    void finishCalc(double distance,JSONObject hospital)
    {
        Toast.makeText(this, "Perhitungan selesai", Toast.LENGTH_SHORT).show();
        hospitalNameView.setText(hospitalName);

        Marker p = createMarker(new LatLong(hospital.optDouble("latitude"),hospital.optDouble("longitude")),R.drawable.ic_place_red_36dp);
        layManager.getLayers().add(p);
        layManager.redrawLayers();
        DecimalFormat df = new DecimalFormat("#.##");
        distanceView.setText(df.format(distance / 1000f) + "  KM");
        infoBar.setVisibility(View.VISIBLE);
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
                /*if(clientListView.getVisibility() == View.VISIBLE)
                {
                    clientListView.setVisibility(View.GONE);
                    clientRouting.setVisibility(View.VISIBLE);
                }*/
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
        mapViewLayout.addView(mapView);//pindahkan ke fungsi yang berbeda nanti
       //mapViewLayout.setVisibility(View.INVISIBLE);
        pinpointUser();
        loadGraphStorage();

    }

    void pinpointUser() {
         if(!debugMode) {
             Location best = null;

             for(String p:locManager.getAllProviders())
             {
                 Location test = locManager.getLastKnownLocation(p);
                 if(test == null)
                     continue;
                 if(best == null ||(( test.getAccuracy() < best.getAccuracy()) &&( test.getAccuracy() != 0.0)))
                     best = test;



             }

             if (best != null) {
                 mapView.getModel().mapViewPosition.setMapPosition(new MapPosition(new LatLong(best.getLatitude(), best.getLongitude()), (byte) 15));
                 start = new LatLong(best.getLatitude(), best.getLongitude());
             }else {
                 mapView.getModel().mapViewPosition.setMapPosition(new MapPosition(debugStartPoint, (byte) 15));
                 start = debugStartPoint;
             }
             //case, what if there is no last known location?

         }else {
             start = debugStartPoint;
         }


        if(startMarker == null) {
            startMarker = createMarker(start, R.drawable.ic_place_blue_36dp);
            layManager.getLayers().add(startMarker);
        }
        //locManager.requestSingleUpdate(LocationManager.GPS_PROVIDER,this,null);
        locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 100, this);

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
    public void calcPath(final double fromLat,final double fromLon) {
        new AsyncTask<Void, Void, GHResponse>()
        {
            JSONObject hospital;
            int iterator;
            protected GHResponse doInBackground( Void... v )
            {
                GHRequest req = null;
                GHResponse resp = null;
                GHResponse shortest =null;
               // StopWatch sw = new StopWatch().start();
                for(iterator = 0;iterator < hosps.length();iterator++) {
                    if(hosps.optJSONObject(iterator) == null)
                        continue;
                    Log.d("Routing hospital",""+hosps.optJSONObject(iterator).optString("name")+" "+hosps.length());
                    req = new GHRequest(fromLat, fromLon,  hosps.optJSONObject(iterator).optDouble("latitude")
                                     ,hosps.optJSONObject(iterator).optDouble("longitude")).
                            setAlgorithm(AlgorithmOptions.DIJKSTRA_BI);
                    req.getHints().
                            put("instructions", "false");
                    resp = hopper.route(req);
                    if(!resp.hasErrors()) {
                       if(shortest == null) {
                       shortest = resp;
                           hospital = hosps.optJSONObject(iterator);
                       }
                        else {
                               if(resp.getDistance() < shortest.getDistance())
                                   shortest = resp;
                       }
                    }
                }


               // time = sw.stop().getSeconds();
                hospitalName = hospital.optString("name");
                Log.d("NUlll?",""+(hospital == null));
                return shortest;
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
                    // resp.getDistance();
                    mapView.getLayerManager().getLayers().add(createPolyline(resp));
//                    setInfoBarVisible(true);
                    //mapView.redraw();
                    finishCalc(resp.getDistance(),hospital);
                } else
                {
                    //debug message: "Error:" + resp.getErrors();
                }



            }
        }.execute();
    }
    public void calcPath( final double fromLat, final double fromLon,
                          final double toLat, final double toLon )
    {


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
                   // resp.getDistance();
                    mapView.getLayerManager().getLayers().add(createPolyline(resp));
//                    setInfoBarVisible(true);
                    //mapView.redraw();
                } else
                {
                    //debug message: "Error:" + resp.getErrors();
                }



            }
        }.execute();
    }

    //ROUTING COOOOOOODEESSSSSSSS

    private void log( String str )
    {
        Toast.makeText(this, str, Toast.LENGTH_LONG);
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



    public void removeLayersExceptMap(){
        Layers layers = layManager.getLayers();
        while (layers.size() > 2)
        {
            layers.remove(layers.size()-1);
        }
    }

    public void setInfoBarVisible(boolean visible){
        if(visible && start != null && end != null) {

            //textFrom.setText(df.format(start.latitude) +", "+ df.format(start.longitude));
            //textFrom.setText(df.format(end.latitude) +", "+ df.format(end.longitude));
            infoBar.setVisibility(View.VISIBLE);
        } else {
            infoBar.setVisibility(View.GONE);
        }
    }

    public void onClick(View v){
        switch(v.getId()){
           case R.id.medicButton:
               findNearestHospital();
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
           /* case R.id.buttonClosePoint:
                removeLayersExceptMap();
                locManager.removeUpdates(this);
                startMarker.setLatLong(start);
                startMarker.requestRedraw();
                navButton.setVisibility(View.VISIBLE);
                infoBar.setVisibility(View.GONE);
                routingMode = false;
                break;
                */
          /*  case R.id.clientsRouting:
                clientListView.setVisibility(View.VISIBLE);
                clientRouting.setVisibility(View.GONE);
                Log.d("Hmm","button didnt work?");

                break;*/

        }
    }
    @Override
    public void onResume(){
        super.onResume();
        if(!inited)
            initMap();
        Log.d("MainActivity","resumed!");
     /*    Bundle extras = getIntent().getExtras();
       if(clientID.size() != 0)
            clientRouting.setVisibility(View.VISIBLE);
        else
            clientRouting.setVisibility(View.GONE);

        {
            try {
                clientID.add(new JSONObject(extras.getString("test")));
                adapter.updateData(clientID);
                Log.d("ListView","Datachanged");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            clientRouting.setVisibility(View.VISIBLE);

        */
    /*    if(extras != null ){
    LatLong pos = new LatLong(extras.getDouble("Latitude"),extras.getDouble("Longitude"));

    Marker p = createMarker(pos,R.drawable.place_red);
    mapView.getModel().mapViewPosition.setMapPosition(new MapPosition(pos, (byte) 13));
    layManager.getLayers().add(p);
            Toast.makeText(this, "Menghitung rute , " +
                    "tunggu hingga muncul jalur berwarna hijau", Toast.LENGTH_LONG).show();
            calcPath(start.latitude, start.longitude, pos.latitude, pos.longitude);
            navButton.setVisibility(View.GONE);
            infoBar.setVisibility(View.VISIBLE);
            textFrom.setText(Double.toString(start.latitude)+" "+Double.toString(start.longitude));
            textTo.setText(Double.toString(extras.getDouble("Latitude"))+" "+Double.toString(extras.getDouble("Longitude")));
            routingMode = true;
           // locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,100,1,this );
            navMarker = createMarker(start,R.drawable.ic_place_green_36dp);
            layManager.getLayers().add(navMarker);
            Toast.makeText(this,"Tanda hijau akan mulai bergerak jika GPS telah mengunci posisi",Toast.LENGTH_LONG).show();

*/

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
        start = new LatLong(location.getLatitude(),location.getLongitude());
        if(routingMode)
     {

        navMarker.setLatLong(start);
         navMarker.requestRedraw();
     }
        else {

            startMarker.setLatLong(start);
            startMarker.requestRedraw();
        }
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
      /* JSONObject client = (JSONObject)adapter.getItem(position);
        //LatLong l = new LatLong(,client.optDouble("Longitude"));

        */
    }
}
