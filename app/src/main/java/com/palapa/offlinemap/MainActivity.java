package com.palapa.offlinemap;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
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

import java.io.File;
import java.io.FileNotFoundException;
import java.text.DecimalFormat;
import java.util.List;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.MapPosition;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.Layers;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapDataStore;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.InternalRenderTheme;
import org.mapsforge.map.rendertheme.ExternalRenderTheme;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private File mapsFolder;
    private LatLong start;
    private LatLong end;
    private LinearLayout infoLayout;
    private LinearLayout mapViewLayout;
    private GraphHopper hopper;
    private MapView mapView;
    private RelativeLayout infoBar;
    private String currentArea = "indonesia";
    private TextView textFrom;
    private TextView textTo;
    private TileCache tileCache;
    private TileRendererLayer tileRendererLayer;
    private volatile boolean prepareInProgress = false;
    private volatile boolean shortestPathRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mapViewLayout = (LinearLayout)findViewById(R.id.mapViewLayout);
        infoBar = (RelativeLayout) findViewById(R.id.infobar);
        textFrom= (TextView) findViewById(R.id.from);
        textTo  = (TextView) findViewById(R.id.to);

        infoBar.setVisibility(View.GONE);

        AndroidGraphicFactory.createInstance(getApplication());

        mapView = new MapView(this);
        mapView.setClickable(true);
        mapView.setBuiltInZoomControls(false);


        tileCache = AndroidUtil.createTileCache(this, getClass().getSimpleName(), mapView.getModel().displayModel.getTileSize(),
                1f, mapView.getModel().frameBufferModel.getOverdrawFactor());

        boolean greaterOrEqKitkat = Build.VERSION.SDK_INT >= 19;
        if (greaterOrEqKitkat)
        {
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            {
                // TODO ??? show error:"GraphHopper is not usable without an external storage!"
                return;
            }
            mapsFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "/graphhopper/maps/");
        } else
            mapsFolder = new File(Environment.getExternalStorageDirectory(), "/graphhopper/maps/");

        if (!mapsFolder.exists())
            mapsFolder.mkdirs();


        final File areaFolder = new File(mapsFolder, currentArea + "-gh");
        if (areaFolder.exists())
        {
            log("eksis");
            loadMap(areaFolder);
        }
        else
        {
            log("ga eksis");
            // TODO show error:"Folder not found: " areaFolder
        }
    }

    void loadMap( File areaFolder )
    {
        //TODO show message: "Loading map"
        MapDataStore mapDataStore = new MapFile(new File(areaFolder, currentArea + ".map"));

        mapView.getLayerManager().getLayers().clear();

        tileRendererLayer = new TileRendererLayer(tileCache, mapDataStore,
            mapView.getModel().mapViewPosition, false, true, AndroidGraphicFactory.INSTANCE)
        {
            @Override
            public boolean onLongPress(LatLong tapLatLong, Point layerXY, Point tapXY) {
                return onMapTap(tapLatLong, layerXY, tapXY);
            }

        };
        tileRendererLayer.setTextScale(1.5f);

        try { //spiralhalo
            ExternalRenderTheme renderTheme = new ExternalRenderTheme(new File(areaFolder, "rendertheme.xml"));
            tileRendererLayer.setXmlRenderTheme(renderTheme);
        } catch (FileNotFoundException e) {
            tileRendererLayer.setXmlRenderTheme(InternalRenderTheme.OSMARENDER);
        }

        pinpointUser();
        mapView.getLayerManager().getLayers().add(tileRendererLayer);

        mapViewLayout.addView(mapView);
        loadGraphStorage();
    }

    void pinpointUser() {
        Float bestAccuracy = Float.MAX_VALUE;
        Location best = null;
        LocationManager lm = (LocationManager) this.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = lm.getAllProviders();
        for(String p:providers){
            Location l = lm.getLastKnownLocation(p);
            if(l != null){
                Float accuracy = l.getAccuracy();
                if(accuracy < bestAccuracy){
                    best = l;
                    bestAccuracy = accuracy;
                }
            }
        }
        if(best != null) {
            mapView.getModel().mapViewPosition.setMapPosition(new MapPosition(new LatLong(best.getLatitude(), best.getLongitude()), (byte) 15));
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
                tmpHopp.load(new File(mapsFolder, currentArea).getAbsolutePath());
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
                    //debug message: "Finished loading graph. Press long to define where to start and end the route.";
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
//        paintStroke.setDashPathEffect(new float[]
//                {
//                        25, 15
//                });
        paintStroke.setStrokeWidth(6);

        // TODO: new mapsforge version wants an mapsforge-paint, not an android paint.
        // This doesn't seem to support transparceny
        //paintStroke.setAlpha(128);
        Polyline line = new Polyline((org.mapsforge.core.graphics.Paint) paintStroke, AndroidGraphicFactory.INSTANCE);
        List<LatLong> geoPoints = line.getLatLongs();
        PointList tmp = response.getPoints();
        for (int i = 0; i < response.getPoints().getSize(); i++)
        {
            geoPoints.add(new LatLong(tmp.getLatitude(i), tmp.getLongitude(i)));
        }

        return line;
    }

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
                    setInfoBarVisible(true);
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
        Layers layers = mapView.getLayerManager().getLayers();

        if (start != null && end == null)
        {
            end = tapLatLong;
            shortestPathRunning = true;
            Marker marker = createMarker(tapLatLong, R.drawable.place_red);
            if (marker != null)
            {
                layers.add(marker);
            }

            calcPath(start.latitude, start.longitude, end.latitude,
                    end.longitude);
        }
        else
        {
            setInfoBarVisible(false);
            start = tapLatLong;
            end = null;
            // remove all layers but the first one, which is the map
            removeLayersExceptMap();

            Marker marker = createMarker(start, R.drawable.place_blue);
            if (marker != null)
            {
                layers.add(marker);
            }
        }
        return true;
    }

    public void removeLayersExceptMap(){
        Layers layers = mapView.getLayerManager().getLayers();
        while (layers.size() > 1)
        {
            layers.remove(1);
        }
    }

    public void setInfoBarVisible(boolean visible){
        if(visible && start != null && end != null) {
            DecimalFormat df = new DecimalFormat("#.######");
            textFrom.setText(df.format(start.latitude) +", "+ df.format(start.longitude));
            textFrom.setText(df.format(end.latitude) +", "+ df.format(end.longitude));
            infoBar.setVisibility(View.VISIBLE);
        } else {
            infoBar.setVisibility(View.GONE);
        }
    }

    public void onClick(View v){
        switch(v.getId()){
//            case R.id.buttonNav:
//                break;
            case R.id.buttonGps:
                pinpointUser();
                break;
            case R.id.controlZoomIn:
                mapView.getModel().mapViewPosition.zoomIn();
                break;
            case R.id.controlZoomOut:
                mapView.getModel().mapViewPosition.zoomOut();
                break;
            case R.id.buttonClosePoint:
                removeLayersExceptMap();
                setInfoBarVisible(false);
                start = null;
                end = null;
                break;
        }
    }
}
