package com.frib.mark.romefountains;

import android.*;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AddFountain extends FragmentActivity implements OnMapReadyCallback {

    private AlertDialog confirmPosDialog,
                        requestPhotoDialog,
                        requestDescriptionDialog,
                        getDescriptionDialog,
                        confirmSendDialog;
    private Location rfUserLocation;
    private Marker rfNewMarker;
    private String mCurrentPhotoPath;
    private String rfDescription = "";

    private GoogleMap mMap;
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    private class rfUploadTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) { try { return downloadUrl(urls[0]); } catch (IOException e) { return "Unable to retrieve web page."; } }
        @Override
        protected void onPostExecute(String result) { log(result); }

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
                //int response = conn.getResponseCode();
                is = conn.getInputStream();

                String contentAsString = getPageFromStream(is);
                return contentAsString;
            } finally { if (is != null) { is.close(); } }
        }
        public String getPageFromStream(InputStream s) throws IOException {
            Reader reader;
            reader = new InputStreamReader(s, "UTF-8");
            char[] buffer = new char[1024];
            int len = reader.read(buffer);
            return new String(buffer, 0, len);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_addfountain);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();

        Intent intent = getIntent();
        rfUserLocation = new Location("dummy");
        double lat = Double.parseDouble(intent.getStringExtra(FountainMap.USER_LAT_MSG));
        double lon = Double.parseDouble(intent.getStringExtra(FountainMap.USER_LON_MSG));
        rfUserLocation.setLatitude(lat);
        rfUserLocation.setLongitude(lon);

        TextView instructionsView = (TextView) findViewById(R.id.instructionsView);
        instructionsView.setText(R.string.instructions_setMarker);

        buildDialogs();
    }

    private void buildDialogs() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.confirmPos);
        builder.setNegativeButton(R.string.redo, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) { AddFountain.this.rfNewMarker.remove(); } });
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) { AddFountain.this.requestDescriptionDialog.show(); } });
        confirmPosDialog = builder.create();
        confirmPosDialog.setCancelable(false);
        repositionDialog(confirmPosDialog);

        builder.setMessage(R.string.requestPhoto);
        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) { AddFountain.this.requestDescriptionDialog.show(); } });
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) { AddFountain.this.getPhoto(); } });
        requestPhotoDialog = builder.create();
        requestPhotoDialog.setCancelable(false);
        repositionDialog(requestPhotoDialog);

        builder.setMessage(R.string.requestDescription);
        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) { AddFountain.this.confirmSendDialog.show(); } });
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) { AddFountain.this.getDescriptionDialog.show(); } });
        requestDescriptionDialog = builder.create();
        requestDescriptionDialog.setCancelable(false);
        repositionDialog(requestDescriptionDialog);

        builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        builder.setTitle(R.string.getDescriptionTitle);
        final View descriptionLayout = inflater.inflate(R.layout.description_layout, null);
        builder.setView(descriptionLayout);
        getDescriptionDialog = builder.create();
        getDescriptionDialog.setCancelable(false);
        Button button = (Button) descriptionLayout.findViewById(R.id.description_ok);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText et = (EditText) descriptionLayout.findViewById(R.id.editText);
                rfDescription = et.getText().toString();
                et.setText("");
                getDescriptionDialog.hide();
                confirmSendDialog.show();
            }
        });
        button = (Button) descriptionLayout.findViewById(R.id.description_cancel);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((EditText) descriptionLayout.findViewById(R.id.editText)).setText("");
                getDescriptionDialog.hide();
                confirmSendDialog.show();
            }
        });

        builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.confirmSend);
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) { rfNewMarker.remove(); rfDescription = ""; } });
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) { sendInfo(); } });
        confirmSendDialog = builder.create();
        confirmSendDialog.setCancelable(false);
        repositionDialog(confirmSendDialog);
    }

    private void getPhoto() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(intent, FountainMap.CAMERA_REQUEST);
            }
        } else
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA},
                    FountainMap.CAMERA_PERMISSION);
    }

    private void log(String msg) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); }
    private void moveTo(LatLng point) { mMap.moveCamera(CameraUpdateFactory.newLatLng(point)); }
    private void moveToZoom(LatLng point, float zoom) { mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(point, zoom)); }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FountainMap.CAMERA_REQUEST && resultCode == RESULT_OK) {
            requestDescriptionDialog.show();
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        LatLng userLatLng = new LatLng(rfUserLocation.getLatitude(), rfUserLocation.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 17.0f));

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng point) {
                rfNewMarker = mMap.addMarker(new MarkerOptions().position(point).title(AddFountain.this.getString(R.string.marker_newFountain)));
                rfNewMarker.showInfoWindow();
                moveToZoom(rfNewMarker.getPosition(), 20.0f); //center before dialog box
                confirmPosDialog.show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case FountainMap.CAMERA_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getPhoto();
                } else {
                    requestDescriptionDialog.show();
                }
                return;
            }
        }
    }

            @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "AddFountain Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.frib.mark.romefountains/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "AddFountain Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.frib.mark.romefountains/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }

    private void repositionDialog(AlertDialog dialog) {
        Window window = dialog.getWindow();
        WindowManager.LayoutParams wlp = window.getAttributes();
        wlp.gravity = Gravity.BOTTOM;
        wlp.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        window.setAttributes(wlp);
    }

    private void sendInfo() {
        String url = "http://www.americaninrome.us/rfaddfountain.php?" +
            "lat=" + String.valueOf(rfNewMarker.getPosition().latitude) +
            "&lon=" + String.valueOf(rfNewMarker.getPosition().longitude) +
            "&des=" + rfDescription;
        rfDescription = "";
        new rfUploadTask().execute(url);
        setResult(RESULT_OK);
        finish();
    }
}
