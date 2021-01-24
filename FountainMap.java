package com.frib.mark.romefountains;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.support.design.widget.Snackbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class FountainMap extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks, LocationListener {
    public static final String USER_LAT_MSG = "com.example.mark.romefountains.USER_LAT";
    public static final String USER_LON_MSG = "com.example.mark.romefountains.USER_LON";
    private String rfUserLanguage;

    private boolean initialized = false, isConnected = false;
    private LocationSettingsStates rfStates;
    private Location rfUserLocation;
    private LocationCallback rfLocationCallback;
    private GoogleMap mMap;
    private GoogleApiClient rfGAPIClient;
    private LocationRequest rfLocationRequest;
    private AlertDialog aboutDialog;
    public static final int LOCATION_PERMISSION = 123;
    public static final int CAMERA_PERMISSION = 124;
    public static final int STORAGE_PERMISSION = 125;
    public static final int REQUEST_CHECK_SETTINGS = 126;
    public static final int LOCATION_PERMISSION_SETTINGS_REQUEST = 127;
    public static final int LOCATION_PERMISSION_UPDATES = 128;
    public static final int ADD_FOUNTAIN = 220;
    public static final int CAMERA_REQUEST = 224;
    private Marker rfCurrentMarker;
    private Snackbar rfSnackbar;
    private Map<Marker, String> rfMarkerList;

    private class rfDownloadTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) { try { return downloadUrl(urls[0]); } catch (IOException e) { return "Unable to retrieve web page."; } }
        @Override
        protected void onPostExecute(String result) { displayFountainMarkers(result); }

        private String downloadUrl(String u) throws IOException {
            InputStream is = null;
            try {
                URL url = new URL(u);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000 /* milliseconds */);
                conn.setConnectTimeout(15000 /* milliseconds */);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                conn.connect();
                is = conn.getInputStream();  //int response = conn.getResponseCode();

                String contentAsString = getPageFromStream(is);
                return contentAsString;
            } finally { if (is != null) { is.close(); } }
        }
        public String getPageFromStream(InputStream s) throws IOException {
            byte[] bytes = new byte[1000];
            StringBuilder x = new StringBuilder();
            int numRead;
            while ((numRead = s.read(bytes)) >= 0) {
                x.append(new String(bytes, 0, numRead));
            }
            return x.toString();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fountain_map);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        myToolbar.setTitleTextColor(0xFFFFFFFF);
        setSupportActionBar(myToolbar);

        //ensure connectivity
        ConnectivityManager cm =
                (ConnectivityManager)this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();

        if(isConnected) {
            // Obtain the SupportMapFragment and get notified when the map is ready to be used.
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map);
            mapFragment.getMapAsync(this);

            rfUserLanguage = Locale.getDefault().getDisplayLanguage();

            rfUserLocation = new Location("dummy");
            rfSnackbar = Snackbar.make(findViewById(R.id.fountainMapLayou), "Hello Fountain Map User", Snackbar.LENGTH_INDEFINITE);
            rfSnackbar.getView().setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary));
            TextView snackText = (TextView) rfSnackbar.getView().findViewById(android.support.design.R.id.snackbar_text);
            snackText.setMaxLines(5);

            rfMarkerList = new HashMap<>();

            if (rfGAPIClient == null) {
                rfGAPIClient = new GoogleApiClient.Builder(this)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApi(LocationServices.API)
                        .build();
            }

            rfLocationRequest = new LocationRequest();
            rfLocationRequest.setInterval(10000);
            rfLocationRequest.setFastestInterval(5000);
            rfLocationRequest.setSmallestDisplacement(10.0f);
            rfLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

            createAboutDialog();
        } else {
            log("Rome Fountains cannot run without internet!");
        }
    }

    private void addFountain() {
        if(rfUserLocation != null) {
            Intent intent = new Intent(this, AddFountain.class);
            // !!! if user location cannot be determined (i.e. bc settings off) app will crash
            intent.putExtra(USER_LAT_MSG, String.valueOf(rfUserLocation.getLatitude()));
            intent.putExtra(USER_LON_MSG, String.valueOf(rfUserLocation.getLongitude()));
            startActivityForResult(intent, ADD_FOUNTAIN);
        } else
            log(this.getString(R.string.disabledAddWithoutGPS));
    }

    private void createAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.aboutInfo);
        builder.setNegativeButton(R.string.notnow, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) { } });
        builder.setPositiveButton(R.string.sendmail, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) { FountainMap.this.sendMail(); } });
        aboutDialog = builder.create();
    }

    private void displayFountainMarkers(String result) {
        Marker newMarker;
        result = result.substring(0, result.length() - 1);
        String rfTempMarkerList[] = result.split(";");
        String rfTempMarker[];
        double lat, lng;
        LatLng markerLatLng;
        for (String listItem : rfTempMarkerList) {
            rfTempMarker = listItem.split("&");
            lat = Double.parseDouble(rfTempMarker[0]);
            lng = Double.parseDouble(rfTempMarker[1]);
            markerLatLng = new LatLng(lat, lng);
            newMarker = mMap.addMarker(new MarkerOptions().position(markerLatLng).title(rfTempMarker[2]).icon(BitmapDescriptorFactory.fromResource(R.mipmap.droplet_marker)));
            if(rfTempMarker.length > 3)
                rfMarkerList.put(newMarker, rfTempMarker[3]);
            else rfMarkerList.put(newMarker, null);
        }
    }

    private void getFountainMarkers(){ new rfDownloadTask().execute("http://www.americaninrome.us/rfmarkers.php?lang=" + rfUserLanguage); }

    private void goToRome() { mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(41.906755, 12.492953), 12.0f)); }

    private void log(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private Location getLastKnownLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            initialized = true;
            return new Location(LocationServices.FusedLocationApi.getLastLocation(rfGAPIClient));
        }
        else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION);
            //log("No permission");
            return null;
        }
    }

    private void moveToCurrent() {
        if(rfCurrentMarker != null)
            rfCurrentMarker.remove();
        if(rfUserLocation != null) {
            double lat = rfUserLocation.getLatitude(); double lon = rfUserLocation.getLongitude();
            rfCurrentMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(lat, lon)).title(this.getString(R.string.youAreHere)));
            mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(lat, lon)));
            rfCurrentMarker.showInfoWindow();
        } //else Toast.makeText(this, "Null user location", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        switch (requestCode) {
            case ADD_FOUNTAIN:
                // Make sure the request was successful
                if (resultCode == RESULT_OK) {
                    log(this.getString(R.string.sendingInfo));
                } else {
                    log(this.getString(R.string.notSent));
                }
                break;
            case REQUEST_CHECK_SETTINGS:
                if (resultCode == RESULT_OK) {
                } else {
                    log(this.getString(R.string.noLocation));
                    goToRome();
                }
                break;
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        startLocationUpdates();
        setupSettingsRequest();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) { }

    @Override
    public void onConnectionSuspended(int cause) { }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.rf_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onLocationChanged(Location location) {
        boolean show = false;
        if(rfCurrentMarker != null) {
            show = rfCurrentMarker.isInfoWindowShown();
            rfCurrentMarker.remove();
        }
        rfUserLocation = location;
        if(!initialized) {
            moveToCurrent();
            initialized = false;
        }
        double lat = rfUserLocation.getLatitude(); double lon = rfUserLocation.getLongitude();
        rfCurrentMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(lat, lon)).title(this.getString(R.string.youAreHere)));
        if(show) rfCurrentMarker.showInfoWindow();
    }

    /**If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.moveCamera(CameraUpdateFactory.zoomTo(17.0f));
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) { rfSnackbar.dismiss(); }
        });
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                String description = rfMarkerList.get(marker);
                if(description != null) {
                    rfSnackbar.setText(description);
                    rfSnackbar.show();
                } else if (rfSnackbar.isShown()) rfSnackbar.dismiss();
                return false;
            }
        });
        getFountainMarkers();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.goToRome:
                goToRome();
                return true;
            case R.id.moveToCurrent:
                moveToCurrent();
                return true;
            case R.id.about:
                aboutDialog.show();
                return true;
            case R.id.addFountain:
                addFountain();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case LOCATION_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (!initialized) {
                        initialized = true;
                        rfUserLocation = getLastKnownLocation();
                        moveToCurrent();
                    }
                } else { log(this.getString(R.string.permissionDenied)); return; }
            }
            case LOCATION_PERMISSION_SETTINGS_REQUEST: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initialized = true;
                    rfUserLocation = getLastKnownLocation();
                    moveToCurrent();
                }
            }
            case LOCATION_PERMISSION_UPDATES: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                        LocationServices.FusedLocationApi.requestLocationUpdates(rfGAPIClient, rfLocationRequest, this);
            }
        }
    }

    @Override
    protected void onStart() {
        if (isConnected) {
            rfGAPIClient.connect();
        } else {
            log(this.getString(R.string.noConnection));
        }
        super.onStart();
    }

    @Override
    protected void onStop() {
        rfGAPIClient.disconnect();
        super.onStop();
    }

    private void removeCurMarker() {
        rfCurrentMarker.remove();
    }

    private void sendMail() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("*/*");
        String addresses[] = {"mark.sechter@gmail.com"};
        intent.putExtra(Intent.EXTRA_EMAIL, addresses);
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.emailSubject));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    private void setupSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(rfLocationRequest).setAlwaysShow(true);
        PendingResult<LocationSettingsResult> rfLocationSettingsResult = LocationServices.SettingsApi.checkLocationSettings(rfGAPIClient, builder.build());
        rfLocationSettingsResult.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                final Status status = locationSettingsResult.getStatus();
                rfStates = locationSettingsResult.getLocationSettingsStates();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        rfUserLocation = getLastKnownLocation();
                        moveToCurrent();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        try {
                            status.startResolutionForResult(
                                    FountainMap.this,
                                    REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException e) {
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        break;

                }
            }
        });
    }

    protected void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            LocationServices.FusedLocationApi.requestLocationUpdates(rfGAPIClient, rfLocationRequest, this);
        else
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_UPDATES);
    }
}
